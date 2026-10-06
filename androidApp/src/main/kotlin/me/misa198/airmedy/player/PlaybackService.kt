package me.misa198.airmedy.player

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.graphics.Bitmap
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidMediaPlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.UUID
import android.util.LruCache
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.misa198.airmedy.MainActivity
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.decodeArtworkBitmaps
import me.misa198.airmedy.lastfm.AndroidLastFmRuntime
import me.misa198.airmedy.lastfm.LastFmService
import me.misa198.airmedy.lastfm.LastFmTrack
import me.misa198.airmedy.device.DeviceIdentity
import me.misa198.airmedy.mood.MoodRadioTrack
import me.misa198.airmedy.player.engine.EngineFactory
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.engine.EngineSelectionPreferences
import me.misa198.airmedy.player.engine.LegacyNativeEngine

/** Owns Android transport; queue semantics are delegated to sharedLogic. */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
        Log.e(PlaybackLogTag, "Uncaught playback coroutine exception", throwable)
    })
    private lateinit var restoreJob: Job
    private val queue = PlaybackQueue()
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var sessionStore: PlaybackSessionStore
    private lateinit var playbackPreferences: PlaybackPreferences
    private lateinit var equalizerPreferences: EqualizerPreferences
    private lateinit var normalizationPreferences: NormalizationPreferences
    private lateinit var lastFm: LastFmService
    private lateinit var listeningTracker: ListeningTracker
    private val listeningWrites = Channel<ListeningWrite>(64)
    private lateinit var listeningWriter: Job
    private var preferencesJob: Job? = null
    private var moodRadioJob: Job? = null
    private var equalizerJob: Job? = null
    private var normalizationJob: Job? = null
    private var tickerJob: Job? = null
    @Volatile
    private var inForeground = false
    private var latestStartId = -1
    private var shuttingDown = false
    private var myGeneration = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var focusRequest: AudioFocusRequest
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (audioBecomingNoisyRequiresPause(intent.action)) coordinator.dispatch(ActionPause)
        }
    }

    override fun onCreate() {
        super.onCreate()
        myGeneration = ++instanceGeneration
        val teardownToAwait = previousTeardown
        AndroidPlaybackRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        lastFm = AndroidLastFmRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        listeningTracker = ListeningTracker(DeviceIdentity(applicationContext).id) { UUID.randomUUID().toString() }
        listeningWriter = scope.launch {
            val now = System.currentTimeMillis()
            AndroidSyncRuntime.syncStore().recoverOpenPlaybackAttempts(now)
            AndroidSyncRuntime.syncStore().cleanupListening(now - ListeningRetentionMs)
            for (write in listeningWrites) AndroidSyncRuntime.syncStore().recordListening(write)
        }
        sessionStore = PlaybackSessionStore(applicationContext)
        playbackPreferences = PlaybackPreferences(applicationContext)
        equalizerPreferences = EqualizerPreferences(applicationContext)
        normalizationPreferences = NormalizationPreferences(applicationContext)
        moodRadioJob = scope.launch {
            AndroidSyncRuntime.syncStore().libraryAnalysisEnabled.collectLatest { enabled ->
                if (!enabled) coordinator.withCommandLock { coordinator.stopMoodRadio() }
            }
        }
        audioManager = getSystemService(AudioManager::class.java)
        registerNoisyAudioReceiver()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                when (audioFocusChangeAction(change)) {
                    AudioFocusChangeAction.Pause -> coordinator.dispatch(ActionPause)
                    AudioFocusChangeAction.PauseAndResumeOnGain -> coordinator.dispatch(ActionPauseForTransientFocusLoss)
                    AudioFocusChangeAction.Duck -> coordinator.dispatch(ActionDuck)
                    AudioFocusChangeAction.Restore -> coordinator.dispatch(ActionRestoreFocus)
                    AudioFocusChangeAction.Ignore -> Unit
                }
            }
            .build()
        mediaSession = MediaSession(this, "AirmedyPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { coordinator.dispatch(ActionResume) }
                override fun onPause() { coordinator.dispatch(ActionPause) }
                override fun onSkipToNext() { coordinator.dispatch(ActionNext) }
                override fun onSkipToPrevious() { coordinator.dispatch(ActionPrevious) }
                override fun onSkipToQueueItem(id: Long) {
                    coordinator.selectQueueItem(id)
                }
                override fun onSeekTo(pos: Long) { coordinator.dispatch(ActionSeek, positionMs = pos) }
                override fun onStop() { coordinator.dispatch(ActionStop) }
            })
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            isActive = false
        }
        AndroidPlaybackSession.publish(mediaSession.sessionToken)

        coordinator = PlaybackCoordinator(
            scope = scope,
            queue = queue,
            flows = PlaybackFlows(state, queueState, crossfadeSeconds, blendArtworkDuringCrossfade, artworkCrossfade, moodRadioActive),
            engineFactory = EngineFactory(
                selection = EngineSelectionPreferences(applicationContext).engine,
                builders = mapOf(EngineKind.Native to { LegacyNativeEngine() }),
            )::create,
            resolver = PlaybackItemResolver { id -> AndroidPlaybackRuntime.controller().resolve(id) },
            library = LibraryAdapter(),
            nowPlaying = NowPlayingAdapter(),
            focus = FocusAdapter(),
            listening = ListeningAdapter(),
            scrobble = ScrobbleAdapter(),
            sessionStore = SessionStoreAdapter(),
            clock = ClockAdapter(),
            log = PlaybackLogAdapter(),
            listeningTracker = listeningTracker,
            nextArtworkCrossfadeId = { ++nextArtworkCrossfadeId },
            decodeFailures = DecodeFailureLog.forContext(applicationContext),
        )
        coordinator.onIdle = { if (serviceShouldStopWhenSettled(state.value)) mainHandler.post { settle() } }

        preferencesJob = scope.launch {
            playbackPreferences.settings.collectLatest { settings ->
                coordinator.withCommandLock {
                    coordinator.onPlaybackSettings(settings.seconds, settings.blendArtworkDuringCrossfade)
                }
            }
        }
        equalizerJob = scope.launch {
            equalizerPreferences.settings.collectLatest { settings ->
                coordinator.withCommandLock {
                    coordinator.onEqualizerSettings(settings)
                }
            }
        }
        normalizationJob = scope.launch {
            normalizationPreferences.settings.collectLatest { settings ->
                coordinator.withCommandLock {
                    coordinator.onNormalizationSettings(settings)
                }
            }
        }

        restoreJob = scope.launch {
            try {
                teardownToAwait?.join()
                sessionStore.load()?.let { session ->
                    val availableTrackIds = AndroidPlaybackRuntime.availableTrackIds(session.queue.originalTrackIds)
                    coordinator.withCommandLock {
                        coordinator.restoreSaved(session, availableTrackIds)
                    }
                }
            } catch (error: Throwable) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    Log.w(PlaybackLogTag, "Unable to restore playback session; clearing it", error)
                    coordinator.withCommandLock { coordinator.clearRestoredSession() }
                }
            } finally {
                coordinator.markRestored()
            }
        }
        tickerJob = scope.launch {
            state.map { !serviceShouldStopWhenSettled(it) }.distinctUntilChanged().collectLatest { active ->
                if (active) {
                    while (true) {
                        delay(200)
                        coordinator.withCommandLock {
                            coordinator.tick()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (shuttingDown) return START_NOT_STICKY
        ensureForeground()
        if (playbackActionReplacesRestoredQueue(intent?.action)) {
            // A tap is more important than reconstructing the prior session. In
            // particular, do not delay its Preparing state (and mini-player)
            // behind DataStore I/O and validation of every saved queue entry.
            restoreJob.cancel()
            coordinator.markRestored()
        }
        val command: Job? = when (intent?.action) {
            ActionPlay, ActionShuffle -> coordinator.dispatch(
                action = intent.action!!,
                trackIds = takeQueueToken(intent),
                startIndex = intent.getIntExtra(StartIndexExtra, 0),
            )
            ActionSeek -> coordinator.dispatch(ActionSeek, positionMs = intent.getLongExtra(PositionMsExtra, 0L))
            ActionSetShuffle -> coordinator.dispatch(ActionSetShuffle, enabled = intent.getBooleanExtra(EnabledExtra, false))
            ActionSetRepeat -> coordinator.dispatch(
                ActionSetRepeat,
                repeat = intent.getStringExtra(RepeatModeExtra)?.let { value -> runCatching { RepeatMode.valueOf(value) }.getOrNull() },
            )
            ActionSetCrossfade -> {
                scope.launch {
                    playbackPreferences.setCrossfadeSeconds(
                        intent.getIntExtra(CrossfadeSecondsExtra, CrossfadeDisabledSeconds),
                    )
                }
                null
            }
            ActionPlayNext, ActionAppend, ActionReorder -> coordinator.dispatch(
                action = intent.action!!,
                trackIds = takeQueueToken(intent),
            )
            ActionStartMoodRadio -> coordinator.dispatch(ActionStartMoodRadio, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            ActionSelect -> coordinator.dispatch(ActionSelect, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            ActionRemove -> coordinator.dispatch(ActionRemove, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            null -> null
            else -> coordinator.dispatch(intent.action!!)
        }
        if (command == null) settle()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shuttingDown = true
        // Read before cancelling: the restore job's finally marks the coordinator restored even when cancelled.
        val restoredBeforeDestroy = coordinator.isRestored()
        restoreJob.cancel()
        tickerJob?.cancel()
        preferencesJob?.cancel()
        equalizerJob?.cancel()
        normalizationJob?.cancel()
        moodRadioJob?.cancel()
        unregisterReceiver(noisyAudioReceiver)
        audioManager.abandonAudioFocusRequest(focusRequest)
        val teardown = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { coordinator.shutdown() }
                .onFailure { Log.w(PlaybackLogTag, "Playback coordinator shutdown failed", it) }
            runCatching { coordinator.finishListeningCrossfade() }
                .onFailure { Log.w(PlaybackLogTag, "Listening crossfade finish failed", it) }
            runCatching {
                coordinator.enqueueListening(
                    coordinator.listeningTracker.finish(PlaybackEndReason.STOPPED, System.currentTimeMillis(), SystemClock.elapsedRealtime()),
                )
            }.onFailure { Log.w(PlaybackLogTag, "Listening finish failed", it) }
            if (restoredBeforeDestroy) {
                runCatching { coordinator.saveSessionNow() }
                    .onFailure { Log.w(PlaybackLogTag, "Session save failed", it) }
            }
            runCatching { coordinator.closeEngine() }
                .onFailure { Log.w(PlaybackLogTag, "Engine close failed", it) }
            runCatching { listeningWrites.close() }
                .onFailure { Log.w(PlaybackLogTag, "Listening channel close failed", it) }
            runCatching { withTimeoutOrNull(2_000) { listeningWriter.join() } }
                .onFailure { Log.w(PlaybackLogTag, "Listening writer join failed", it) }
            withContext(Dispatchers.Main) {
                if (instanceGeneration == myGeneration) {
                    AndroidPlaybackSession.clear(mediaSession.sessionToken)
                    coordinator.clearArtworkCrossfade()
                    if (state.value !is PlaybackState.Failed) state.value = PlaybackState.Idle
                }
                mediaSession.release()
            }
            scope.cancel()
        }
        previousTeardown = teardown
        super.onDestroy()
    }

    /** Reaches foreground for every command; the service cannot tell whether startForegroundService was used. */
    private fun ensureForeground() {
        if (inForeground) return
        try {
            createChannel()
            startForeground(NotificationId, placeholderNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            inForeground = true
        } catch (error: ForegroundServiceStartNotAllowedException) {
            Log.w(PlaybackLogTag, "Unable to start the playback foreground service", error)
        }
    }

    private fun placeholderNotification(): Notification {
        val item = when (val current = state.value) {
            is PlaybackState.Playing -> current.item
            is PlaybackState.Paused -> current.item
            is PlaybackState.Preparing -> current.item
            else -> null
        }
        if (item != null) return notification(item)
        return Notification.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(nowPlayingContentIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .build()
    }

    /** Main thread only. Stops the service once playback has settled and no command is queued or running. */
    private fun settle() {
        if (!shuttingDown && serviceShouldStopWhenSettled(state.value) && coordinator.isIdle()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
            stopSelf(latestStartId)
        }
    }

    private fun registerNoisyAudioReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyAudioReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyAudioReceiver, filter)
        }
    }

    private fun takeQueueToken(intent: Intent): List<String> {
        val trackIds = QueueHandoff.shared.take(intent.getStringExtra(QueueTokenExtra))
        if (trackIds == null) {
            Log.w(PlaybackLogTag, "Queue handoff token missing, unknown or expired action=${intent.action}")
        }
        return trackIds.orEmpty()
    }

    private fun transportStateToAndroid(state: TransportState): Int = when (state) {
        TransportState.None -> AndroidMediaPlaybackState.STATE_NONE
        TransportState.Buffering -> AndroidMediaPlaybackState.STATE_BUFFERING
        TransportState.Playing -> AndroidMediaPlaybackState.STATE_PLAYING
        TransportState.Paused -> AndroidMediaPlaybackState.STATE_PAUSED
        TransportState.Stopped -> AndroidMediaPlaybackState.STATE_STOPPED
        TransportState.Error -> AndroidMediaPlaybackState.STATE_ERROR
    }

    private inner class NowPlayingAdapter : NowPlayingPort {
        override fun publishNowPlaying(item: PlaybackItem, state: TransportState, positionMs: Long, durationMs: Long, activeQueueItemId: Long) {
            mediaSession.isActive = true
            val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, item.trackId)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            loadNowPlayingArtwork(item)?.let { artwork ->
                metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
                metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)
            }
            mediaSession.setMetadata(metadata.build())
            mediaSession.setPlaybackState(androidPlaybackState(transportStateToAndroid(state), positionMs, activeQueueItemId))
            Log.d(PlaybackLogTag, "Published Android Now Playing id=${item.trackId} state=$state")
        }

        override fun setTransportState(state: TransportState, positionMs: Long, activeQueueItemId: Long) {
            mediaSession.setPlaybackState(androidPlaybackState(transportStateToAndroid(state), positionMs, activeQueueItemId))
        }

        override fun deactivate() {
            mediaSession.isActive = false
        }

        override suspend fun publishQueue(snapshot: PlaybackQueueSnapshot, window: List<QueueWindowEntry>) {
            mediaSession.setQueue(window.map { entry ->
                MediaSession.QueueItem(
                    MediaDescription.Builder()
                        .setMediaId(entry.item.trackId)
                        .setTitle(entry.item.title)
                        .setSubtitle(entry.item.artist)
                        .setDescription(entry.item.album)
                        .build(),
                    entry.index.toLong(),
                )
            })
        }

        override fun showForeground(item: PlaybackItem) {
            createChannel()
            startForeground(NotificationId, notification(item), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            inForeground = true
        }

        override fun updateNotification(item: PlaybackItem) {
            getSystemService(NotificationManager::class.java).notify(NotificationId, notification(item))
        }

        override fun stopForeground() {
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
    }

    private inner class FocusAdapter : FocusPort {
        override fun request(): Boolean = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        override fun abandon() { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    private inner class ListeningAdapter : ListeningSink {
        override fun offer(write: ListeningWrite): Boolean = listeningWrites.trySend(write).isSuccess
    }

    private inner class ScrobbleAdapter : ScrobbleSink {
        override fun startPlayback(trackId: String, positionMs: Long) = lastFm.startPlayback(trackId, positionMs)
        override fun seek(positionMs: Long) = lastFm.seek(positionMs)
        override fun reportPlayback(item: PlaybackItem, positionMs: Long, durationMs: Long) = lastFm.reportPlayback(item.toLastFmTrack(), positionMs, durationMs)
    }

    private inner class SessionStoreAdapter : SessionStorePort {
        override suspend fun load(): PlaybackSession? = sessionStore.load()
        override suspend fun save(session: PlaybackSession) = sessionStore.save(session)
        override suspend fun clear() = sessionStore.clear()
    }

    private inner class ClockAdapter : Clock {
        override fun nowMs(): Long = System.currentTimeMillis()
        override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
    }

    private inner class PlaybackLogAdapter : PlaybackLog {
        override fun d(message: String) { Log.d(PlaybackLogTag, message) }
        override fun w(message: String, error: Throwable?) { Log.w(PlaybackLogTag, message, error) }
        override fun e(message: String) { Log.e(PlaybackLogTag, message) }
    }

    private inner class LibraryAdapter : LibraryPort {
        override suspend fun activeAnalyses(): Map<String, TrackAnalysis> = AndroidSyncRuntime.syncStore().activeAnalyses()
        override suspend fun tracks(): List<LibraryTrack> = AndroidSyncRuntime.syncStore().tracks.first()
        override suspend fun disableNormalization() = normalizationPreferences.disable()
        override suspend fun libraryAnalysisEnabled(): Boolean = AndroidSyncRuntime.syncStore().libraryAnalysisEnabled.first()
        override suspend fun moodRadioTracks(): List<MoodRadioTrack> = AndroidSyncRuntime.syncStore().moodRadioTracks()
    }

    private fun PlaybackItem.toLastFmTrack() = LastFmTrack(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
    )

    private fun androidPlaybackState(state: Int, positionMs: Long, activeQueueItemId: Long): AndroidMediaPlaybackState =
        AndroidMediaPlaybackState.Builder()
            .setActions(
                AndroidMediaPlaybackState.ACTION_PLAY or
                    AndroidMediaPlaybackState.ACTION_PAUSE or
                    AndroidMediaPlaybackState.ACTION_PLAY_PAUSE or
                    AndroidMediaPlaybackState.ACTION_SKIP_TO_NEXT or
                    AndroidMediaPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    AndroidMediaPlaybackState.ACTION_SEEK_TO or
                    AndroidMediaPlaybackState.ACTION_STOP,
            )
            .setActiveQueueItemId(activeQueueItemId)
            .setState(state, positionMs, if (state == AndroidMediaPlaybackState.STATE_PLAYING) 1f else 0f)
            .build()

    /** Same resolution as the in-app UI ([decodeArtworkBitmaps]): album artwork file, then embedded picture. */
    private fun loadNowPlayingArtwork(item: PlaybackItem): Bitmap? {
        val cacheKey = item.artworkPath ?: item.audioPath
        nowPlayingArtworkCache.get(cacheKey)?.let { return it }
        val artwork = decodeArtworkBitmaps(item.artworkPath, item.audioPath, NowPlayingArtworkSizePx, Bitmap.Config.ARGB_8888)
        if (artwork != null) nowPlayingArtworkCache.put(cacheKey, artwork)
        else Log.w(PlaybackLogTag, "Unable to decode Now Playing artwork artworkPath=${item.artworkPath} audioPath=${item.audioPath}")
        return artwork
    }

    private fun notification(item: PlaybackItem): Notification = Notification.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(item.title)
        .setContentText(item.artist)
        .setContentIntent(nowPlayingContentIntent())
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
        .build()

    private fun nowPlayingContentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, getString(R.string.playback_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        @Volatile
        private var instanceGeneration = 0
        @Volatile
        private var previousTeardown: Job? = null

        internal const val ActionPlay = "me.misa198.airmedy.player.PLAY"
        internal const val ActionShuffle = "me.misa198.airmedy.player.SHUFFLE"
        internal const val ActionPause = "me.misa198.airmedy.player.PAUSE"
        internal const val ActionPauseForTransientFocusLoss = "me.misa198.airmedy.player.PAUSE_FOR_TRANSIENT_FOCUS_LOSS"
        internal const val ActionDuck = "me.misa198.airmedy.player.DUCK"
        internal const val ActionRestoreFocus = "me.misa198.airmedy.player.RESTORE_FOCUS"
        internal const val ActionResume = "me.misa198.airmedy.player.RESUME"
        internal const val ActionStop = "me.misa198.airmedy.player.STOP"
        internal const val ActionClearQueue = "me.misa198.airmedy.player.CLEAR_QUEUE"
        internal const val ActionNext = "me.misa198.airmedy.player.NEXT"
        internal const val ActionPrevious = "me.misa198.airmedy.player.PREVIOUS"
        internal const val ActionSeek = "me.misa198.airmedy.player.SEEK"
        internal const val ActionSetShuffle = "me.misa198.airmedy.player.SET_SHUFFLE"
        internal const val ActionSetRepeat = "me.misa198.airmedy.player.SET_REPEAT"
        internal const val ActionPlayNext = "me.misa198.airmedy.player.PLAY_NEXT"
        internal const val ActionAppend = "me.misa198.airmedy.player.APPEND"
        internal const val ActionSelect = "me.misa198.airmedy.player.SELECT"
        internal const val ActionRemove = "me.misa198.airmedy.player.REMOVE"
        internal const val ActionReorder = "me.misa198.airmedy.player.REORDER"
        internal const val ActionStartMoodRadio = "me.misa198.airmedy.player.START_MOOD_RADIO"
        internal const val QueueTokenExtra = "queue_token"
        internal const val TrackIdExtra = "track_id"
        internal const val StartIndexExtra = "start_index"
        internal const val PositionMsExtra = "position_ms"
        internal const val EnabledExtra = "enabled"
        internal const val RepeatModeExtra = "repeat_mode"
        private const val ChannelId = "playback"
        private const val NotificationId = 2002
        private const val NowPlayingArtworkSizePx = 512
        private const val ListeningRetentionMs = 180L * 24 * 60 * 60 * 1_000
        private val nowPlayingArtworkCache = LruCache<String, Bitmap>(20)
        internal val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        internal val queueState = MutableStateFlow(PlaybackQueueSnapshot())
        internal val crossfadeSeconds = MutableStateFlow(CrossfadeDisabledSeconds)
        internal val blendArtworkDuringCrossfade = MutableStateFlow(true)
        internal val artworkCrossfade = MutableStateFlow<ArtworkCrossfadeTransition?>(null)
        internal val moodRadioActive = MutableStateFlow(false)
        private var nextArtworkCrossfadeId = 0L
        internal const val ActionSetCrossfade = "me.misa198.airmedy.player.SET_CROSSFADE"
        internal const val CrossfadeSecondsExtra = "crossfade_seconds"
        internal fun intent(context: Context, action: String) = Intent(context, PlaybackService::class.java).setAction(action)
    }
}
