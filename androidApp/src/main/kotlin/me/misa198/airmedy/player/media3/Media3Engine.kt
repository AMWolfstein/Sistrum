package me.misa198.airmedy.player.media3

import androidx.annotation.OptIn
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.MediaSource
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.equalizerDspConfig
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.engine.ItemGain
import me.misa198.airmedy.player.engine.PlayerEngine
import me.misa198.airmedy.player.dsp.GainProcessor
import me.misa198.airmedy.player.dsp.FadeDirection
import me.misa198.airmedy.player.dsp.fadeAudibleEndMs
import me.misa198.airmedy.player.normalization.AlbumLoudnessLookup
import me.misa198.airmedy.player.normalization.GainForm
import me.misa198.airmedy.player.normalization.GainInfo
import me.misa198.airmedy.player.normalization.GainResolver
import me.misa198.airmedy.player.normalization.MeasuredGainSource
import me.misa198.airmedy.player.normalization.TagGainSource

/**
 * Media3 engine with a gapless playlist or a controller-driven dual-player crossfade. Every seam call marshals through
 * [Media3PlayerFactory.call] onto the process-wide playback looper; player listeners run
 * on that looper and append to a thread-safe buffer that [pollEvents] drains, so
 * [events] stays empty. [EngineEvent.OutputStarted] is emitted once on the first advance
 * of the rendered position (FR-090); [EngineEvent.Ended] and [EngineEvent.Error] are
 * edge events from the listener. A failed [prepare] throws after releasing the player
 * it created (FR-086).
 */
