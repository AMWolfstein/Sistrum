package me.misa198.airmedy.player

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
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.util.UUID
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.misa198.airmedy.MainActivity
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.decodeArtworkBitmaps
import me.misa198.airmedy.lastfm.AndroidLastFmRuntime
import me.misa198.airmedy.lastfm.LastFmService
import me.misa198.airmedy.lastfm.LastFmTrack
import me.misa198.airmedy.device.DeviceIdentity
import me.misa198.airmedy.mood.MoodRadioBatchSize
import me.misa198.airmedy.mood.MoodRadioRefillThreshold
import me.misa198.airmedy.mood.selectMoodRadio
import me.misa198.airmedy.player.engine.EngineFactory

/** Owns Android transport; queue semantics are delegated to sharedLogic. */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val restored = CompletableDeferred<Unit>()
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
    private var moodRadioSeedId: String? = null
    private var moodRadioLastRefillAttempt: Pair<String?, Int>? = null
    private var resumeOnFocusGain = false
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var focusRequest: AudioFocusRequest
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (audioBecomingNoisyRequiresPause(intent.action)) dispatch(ActionPause)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidPlaybackRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        lastFm = AndroidLastFmRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        listeningTracker = ListeningTracker(DeviceIdentity(applicationContext).id) { UUID.randomUUID().toString() }
        listeningWriter = scope.launch {
            for (write in listeningWrites) AndroidSyncRuntime.syncStore().recordListening(write)
        }
        runBlocking {
            val now = System.currentTimeMillis()
            AndroidSyncRuntime.syncStore().recoverOpenPlaybackAttempts(now)
            AndroidSyncRuntime.syncStore().cleanupListening(now - ListeningRetentionMs)
        }
        sessionStore = PlaybackSessionStore(applicationContext)
        playbackPreferences = PlaybackPreferences(applicationContext)
        equalizerPreferences = EqualizerPreferences(applicationContext)
        normalizationPreferences = NormalizationPreferences(applicationContext)
        moodRadioJob = scope.launch {
            AndroidSyncRuntime.syncStore().libraryAnalysisEnabled.collectLatest { enabled ->
                if (!enabled) commandMutex.withLock { stopMoodRadio() }
            }
        }
        audioManager = getSystemService(AudioManager::class.java)
        registerNoisyAudioReceiver()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                when (audioFocusChangeAction(change)) {
                    AudioFocusChangeAction.Pause -> dispatch(ActionPause)
                    AudioFocusChangeAction.PauseAndResumeOnGain -> dispatch(ActionPauseForTransientFocusLoss)
                    AudioFocusChangeAction.Duck -> dispatch(ActionDuck)
                    AudioFocusChangeAction.Restore -> dispatch(ActionRestoreFocus)
                    AudioFocusChangeAction.Ignore -> Unit
                }
            }
            .build()
        mediaSession = MediaSession(this, "AirmedyPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { dispatch(ActionResume) }
                override fun onPause() { dispatch(ActionPause) }
                override fun onSkipToNext() { dispatch(ActionNext) }
                override fun onSkipToPrevious() { dispatch(ActionPrevious) }
                override fun onSkipToQueueItem(id: Long) {
                    coordinator.queue.snapshot().activeTrackIds.getOrNull(id.toInt())?.let { dispatch(ActionSelect, trackIds = listOf(it)) }
                }
                override fun onSeekTo(pos: Long) { dispatch(ActionSeek, positionMs = pos) }
                override fun onStop() { dispatch(ActionStop) }
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
            engineFactory = EngineFactory::create,
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
        )

        preferencesJob = scope.launch {
            playbackPreferences.settings.collectLatest { settings ->
                commandMutex.withLock {
                    coordinator.onPlaybackSettings(settings.seconds, settings.blendArtworkDuringCrossfade)
                }
            }
        }
        scope.launch {
            equalizerPreferences.settings.collectLatest { settings ->
                commandMutex.withLock {
                    coordinator.onEqualizerSettings(settings)
                }
            }
        }
        scope.launch {
            normalizationPreferences.settings.collectLatest { settings ->
                commandMutex.withLock {
                    coordinator.onNormalizationSettings(settings)
                }
            }
        }

        restoreJob = scope.launch {
            try {
                sessionStore.load()?.let { session ->
                    val availableTrackIds = AndroidPlaybackRuntime.availableTrackIds(session.queue.originalTrackIds)
                    commandMutex.withLock {
                        coordinator.restoreSaved(session, availableTrackIds)
                    }
                }
            } catch (error: Throwable) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    Log.w(PlaybackLogTag, "Unable to restore playback session; clearing it", error)
                    commandMutex.withLock { coordinator.clearRestoredSession() }
                }
            } finally {
                restored.complete(Unit)
            }
        }
        scope.launch {
            while (true) {
                delay(200)
                commandMutex.withLock {
                    coordinator.tick { refillMoodRadioIfNeeded() }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (playbackActionReplacesRestoredQueue(intent?.action)) {
            // A tap is more important than reconstructing the prior session. In
            // particular, do not delay its Preparing state (and mini-player)
            // behind DataStore I/O and validation of every saved queue entry.
            restoreJob.cancel()
            restored.complete(Unit)
        }
        when (intent?.action) {
            ActionPlay, ActionShuffle -> dispatch(
                action = intent.action!!,
                trackIds = intent.getStringArrayExtra(TrackIdsExtra).orEmpty().toList(),
                startIndex = intent.getIntExtra(StartIndexExtra, 0),
            )
            ActionSeek -> dispatch(ActionSeek, positionMs = intent.getLongExtra(PositionMsExtra, 0L))
            ActionSetShuffle -> dispatch(ActionSetShuffle, enabled = intent.getBooleanExtra(EnabledExtra, false))
            ActionSetRepeat -> dispatch(
                ActionSetRepeat,
                repeat = intent.getStringExtra(RepeatModeExtra)?.let { value -> runCatching { RepeatMode.valueOf(value) }.getOrNull() },
            )
            ActionSetCrossfade -> scope.launch {
                playbackPreferences.setCrossfadeSeconds(
                    intent.getIntExtra(CrossfadeSecondsExtra, CrossfadeDisabledSeconds),
                )
            }
            ActionPlayNext, ActionAppend, ActionReorder -> dispatch(
                action = intent.action!!,
                trackIds = intent.getStringArrayExtra(TrackIdsExtra).orEmpty().toList(),
            )
            ActionStartMoodRadio -> dispatch(ActionStartMoodRadio, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            ActionSelect -> dispatch(ActionSelect, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            ActionRemove -> dispatch(ActionRemove, trackIds = listOfNotNull(intent.getStringExtra(TrackIdExtra)))
            null -> Unit
            else -> dispatch(intent.action!!)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runBlocking {
            coordinator.finishListeningCrossfade()
            coordinator.enqueueListening(coordinator.listeningTracker.finish(PlaybackEndReason.STOPPED, System.currentTimeMillis(), SystemClock.elapsedRealtime()))
            listeningWrites.close()
            withTimeoutOrNull(2_000) { listeningWriter.join() }
        }
        runBlocking { sessionStore.save(coordinator.currentSession()) }
        coordinator.closeEngine()
        preferencesJob?.cancel()
        moodRadioJob?.cancel()
        unregisterReceiver(noisyAudioReceiver)
        AndroidPlaybackSession.clear()
        mediaSession.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        scope.cancel()
        coordinator.clearArtworkCrossfade()
        state.value = PlaybackState.Idle
        super.onDestroy()
    }

    private fun dispatch(
        action: String,
        positionMs: Long = 0L,
        trackIds: List<String> = emptyList(),
        startIndex: Int = 0,
        enabled: Boolean = false,
        repeat: RepeatMode? = null,
    ) = scope.launch {
        restored.await()
        commandMutex.withLock {
            coordinator.pollEngineEvents()
            Log.d(PlaybackLogTag, "Handling action=$action queueSize=${coordinator.queue.snapshot().activeTrackIds.size}")
            if (action in MoodRadioStoppingActions) stopMoodRadio()
            when (action) {
                ActionPlay -> coordinator.handleTransition(runCatching { coordinator.queue.play(PlaybackRequest(trackIds, startIndex)) }
                    .getOrElse { QueueTransition.Stop }, PlaybackEndReason.SKIPPED)
                ActionShuffle -> coordinator.handleTransition(runCatching { coordinator.queue.playShuffled(PlaybackRequest(trackIds, startIndex)) }
                    .getOrElse { QueueTransition.Stop }, PlaybackEndReason.SKIPPED)
                ActionPause -> {
                    resumeOnFocusGain = false
                    coordinator.restoreFocusGain()
                    coordinator.pauseCurrent()
                }
                ActionPauseForTransientFocusLoss -> pauseForTransientFocusLoss()
                ActionDuck -> coordinator.duckForFocusLoss()
                ActionRestoreFocus -> restoreAfterFocusGain()
                ActionResume -> {
                    resumeOnFocusGain = false
                    coordinator.resumeCurrent()
                }
                ActionStop -> coordinator.stopPlayback()
                ActionClearQueue -> coordinator.handleTransition(coordinator.queue.clear())
                ActionNext -> coordinator.handleTransition(coordinator.queue.next(), PlaybackEndReason.SKIPPED, preservePlaybackState = true)
                ActionPrevious -> {
                    if (coordinator.positionMs() > PreviousRestartThresholdMs) coordinator.seekCurrent(0L)
                    else coordinator.handleTransition(coordinator.queue.previous(), PlaybackEndReason.SKIPPED, preservePlaybackState = true)
                }
                ActionSeek -> coordinator.seekCurrent(positionMs)
                ActionSetShuffle -> coordinator.handleTransition(coordinator.queue.setShuffle(enabled))
                ActionSetRepeat -> repeat?.let(coordinator.queue::setRepeatMode)
                ActionPlayNext -> coordinator.queue.playNext(trackIds)
                ActionAppend -> coordinator.queue.append(trackIds)
                ActionStartMoodRadio -> trackIds.firstOrNull()?.let { startMoodRadio(it) }
                ActionSelect -> trackIds.firstOrNull()?.let { coordinator.handleTransition(coordinator.queue.select(it), PlaybackEndReason.SKIPPED) }
                ActionRemove -> trackIds.firstOrNull()?.let { coordinator.handleTransition(coordinator.queue.removeFromQueue(it), PlaybackEndReason.SKIPPED) }
                ActionReorder -> coordinator.queue.reorderQueue(trackIds)
            }
            if (action in PreloadResyncActions) {
                // The old source must not remain part of a fade whose queued
                // successor has just changed.
                if (coordinator.isCrossfading()) {
                    coordinator.clearArtworkCrossfade()
                    coordinator.snapCrossfade()
                }
                coordinator.preloadNext()
            }
            coordinator.publishQueue()
        }
    }

    private suspend fun startMoodRadio(seedId: String) {
        val store = AndroidSyncRuntime.syncStore()
        if (!store.libraryAnalysisEnabled.first()) {
            Log.d(PlaybackLogTag, "Mood Radio ignored: library analysis is disabled")
            return
        }
        val tracks = store.moodRadioTracks()
        val selected = selectMoodRadio(seedId, tracks, emptySet(), MoodRadioBatchSize)
        if (selected.isEmpty()) {
            Log.d(PlaybackLogTag, "Mood Radio has no candidates seed=$seedId analyzed=${tracks.count { it.energy != null && it.danceability != null && it.brightness != null && it.tempo != null }}")
            return
        }
        moodRadioSeedId = seedId
        moodRadioLastRefillAttempt = null
        moodRadioActive.value = true
        if (coordinator.queue.snapshot().currentTrackId == seedId) coordinator.queue.replaceKeepingCurrent(listOf(seedId) + selected.map { it.id })
        else coordinator.handleTransition(coordinator.queue.play(PlaybackRequest(listOf(seedId) + selected.map { it.id })), PlaybackEndReason.SKIPPED)
        Log.d(PlaybackLogTag, "Mood Radio started seed=$seedId added=${selected.size} queueSize=${coordinator.queue.snapshot().activeTrackIds.size}")
    }

    private suspend fun refillMoodRadioIfNeeded(): Boolean {
        val seedId = moodRadioSeedId ?: return false
        val snapshot = coordinator.queue.snapshot()
        if (snapshot.activeTrackIds.size - snapshot.currentIndex - 1 >= MoodRadioRefillThreshold) return false
        val attempt = snapshot.currentTrackId to snapshot.activeTrackIds.size
        if (attempt == moodRadioLastRefillAttempt) return false
        moodRadioLastRefillAttempt = attempt
        val store = AndroidSyncRuntime.syncStore()
        if (!store.libraryAnalysisEnabled.first()) return false
        val selected = selectMoodRadio(seedId, store.moodRadioTracks(), snapshot.activeTrackIds.toSet(), MoodRadioBatchSize)
        if (selected.isEmpty()) return false
        coordinator.queue.append(selected.map { it.id })
        Log.d(PlaybackLogTag, "Mood Radio refilled seed=$seedId added=${selected.size} queueSize=${coordinator.queue.snapshot().activeTrackIds.size}")
        return true
    }

    private fun stopMoodRadio() { moodRadioSeedId = null; moodRadioLastRefillAttempt = null; moodRadioActive.value = false }

    private fun pauseForTransientFocusLoss() {
        resumeOnFocusGain = state.value is PlaybackState.Playing
        coordinator.restoreFocusGain()
        coordinator.pauseCurrent()
    }

    private suspend fun restoreAfterFocusGain() {
        coordinator.restoreFocusGain()
        if (!resumeOnFocusGain) return
        resumeOnFocusGain = false
        coordinator.resumeCurrent()
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

        override suspend fun publishQueue(snapshot: PlaybackQueueSnapshot) {
            val tracks = AndroidSyncRuntime.syncStore().tracks.first().associateBy { it.id }
            mediaSession.setQueue(snapshot.activeTrackIds.mapIndexedNotNull { index, trackId ->
                tracks[trackId]?.let { track ->
                    MediaSession.QueueItem(
                        MediaDescription.Builder()
                            .setMediaId(track.id)
                            .setTitle(track.title)
                            .setSubtitle(track.artists)
                            .setDescription(track.album)
                            .build(),
                        index.toLong(),
                    )
                }
            })
        }

        override fun showForeground(item: PlaybackItem) {
            createChannel()
            startForeground(NotificationId, notification(item), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }

        override fun updateNotification(item: PlaybackItem) {
            getSystemService(NotificationManager::class.java).notify(NotificationId, notification(item))
        }

        override fun stopForeground() {
            stopForeground(STOP_FOREGROUND_REMOVE)
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
        internal const val ActionPlay = "me.misa198.airmedy.player.PLAY"
        internal const val ActionShuffle = "me.misa198.airmedy.player.SHUFFLE"
        internal const val ActionPause = "me.misa198.airmedy.player.PAUSE"
        private const val ActionPauseForTransientFocusLoss = "me.misa198.airmedy.player.PAUSE_FOR_TRANSIENT_FOCUS_LOSS"
        private const val ActionDuck = "me.misa198.airmedy.player.DUCK"
        private const val ActionRestoreFocus = "me.misa198.airmedy.player.RESTORE_FOCUS"
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
        internal const val TrackIdsExtra = "track_ids"
        internal const val TrackIdExtra = "track_id"
        internal const val StartIndexExtra = "start_index"
        internal const val PositionMsExtra = "position_ms"
        internal const val EnabledExtra = "enabled"
        internal const val RepeatModeExtra = "repeat_mode"
        private const val PreviousRestartThresholdMs = 3_000L
        private const val ChannelId = "playback"
        private const val NotificationId = 2002
        private const val NowPlayingArtworkSizePx = 512
        private const val ListeningRetentionMs = 180L * 24 * 60 * 60 * 1_000
        private val PreloadResyncActions = setOf(
            ActionSetShuffle, ActionSetRepeat, ActionPlayNext, ActionRemove, ActionReorder, ActionStartMoodRadio,
        )
        private val MoodRadioStoppingActions = setOf(
            ActionPlay, ActionShuffle, ActionStop, ActionClearQueue, ActionPlayNext, ActionAppend, ActionRemove, ActionReorder,
        )
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