@OptIn(UnstableApi::class)
internal class Media3Engine(
    private val factory: Media3PlayerFactory,
    private val gainResolverProvider: suspend () -> GainResolver = {
        GainResolver(MeasuredGainSource(AlbumLoudnessLookup { null }), TagGainSource())
    },
) : PlayerEngine {

    override val kind: EngineKind = EngineKind.Media3

    override val events: Flow<EngineEvent> = emptyFlow()

    /** Each listener owns its source; the outgoing listener must never advance the queue. */
    private inner class Slot(val p: ExoPlayer, var item: PlaybackItem, val gain: PlayerStreamGain) {
        var knownDurationMs = 0L
        // Survives the fade-start re-prepare, which clears the player's format until it is ready again.
        // Keyed to its track: after a gapless advance the old track's format must not name the new one's failure.
        var knownMime: Pair<String, String>? = null
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && p.duration != C.TIME_UNSET) knownDurationMs = p.duration
                if (playbackState == Player.STATE_READY) {
                    p.audioFormat?.sampleMimeType?.let { knownMime = item.trackId to it }
                }
                if (this@Slot === incomingSlot && playbackState == Player.STATE_READY) checkIncomingReady()
                if (p === player && playbackState == Player.STATE_ENDED && endedArmed) {
                    endedArmed = false
                    pendingEvents += EngineEvent.Ended
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (p !== player || reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                val incoming = mediaItem?.mediaId?.let { preloadedByMediaId[it] }
                if (incoming != null) {
                    item = incoming
                    gain.item = incoming
                    // The processor clock keeps counting across a gapless advance (no flush): rebase it onto the new
                    // item by the finished item's duration, so a fade right after the advance measures a true lead.
                    val previous = p.currentMediaItemIndex - 1
                    val previousMs = if (previous >= 0 && previous < p.currentTimeline.windowCount) {
                        p.currentTimeline.getWindow(previous, Timeline.Window()).durationMs
                    } else C.TIME_UNSET
                    gain.positionOriginMs = if (previousMs != C.TIME_UNSET) gain.positionOriginMs - previousMs else 0L
                    currentItem = incoming
                    pendingNext = null
                    pendingEvents += EngineEvent.GaplessAdvanced(incoming)
                }
                endedArmed = true
                resetPosition(p.currentPosition)
                val index = p.currentMediaItemIndex
                if (index > 0) p.removeMediaItems(0, index)
                preloadedByMediaId.clear()
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(this@Slot, error)
            }
        }
    }

    private var currentSlot: Slot? = null
    private var incomingSlot: Slot? = null
    private var outgoingSlot: Slot? = null
    private var pendingNext: PlaybackItem? = null
    private var transitionSettings = TransitionSettings(0L)
    private var transition = TransitionController(transitionSettings)
    private var transitionHandler: Handler? = null
    private var transitionStepper: Runnable? = null
    private var incomingDeadlineMs = 0L
    private var incomingVerified = false
    private var sessionDeadlineMs = 0L
    // Output start latency (play call → the audible clock leaves its start position), measured on every start of
    // a player and used to time the incoming player's play call against the outgoing lead.
    private var startLatencyMs = DEFAULT_START_LATENCY_MS
    private var startProbePlayer: ExoPlayer? = null
    private var startProbeBaseMs = 0L
    private var startProbeAtMs = 0L
    private var startProbeSumMs = 0L
    private var startProbeSamples = 0
    private var incomingStart: Runnable? = null
    private var fadeNotBeforeMs = 0L
    private var fadeDrainDeadlineMs = 0L
    private var fadeLeadFrames = 0L
    private var fadeSampleRate = 0
    internal var outgoingLeadMsForTest = 0L
        private set
    internal var incomingDelayMsForTest = 0L
        private set

    private var player: ExoPlayer? = null
    @Volatile
    private var currentItem: PlaybackItem? = null
    private var prepared = false
    private var awaitingOutput = false
    private var outputBaseMs = 0L
    private var endedArmed = false
    private var rawMs = 0L
    private var rawAtMs = 0L
    private var lastReturnedMs = 0L
    private val preloadedByMediaId = ConcurrentHashMap<String, PlaybackItem>()
    @Volatile
    private var closed = false
    private val pendingEvents = ConcurrentLinkedQueue<EngineEvent>()
    private val focusRamp = FocusVolumeRamp()
    private var focusHandler: Handler? = null
    private var focusStepper: Runnable? = null
    private var normalizationSettings = NormalizationSettings()
    private var gainResolver: GainResolver? = null
    // Registered and retargeted on the application playback looper; hook state has its own lock.
    private var streamGain: PlayerStreamGain? = null

    /** Each player's renderer stream and setting changes publish targets under the same lock. */
    private inner class PlayerStreamGain(
        private val resolver: GainResolver,
        settings: NormalizationSettings,
        @Volatile var item: PlaybackItem,
        var positionOriginMs: Long = 0L,
    ) : StreamGainHook, AnalyticsListener {
        private val control = StreamGainControl(settings)
        @Volatile
        var processor: GainProcessor? = null
            private set
        private var fadeAfterFlushMs: Long? = null
        private var loggedDecoderPeriod: MediaSource.MediaPeriodId? = null
        private var decoderLogged = false

        @Synchronized
        override fun onGainProcessor(processor: GainProcessor) {
            this.processor = processor
        }

        @Synchronized
        fun cancelFadeAfterFlush() { fadeAfterFlushMs = null }

        @Synchronized
        fun fadeInAfterFlush(durationMs: Long) {
            fadeAfterFlushMs = durationMs
        }

        @Synchronized
        override fun onSinkFlushed() {
            fadeAfterFlushMs?.let { processor?.startFade(FadeDirection.In, it) }
            fadeAfterFlushMs = null
        }

        @Synchronized
        override fun onStreamStart(config: AudioSink.AudioSinkConfig) {
            // A reconfigured renderer may configure its fresh sink without a separate flush callback.
            fadeAfterFlushMs?.let { processor?.startFade(FadeDirection.In, it) }
            fadeAfterFlushMs = null
            val timeline = config.timeline
            val periodId = config.mediaPeriodId
            val periodIndex = if (periodId != null) {
                timeline.getIndexOfPeriod(periodId.periodUid)
            } else C.INDEX_UNSET
            val mediaId = if (periodIndex != C.INDEX_UNSET) {
                val period = timeline.getPeriod(periodIndex, Timeline.Period())
                timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem.mediaId
            } else null
            // Read the preload first: promotion publishes currentItem before removing the map entry.
            val item = mediaId?.let { preloadedByMediaId[it] }
                ?: this.item.takeIf { mediaId == null || it.trackId == mediaId }
            val info = item?.let { resolver.resolve(it, config.format) }
                ?: GainInfo(null, null, null, null, GainForm.None)
            processor?.setTargetGainDb(control.onStreamStart(info))
        }

        @Synchronized
        fun setNormalization(settings: NormalizationSettings) {
            control.setNormalization(settings)?.let { processor?.setTargetGainDb(it) }
        }

        @Synchronized
        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            logDecoder(eventTime, decoderName)
        }

        @Synchronized
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            // Gapless tracks may reuse a decoder, so there is no initialization event for them.
            if (decoderReuseEvaluation != null &&
                decoderReuseEvaluation.result != DecoderReuseEvaluation.REUSE_RESULT_NO
            ) {
                logDecoder(eventTime, decoderReuseEvaluation.decoderName)
            }
        }

        private fun logDecoder(eventTime: AnalyticsListener.EventTime, name: String) {
            val periodId = eventTime.mediaPeriodId
            if (decoderLogged && loggedDecoderPeriod == periodId) return
            val mediaId = if (eventTime.windowIndex in 0 until eventTime.timeline.windowCount) {
                eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId
            } else null
            Log.i("Media3Engine", "Audio decoder=$name mediaId=$mediaId")
            loggedDecoderPeriod = periodId
            decoderLogged = true
        }
    }

    /** Clip-prevention flag stored from [setNormalization], seeded into the limiter when it opens. */
    @Volatile
    private var preventClip = true

    @Volatile
    private var limiter: LimiterSession? = null

    @Volatile
    private var sharedSessionId = 0

    override suspend fun prepare(
        item: PlaybackItem,
        gain: ItemGain,
        startPositionMs: Long,
        startPaused: Boolean,
    ) {
        check(!closed) { "engine is closed" }
        val resolver = gainResolver ?: gainResolverProvider().also { gainResolver = it }
        ensureLimiter()
        releaseCurrentPlayer()
        val candidate = try {
            factory.call {
                val gainState = PlayerStreamGain(resolver, normalizationSettings, item, startPositionMs)
                streamGain = gainState
                val p = factory.newPlayer(gainState)
                p.addAnalyticsListener(gainState)
                player = p
                currentItem = item
                prepared = false
                endedArmed = false
                val slot = Slot(p, item, gainState)
                currentSlot = slot
                p.addListener(slot.listener)
                if (sharedSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    p.setAudioSessionId(sharedSessionId)
                }
                p.setMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(File(item.audioPath)))
                        .setMediaId(item.trackId)
                        .build(),
                    startPositionMs,
                )
                p.playWhenReady = false
                p.prepare()
                applyFocusGainOnLooper(SystemClock.uptimeMillis())
                p
            }
        } catch (error: Throwable) {
            releaseCurrentPlayer()
            throw error
        }
        try {
            awaitReady(candidate)
        } catch (error: Throwable) {
            releaseCurrentPlayer()
            throw error
        }
        if (sharedSessionId != C.AUDIO_SESSION_ID_UNSET) {
            try {
                verifySharedSession(candidate)
            } catch (error: Throwable) {
                releaseCurrentPlayer()
                throw error
            }
        }
        factory.call {
            prepared = true
            endedArmed = true
            rawMs = candidate.currentPosition.coerceAtLeast(0L)
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = rawMs
        }
        if (!startPaused) play()
    }

    private suspend fun awaitReady(candidate: ExoPlayer) {
        val deadline = SystemClock.elapsedRealtime() + PREPARE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val error = factory.call { candidate.playerError }
            if (error != null) throw IOException("prepare failed: ${error.errorCodeName}", error)
            val state = factory.call { candidate.playbackState }
            if (state == Player.STATE_READY) return
            delay(PREPARE_POLL_MS)
        }
        throw IllegalStateException("timed out waiting for playback to become ready")
    }

    /**
     * Ensures [candidate] stays on the shared session (FR-036): `setAudioSessionId` applies
     * asynchronously and the player can overwrite it, so poll (50 ms steps, <= 3 s) re-setting
     * until the player's session matches. On failure the player must not start.
     */
    private suspend fun verifySharedSession(candidate: ExoPlayer) {
        val deadline = SystemClock.elapsedRealtime() + SESSION_VERIFY_TIMEOUT_MS
        while (true) {
            val session = factory.call { candidate.audioSessionId }
            if (session == sharedSessionId) return
            factory.call { candidate.setAudioSessionId(sharedSessionId) }
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw IllegalStateException("player not on the shared audio session")
            }
            delay(SESSION_VERIFY_POLL_MS)
        }
    }

    private fun ensureLimiter() {
        if (limiter != null) return
        val session = factory.newLimiterSession()
        sharedSessionId = session.sessionId
        limiter = session
        factory.call { session.open(preventClip) }
    }

    override suspend fun preloadNext(item: PlaybackItem, gain: ItemGain) {
        val file = File(item.audioPath)
        val added = factory.call {
            val p = player ?: return@call false
            execute(transition.snap(SnapReason.QueueEdit))
            clearPreloadedOnLooper(p)
            pendingNext = null
            if (transitionSettings.crossfadeMs > 0L) {
                pendingNext = item
                scheduleTransitionTick()
                return@call false
            }
            appendGapless(item)
            true
        }
        // Preserve the existing crossfade-off preload failure contract.
        if (added && !file.exists()) {
            clearPreloaded()
            throw IOException("preloadNext: audio file does not exist: ${item.audioPath}")
        }
    }

    override fun clearPreloaded() {
        factory.call {
            execute(transition.snap(SnapReason.QueueEdit))
            pendingNext = null
            player?.let { clearPreloadedOnLooper(it) }
        }
    }

    override fun hasPreloaded(): Boolean = factory.call {
        if (pendingNext != null || incomingSlot != null) return@call true
        val p = player ?: return@call false
        val index = p.currentMediaItemIndex
        index >= 0 && index + 1 < p.mediaItemCount
    }

    override fun play() {
        factory.call {
            val p = player ?: return@call
            if (!p.playWhenReady) armStartProbe(p)
            p.playWhenReady = true
            awaitingOutput = true
            outputBaseMs = p.currentPosition
            scheduleTransitionTick()
        }
    }

    private fun armStartProbe(p: ExoPlayer) {
        startProbePlayer = p
        startProbeBaseMs = p.currentPosition.coerceAtLeast(0L)
        startProbeAtMs = SystemClock.elapsedRealtime()
        startProbeSumMs = 0L
        startProbeSamples = 0
    }

    /** Extrapolates the audible clock back to its start position; averages the first few samples after it moves. */
    private fun sampleStartProbe() {
        val p = startProbePlayer ?: return
        if (!p.isPlaying) return
        val now = SystemClock.elapsedRealtime()
        val advanced = p.currentPosition - startProbeBaseMs
        if (advanced <= 1L) return
        if (advanced > START_PROBE_MAX_ADVANCE_MS) {
            startProbePlayer = null
            return
        }
        startProbeSumMs += now - advanced - startProbeAtMs
        startProbeSamples++
        if (startProbeSamples < START_PROBE_SAMPLES) return
        val latency = startProbeSumMs / startProbeSamples
        if (latency in 0L..MAX_START_LATENCY_MS) startLatencyMs = latency
        startProbePlayer = null
    }

    override fun pause() {
        factory.call {
            execute(transition.snap(SnapReason.Pause))
            startProbePlayer = null
            val p = player ?: return@call
            p.playWhenReady = false
            awaitingOutput = false
            rawMs = p.currentPosition.coerceAtLeast(0L)
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = rawMs
        }
    }

    override fun seekTo(positionMs: Long) {
        factory.call {
            execute(transition.snap(SnapReason.Seek))
            startProbePlayer = null
            val p = player ?: return@call
            streamGain?.positionOriginMs = positionMs
            p.seekTo(positionMs)
            endedArmed = true
            if (awaitingOutput) outputBaseMs = positionMs
            rawMs = positionMs
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = positionMs
        }
    }

    override fun positionMs(): Long = factory.call {
        val p = player ?: return@call 0L
        val now = SystemClock.elapsedRealtime()
        val current = p.currentPosition.coerceAtLeast(0L)
        if (current != rawMs) {
            rawMs = current
            rawAtMs = now
        }
        if (!p.isPlaying) {
            lastReturnedMs = rawMs
            return@call rawMs
        }
        val speed = p.playbackParameters.speed
        val extrapolated = rawMs + (min(now - rawAtMs, MAX_EXTRAPOLATION_MS) * speed).toLong()
        val result = max(lastReturnedMs, extrapolated)
        val duration = p.duration
        val clamped = if (duration != C.TIME_UNSET && duration > 0L) {
            result.coerceIn(0L, duration)
        } else {
            result.coerceAtLeast(0L)
        }
        lastReturnedMs = clamped
        clamped
    }

    override fun durationMs(): Long = factory.call {
        val p = player ?: return@call 0L
        val duration = p.duration
        if (duration == C.TIME_UNSET) currentSlot?.knownDurationMs ?: 0L else duration
    }

    override fun setCrossfade(durationMs: Long, repeatOneCrossfade: Boolean) {
        factory.call {
            transitionSettings = TransitionSettings(durationMs.coerceAtLeast(0L), repeatOneCrossfade = repeatOneCrossfade)
            execute(transition.updateSettings(transitionSettings), fallbackAfterRelease = true)
            if (transitionSettings.crossfadeMs == 0L && transition.phase != TransitionPhase.Transitioning) {
                pendingNext?.let { appendGapless(it) }
            } else if ((transition.phase == TransitionPhase.Idle || transition.phase == TransitionPhase.Scheduled) &&
                !transitionSettings.repeatOneCrossfade && pendingNext?.trackId == currentItem?.trackId
            ) {
                pendingNext?.let { appendGapless(it) }
            }
            scheduleTransitionTick()
        }
    }

    override fun beginCrossfade(durationMs: Long): Boolean = factory.call {
        if (transition.phase != TransitionPhase.Preparing) return@call false
        checkIncomingReady()
        val commands = transition.beginFade(durationMs)
        execute(commands)
        commands.any { it is TransitionCommand.StartFade }
    }

    override fun isCrossfading(): Boolean = factory.call { transition.phase == TransitionPhase.Transitioning }

    override fun snapCrossfade() {
        factory.call { execute(transition.snap(SnapReason.Pause)) }
    }

    override fun setFocusGain(gain: Float) {
        factory.call {
            val now = SystemClock.uptimeMillis()
            focusRamp.setTarget(gain, now)
            applyFocusGainOnLooper(now)
        }
    }

    override fun setDsp(settings: EqualizerSettings) {
        factory.setDsp(equalizerDspConfig(settings))
    }

    override fun setGains(current: ItemGain, preloaded: ItemGain?) = Unit

    override fun setNormalization(settings: NormalizationSettings) {
        factory.call {
            normalizationSettings = settings
            streamGain?.setNormalization(settings)
            incomingSlot?.gain?.setNormalization(settings)
            outgoingSlot?.gain?.setNormalization(settings)
            preventClip = settings.preventClip
            limiter?.setClipPrevention(settings.preventClip)
        }
    }

    override fun pollEvents(): List<EngineEvent> = factory.call {
        val p = player
        if (p != null && awaitingOutput && p.isPlaying && p.currentPosition > outputBaseMs) {
            awaitingOutput = false
            pendingEvents += EngineEvent.OutputStarted
        }
        val drained = mutableListOf<EngineEvent>()
        while (true) {
            val event = pendingEvents.poll() ?: break
            drained += event
        }
        drained
    }

    override fun close() {
        if (closed) return
        closed = true
        releaseCurrentPlayer()
        factory.call { pendingEvents.clear() }
        limiter?.close()
        limiter = null
    }

    /** The engine's shared session id (0 before the first prepare), for the device test. */
    internal val audioSessionIdForTest: Int get() = sharedSessionId

    /** The current player's audio session id (0 without a player), read on the playback looper. */
    internal fun playerAudioSessionIdForTest(): Int = factory.call { player?.audioSessionId ?: 0 }

    private fun releaseCurrentPlayer() {
        factory.call {
            execute(transition.snap(SnapReason.Stop))
            cancelIncomingStart()
            startProbePlayer = null
            transitionStepper?.let { transitionHandler?.removeCallbacks(it) }
            transitionStepper = null
            cancelFocusStepper()
            releaseIncoming()
            releaseOutgoing()
            currentSlot?.let { releaseSlot(it) }
            currentSlot = null
            player = null
            streamGain = null
            currentItem = null
            prepared = false
            awaitingOutput = false
            endedArmed = false
            pendingNext = null
            preloadedByMediaId.clear()
            // Stop begins a fresh playback lifecycle, including the failed-next suppression state.
            transition = TransitionController(transitionSettings)
        }
    }

    private fun resetPosition(position: Long) {
        rawMs = position.coerceAtLeast(0L)
        rawAtMs = SystemClock.elapsedRealtime()
        lastReturnedMs = rawMs
    }

    private fun appendGapless(item: PlaybackItem) {
        val p = player ?: return
        clearPreloadedOnLooper(p)
        preloadedByMediaId[item.trackId] = item
        p.addMediaItem(mediaItem(item))
        pendingNext = null
    }

    private fun mediaItem(item: PlaybackItem): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(File(item.audioPath))).setMediaId(item.trackId).build()

    /** Execute controller effects in order, exclusively on the factory looper. */
    private fun execute(commands: List<TransitionCommand>, fallbackAfterRelease: Boolean = false) {
        for (command in commands) when (command) {
            is TransitionCommand.PrepareIncoming -> prepareIncoming(command.item)
            TransitionCommand.ReleaseIncoming -> {
                releaseIncoming()
                if (fallbackAfterRelease) pendingNext?.let { appendGapless(it) }
            }
            is TransitionCommand.AppendGapless -> appendGapless(command.item)
            is TransitionCommand.StartFade -> startFade(command.durationMs)
            TransitionCommand.SnapFade -> {
                cancelIncomingStart()
                outgoingSlot?.p?.stop()
                streamGain?.cancelFadeAfterFlush()
                streamGain?.processor?.snapFade()
                // A player that already failed is left alone: re-preparing it would report the same failure twice.
                player?.takeIf { it.playerError == null }?.let { p ->
                    val position = p.currentPosition
                    streamGain?.positionOriginMs = position
                    p.seekTo(position)
                    // Paused position zero can make seek a no-op; discard preroll in that case too.
                    if (position == 0L) {
                        currentItem?.let { p.setMediaItem(mediaItem(it), position) }
                        p.prepare()
                    }
                    p.playWhenReady = true
                }
            }
            TransitionCommand.ReleaseOutgoing -> releaseOutgoing()
            is TransitionCommand.HardCut -> Unit // T060 owns manual next/previous policy.
        }
    }

    private fun prepareIncoming(item: PlaybackItem) {
        try {
            val gain = PlayerStreamGain(checkNotNull(gainResolver), normalizationSettings, item)
            val p = factory.newPlayer(gain)
            // Paused preroll is silent; the actual incoming curve is armed on the start flush.
            gain.processor?.startFade(FadeDirection.In, Long.MAX_VALUE / 48_000L)
            val slot = Slot(p, item, gain)
            incomingSlot = slot
            p.addAnalyticsListener(gain)
            p.addListener(slot.listener)
            if (sharedSessionId != C.AUDIO_SESSION_ID_UNSET) p.setAudioSessionId(sharedSessionId)
            p.volume = focusRamp.valueAt(SystemClock.uptimeMillis())
            p.setMediaItem(mediaItem(item))
            p.playWhenReady = false
            incomingDeadlineMs = SystemClock.elapsedRealtime() + PREPARE_TIMEOUT_MS
            incomingVerified = false
            sessionDeadlineMs = 0L
            p.prepare()
        } catch (error: Throwable) {
            Log.w("Media3Engine", "Incoming preparation failed; using gapless", error)
            execute(transition.onIncomingError(), fallbackAfterRelease = true)
        }
    }

    /** Non-blocking equivalent of prepare's shared-session verification, before B may start. */
    private fun checkIncomingReady() {
        val slot = incomingSlot ?: return
        if (transition.phase != TransitionPhase.Preparing) return
        val now = SystemClock.elapsedRealtime()
        val p = slot.p
        if (p.playerError != null) {
            execute(transition.onIncomingError(), fallbackAfterRelease = true)
            return
        }
        // A ready, session-verified B stays prepared however long playback pauses before the fade.
        if (incomingVerified) return
        if (now >= incomingDeadlineMs) {
            execute(transition.onIncomingError(), fallbackAfterRelease = true)
            return
        }
        if (p.playbackState != Player.STATE_READY) return
        if (sharedSessionId != C.AUDIO_SESSION_ID_UNSET && p.audioSessionId != sharedSessionId) {
            if (sessionDeadlineMs == 0L) sessionDeadlineMs = now + SESSION_VERIFY_TIMEOUT_MS
            if (now >= sessionDeadlineMs) {
                execute(transition.onIncomingError(), fallbackAfterRelease = true)
            } else p.setAudioSessionId(sharedSessionId)
            return
        }
        incomingVerified = true
        execute(transition.onIncomingReady())
    }

    private fun startFade(durationMs: Long) {
        val outgoing = checkNotNull(currentSlot)
        val incoming = checkNotNull(incomingSlot)
        val clock = outgoing.gain.processor?.processingPosition
        fadeSampleRate = clock?.sampleRate?.takeIf { it > 0 } ?: 48_000
        val processedMs = outgoing.gain.positionOriginMs +
            (clock?.frames ?: 0L) * 1000L / fadeSampleRate
        val startLeadMs = (processedMs - outgoing.p.currentPosition).coerceIn(0L, MAX_LEAD_MS)
        outgoingLeadMsForTest = startLeadMs
        fadeLeadFrames = outgoingLeadMsForTest * fadeSampleRate / 1000L
        val outgoingFadeGeneration = clock?.fadeGeneration ?: 0L
        val now = SystemClock.elapsedRealtime()
        fadeNotBeforeMs = now + durationMs + outgoingLeadMsForTest
        fadeDrainDeadlineMs = 0L
        outgoing.gain.processor?.startFade(FadeDirection.Out, durationMs)
        incoming.gain.fadeInAfterFlush(durationMs)
        // Replacing the single-item timeline forces a sink flush, even at paused position zero
        // where seekTo(0) may be optimized away. Arm the curve on that flush, after silent preroll.
        incoming.gain.positionOriginMs = incoming.p.currentPosition
        incoming.p.setMediaItem(mediaItem(incoming.item), incoming.p.currentPosition)
        incoming.p.prepare()
        outgoingSlot = outgoing
        incomingSlot = null
        currentSlot = incoming
        player = incoming.p
        currentItem = incoming.item
        streamGain = incoming.gain
        pendingNext = null
        preloadedByMediaId.clear()
        endedArmed = true
        resetPosition(incoming.p.currentPosition)
        // OutputStarted belongs to prepare/play only (contract); a pending one now waits on the incoming clock.
        if (awaitingOutput) outputBaseMs = incoming.p.currentPosition
        val handler = transitionHandler ?: Handler(Looper.myLooper()!!).also { transitionHandler = it }
        val startDeadlineMs = now + PREPARE_TIMEOUT_MS
        val start = object : Runnable {
            private var playAtMs: Long? = null
            override fun run() {
                incomingStart = null
                if (player !== incoming.p || transition.phase != TransitionPhase.Transitioning) return
                val p = incoming.p
                val elapsed = SystemClock.elapsedRealtime()
                if (elapsed >= startDeadlineMs) {
                    handlePlayerError(incoming, PlaybackException("Incoming restart timed out", null,
                        PlaybackException.ERROR_CODE_TIMEOUT))
                    return
                }
                if (playAtMs == null) {
                    // Measure the frame where the audio thread actually observed the request. A buffer
                    // in flight at startFade must not introduce a whole decoder-buffer timing error.
                    val observed = outgoing.gain.processor?.processingPosition
                    val leadMs = when {
                        observed != null && observed.fadeGeneration > outgoingFadeGeneration &&
                            observed.sampleRate > 0 -> {
                            val curveStartMs = outgoing.gain.positionOriginMs +
                                observed.fadeStartFrame * 1000L / observed.sampleRate
                            fadeSampleRate = observed.sampleRate
                            (curveStartMs - outgoing.p.currentPosition).coerceIn(0L, MAX_LEAD_MS)
                        }
                        // A never processes the request when its decoder already finished (fade begun near the
                        // end): fall back to the lead measured at fade start, so B is never left waiting.
                        outgoing.p.playbackState == Player.STATE_ENDED || outgoing.gain.processor == null ||
                            elapsed - now >= OBSERVE_FALLBACK_MS ->
                            (startLeadMs - (elapsed - now)).coerceAtLeast(0L)
                        else -> null
                    }
                    if (leadMs != null) {
                        outgoingLeadMsForTest = leadMs
                        fadeLeadFrames = leadMs * fadeSampleRate / 1000L
                        fadeNotBeforeMs = elapsed + leadMs + durationMs
                        // B's curve starts at its first post-flush frame, heard one output start latency after play.
                        incomingDelayMsForTest = (leadMs - startLatencyMs).coerceAtLeast(0L)
                        playAtMs = elapsed + incomingDelayMsForTest
                        Log.i("Media3Engine", "fade leadMs=$leadMs startLatencyMs=$startLatencyMs " +
                            "incomingDelayMs=$incomingDelayMsForTest")
                    }
                }
                val playAt = playAtMs
                // Recheck FR-036 after the forced preroll flush/reprepare, before any play call.
                val ready = p.playbackState == Player.STATE_READY &&
                    (sharedSessionId == C.AUDIO_SESSION_ID_UNSET || p.audioSessionId == sharedSessionId)
                if (!ready) {
                    if (sharedSessionId != C.AUDIO_SESSION_ID_UNSET) p.setAudioSessionId(sharedSessionId)
                } else if (playAt != null && elapsed >= playAt) {
                    startIncomingPlayback(p)
                    return
                }
                incomingStart = this
                handler.postDelayed(this, if (ready && playAt != null) playAt - elapsed else FOCUS_STEP_MS)
            }
        }
        incomingStart = start
        handler.post(start)
        applyFocusGainOnLooper(SystemClock.uptimeMillis())
        pendingEvents += EngineEvent.TransitionStarted(incoming.item, durationMs)
        scheduleTransitionTick()
    }

    private fun handlePlayerError(slot: Slot, error: PlaybackException) {
        if (slot === incomingSlot && transition.phase == TransitionPhase.Preparing) {
            execute(transition.onIncomingError(), fallbackAfterRelease = true)
            return
        }
        if (!prepared || (slot !== currentSlot && slot !== outgoingSlot)) return
        // Capture attribution before a snap can release the failing player's renderer/source.
        val timeline = slot.p.currentTimeline
        val periodId = (error as? ExoPlaybackException)?.mediaPeriodId
        val periodIndex = periodId?.let { timeline.getIndexOfPeriod(it.periodUid) } ?: C.INDEX_UNSET
        val failedMediaId = if (periodIndex != C.INDEX_UNSET) {
            val period = timeline.getPeriod(periodIndex, Timeline.Period())
            timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem.mediaId
        } else slot.p.currentMediaItem?.mediaId
        val failedItem = failedMediaId?.let { preloadedByMediaId[it] } ?: slot.item
        val extension = failedItem.audioPath.substringAfterLast('.', "")
        // A preload source can fail before the AUTO callback; its error must not borrow A's format.
        val format = if (failedItem == slot.item) {
            slot.p.audioFormat?.sampleMimeType
                ?: slot.knownMime?.takeIf { it.first == failedItem.trackId }?.second
                ?: extension.ifEmpty { "unknown" }
        } else extension.ifEmpty { "unknown" }
        val provider = factory.providerRecorder.providerFor(Uri.fromFile(File(failedItem.audioPath)).toString())
        execute(transition.onPlayerError())
        pendingEvents += EngineEvent.Error(provider ?: "platform", format, error)
    }

    private fun releaseSlot(slot: Slot) {
        slot.p.removeListener(slot.listener)
        slot.p.removeAnalyticsListener(slot.gain)
        factory.release(slot.p)
    }

    private fun releaseIncoming() {
        val slot = incomingSlot ?: return
        incomingSlot = null
        releaseSlot(slot)
    }

    private fun releaseOutgoing() {
        val startPending = incomingStart != null
        cancelIncomingStart()
        val slot = outgoingSlot ?: return
        outgoingSlot = null
        releaseSlot(slot)
        // The fade ended (or A failed) before B's delayed start: B plays now rather than staying silent.
        if (startPending) player?.let { startIncomingPlayback(it) }
    }

    private fun startIncomingPlayback(p: ExoPlayer) {
        if (sharedSessionId != C.AUDIO_SESSION_ID_UNSET && p.audioSessionId != sharedSessionId) {
            p.setAudioSessionId(sharedSessionId)
        }
        armStartProbe(p)
        p.playWhenReady = true
    }

    private fun cancelIncomingStart() {
        incomingStart?.let { transitionHandler?.removeCallbacks(it) }
        incomingStart = null
    }

    private fun scheduleTransitionTick() {
        if (closed || transitionStepper != null || player == null) return
        val handler = transitionHandler ?: Handler(Looper.myLooper()!!).also { transitionHandler = it }
        val tick = object : Runnable {
            override fun run() {
                transitionStepper = null
                if (closed) return
                val p = player ?: return
                sampleStartProbe()
                val item = currentItem ?: return
                if (p.isPlaying && p.duration != C.TIME_UNSET) {
                    val next = pendingNext
                    val repeatOne = next?.trackId == item.trackId
                    execute(transition.onPosition(item, (p.duration - p.currentPosition).coerceAtLeast(0L),
                        next, repeatOne))
                    // The controller declines repeat-one fades when disabled. Keep that advance
                    // on the normal playlist path, after preloadNext has only stored the pending item.
                    if (next != null && repeatOne && !transitionSettings.repeatOneCrossfade &&
                        transition.phase == TransitionPhase.Idle
                    ) appendGapless(next)
                }
                checkIncomingReady()
                val outgoing = outgoingSlot
                if (outgoing != null && transition.phase == TransitionPhase.Transitioning) {
                    val now = SystemClock.elapsedRealtime()
                    val remaining = outgoing.gain.processor?.fadeRemainingFrames ?: 0L
                    if (remaining == 0L || outgoing.p.playbackState == Player.STATE_ENDED) {
                        // Do not release merely when processing ends: drain the already-queued curve.
                        if (fadeDrainDeadlineMs == 0L) {
                            fadeDrainDeadlineMs = if (outgoing.p.playbackState == Player.STATE_ENDED) fadeNotBeforeMs
                                else now + fadeAudibleEndMs(remaining, fadeLeadFrames, fadeSampleRate)
                        }
                        if (now >= max(fadeNotBeforeMs, fadeDrainDeadlineMs)) execute(transition.onFadeComplete())
                    }
                }
                if (p.playWhenReady || incomingSlot != null || outgoingSlot != null) scheduleTransitionTick()
            }
        }
        transitionStepper = tick
        handler.postDelayed(tick, if (outgoingSlot != null || startProbePlayer != null) FOCUS_STEP_MS else
            if (incomingSlot != null) SESSION_VERIFY_POLL_MS else POSITION_TICK_MS)
    }

    internal fun playersForTest(): List<ExoPlayer> = factory.call {
        listOfNotNull(currentSlot?.p, incomingSlot?.p, outgoingSlot?.p)
    }

    internal fun gainProcessingPositionForTest(outgoing: Boolean): GainProcessor.ProcessingPosition? = factory.call {
        (if (outgoing) outgoingSlot else currentSlot)?.gain?.processor?.processingPosition
    }

    internal fun injectErrorForTest(outgoing: Boolean, error: PlaybackException) {
        factory.call { (if (outgoing) outgoingSlot else currentSlot)?.let { handlePlayerError(it, error) } }
    }

    /**
     * Applies the focus ramp to the current player on the playback looper: sets
     * `player.volume` to the ramp value now and, while the ramp has not settled, posts a
     * stepper every [FOCUS_STEP_MS]. A retarget replaces any running stepper; a missing
     * player just records the target so the next [prepare] seeds the new player.
     */
    private fun applyFocusGainOnLooper(nowMs: Long) {
        val p = player
        if (p == null) {
            cancelFocusStepper()
            return
        }
        val volume = focusRamp.valueAt(nowMs)
        p.volume = volume
        incomingSlot?.p?.volume = volume
        outgoingSlot?.p?.volume = volume
        if (focusRamp.isSettled(nowMs)) {
            cancelFocusStepper()
            return
        }
        scheduleFocusStep()
    }

    private fun scheduleFocusStep() {
        cancelFocusStepper()
        val handler = focusHandler ?: Handler(Looper.myLooper()!!).also { focusHandler = it }
        val stepper = object : Runnable {
            override fun run() {
                focusStepper = null
                val p = player ?: return
                val now = SystemClock.uptimeMillis()
                val volume = focusRamp.valueAt(now)
                p.volume = volume
                incomingSlot?.p?.volume = volume
                outgoingSlot?.p?.volume = volume
                if (focusRamp.isSettled(now)) return
                focusStepper = this
                handler.postDelayed(this, FOCUS_STEP_MS)
            }
        }
        focusStepper = stepper
        handler.postDelayed(stepper, FOCUS_STEP_MS)
    }

    private fun cancelFocusStepper() {
        focusStepper?.let { focusHandler?.removeCallbacks(it) }
        focusStepper = null
    }

    /** Removes every playlist item after the current one and forgets any preloaded mapping. */
    private fun clearPreloadedOnLooper(p: ExoPlayer) {
        val index = p.currentMediaItemIndex
        if (index >= 0 && index + 1 < p.mediaItemCount) {
            p.removeMediaItems(index + 1, p.mediaItemCount)
        }
        preloadedByMediaId.clear()
    }

    private companion object {
        const val POSITION_TICK_MS = 250L
        const val DEFAULT_START_LATENCY_MS = 50L
        const val MAX_START_LATENCY_MS = 500L
        const val MAX_LEAD_MS = 1_500L
        const val OBSERVE_FALLBACK_MS = 200L
        const val START_PROBE_SAMPLES = 5
        const val START_PROBE_MAX_ADVANCE_MS = 2_000L
        const val PREPARE_TIMEOUT_MS = 10_000L
        const val PREPARE_POLL_MS = 10L
        const val MAX_EXTRAPOLATION_MS = 500L
        const val FOCUS_STEP_MS = 10L
        const val SESSION_VERIFY_TIMEOUT_MS = 3_000L
        const val SESSION_VERIFY_POLL_MS = 50L
    }
}
