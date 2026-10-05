package me.misa198.airmedy.player.engine

import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.FfmpegDecoder
import me.misa198.airmedy.player.GlobalDspConfig
import me.misa198.airmedy.player.NativeTransition
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.clampSeekPosition
import me.misa198.airmedy.player.equalizerDspConfig

/**
 * The native decoder surface the engine drives. Narrower than [FfmpegDecoder]:
 * only the calls today's playback path actually makes.
 */
internal interface NativeDecoderPort : Closeable {
    fun prepare(path: String, normalizationGainDb: Float)
    fun preload(path: String, normalizationGainDb: Float): Boolean
    fun setNormalizationGains(activeDb: Float, preloadedDb: Float)
    fun setFocusGain(gain: Float)
    fun clearPreloaded()
    fun hasPreloaded(): Boolean
    fun beginCrossfade(durationMs: Long)
    fun snapCrossfade()
    fun isCrossfading(): Boolean
    fun consumeTransition(): NativeTransition?
    fun setGlobalDspConfig(config: GlobalDspConfig)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun durationMs(): Long
    fun positionMs(): Long
    fun isFinished(): Boolean
    fun isOutputDisconnected(): Boolean
}

/** Pure delegation from [NativeDecoderPort] to the JNI-backed [FfmpegDecoder]. */
internal class FfmpegDecoderPort(private val decoder: FfmpegDecoder) : NativeDecoderPort {
    override fun prepare(path: String, normalizationGainDb: Float) = decoder.prepare(File(path), normalizationGainDb)
    override fun preload(path: String, normalizationGainDb: Float): Boolean = decoder.preload(File(path), normalizationGainDb)
    override fun setNormalizationGains(activeDb: Float, preloadedDb: Float) = decoder.setNormalizationGains(activeDb, preloadedDb)
    override fun setFocusGain(gain: Float) = decoder.setFocusGain(gain)
    override fun clearPreloaded() = decoder.clearPreloaded()
    override fun hasPreloaded(): Boolean = decoder.hasPreloaded()
    override fun beginCrossfade(durationMs: Long) = decoder.beginCrossfade(durationMs)
    override fun snapCrossfade() = decoder.snapCrossfade()
    override fun isCrossfading(): Boolean = decoder.isCrossfading()
    override fun consumeTransition(): NativeTransition? = decoder.consumeTransition()
    override fun setGlobalDspConfig(config: GlobalDspConfig) = decoder.setGlobalDspConfig(config)
    override fun play() = decoder.play()
    override fun pause() = decoder.pause()
    override fun seekTo(positionMs: Long) = decoder.seekTo(positionMs)
    override fun durationMs(): Long = decoder.durationMs()
    override fun positionMs(): Long = decoder.positionMs()
    override fun isFinished(): Boolean = decoder.isFinished()
    override fun isOutputDisconnected(): Boolean = decoder.isOutputDisconnected()
    override fun close() = decoder.close()
}

/**
 * 1:1 wrapper of today's native playback path (ADR-001) behind [PlayerEngine].
 *
 * Native reports automatic advances through a single transition slot, so the
 * engine drains [NativeDecoderPort.consumeTransition] before every call that
 * could overwrite that slot (prepare, preloadNext, clearPreloaded,
 * beginCrossfade) and surfaces the results through [pollEvents] (FR-089). The
 * native engine has no asynchronous event channel: the service ticker pulls
 * [pollEvents] synchronously, so [events] is always empty.
 */
internal class LegacyNativeEngine(
    private val newPort: () -> NativeDecoderPort = { FfmpegDecoderPort(FfmpegDecoder()) },
) : PlayerEngine {
    override val kind: EngineKind = EngineKind.Native

    override val events: Flow<EngineEvent> = emptyFlow()

    private var port: NativeDecoderPort? = null
    private var preloadedItem: PlaybackItem? = null
    private var lastFadeMs: Long = 0L
    private var dspConfig: GlobalDspConfig = GlobalDspConfig()
    private var focusGain: Float = 1f
    private var lastFinished = false
    private var lastOutputDisconnected = false
    private val pendingEvents = ArrayDeque<EngineEvent>()

    /** Drains native state and returns every buffered event exactly once, in order. */
    override fun pollEvents(): List<EngineEvent> {
        drain()
        port?.let { current ->
            val disconnected = current.isOutputDisconnected()
            if (disconnected && !lastOutputDisconnected) pendingEvents += EngineEvent.OutputDisconnected
            lastOutputDisconnected = disconnected
            val finished = current.isFinished()
            if (finished && !lastFinished) pendingEvents += EngineEvent.Ended
            lastFinished = finished
        }
        return pendingEvents.toList().also { pendingEvents.clear() }
    }

    private fun drain() {
        val current = port ?: return
        while (true) {
            val transition = current.consumeTransition() ?: break
            val item = preloadedItem ?: continue
            when (transition) {
                NativeTransition.GaplessPromoted -> pendingEvents += EngineEvent.GaplessAdvanced(item)
                NativeTransition.CrossfadeStarted -> pendingEvents += EngineEvent.TransitionStarted(item, lastFadeMs)
            }
            preloadedItem = null
        }
    }

    override suspend fun prepare(item: PlaybackItem, gain: ItemGain, startPositionMs: Long, startPaused: Boolean) {
        drain()
        port?.close()
        port = null
        preloadedItem = null
        val candidate = newPort()
        try {
            candidate.setGlobalDspConfig(dspConfig)
            candidate.setFocusGain(focusGain)
            candidate.prepare(item.audioPath, gain.gainDb)
            if (startPositionMs > 0L) candidate.seekTo(clampSeekPosition(startPositionMs, candidate.durationMs()))
            if (startPaused) candidate.pause() else candidate.play()
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
        port = candidate
        lastFinished = false
        lastOutputDisconnected = false
    }

    override suspend fun preloadNext(item: PlaybackItem, gain: ItemGain) {
        drain()
        val current = port ?: return
        try {
            if (current.preload(item.audioPath, gain.gainDb)) preloadedItem = item
        } catch (error: Throwable) {
            preloadedItem = null
            throw error
        }
    }

    override fun clearPreloaded() {
        drain()
        port?.clearPreloaded()
        preloadedItem = null
    }

    override fun hasPreloaded(): Boolean = preloadedItem != null && port?.hasPreloaded() == true

    override fun play() { port?.play() }

    override fun pause() { port?.pause() }

    override fun seekTo(positionMs: Long) { port?.seekTo(positionMs) }

    override fun positionMs(): Long = port?.positionMs() ?: 0L

    override fun durationMs(): Long = port?.durationMs() ?: 0L

    override fun beginCrossfade(durationMs: Long): Boolean {
        drain()
        val current = port ?: return false
        if (!hasPreloaded() || current.isCrossfading()) return false
        current.beginCrossfade(durationMs)
        val started = current.isCrossfading()
        if (started) lastFadeMs = durationMs
        return started
    }

    override fun isCrossfading(): Boolean = port?.isCrossfading() == true

    override fun snapCrossfade() { port?.snapCrossfade() }

    override fun setFocusGain(gain: Float) {
        focusGain = gain
        port?.setFocusGain(gain)
    }

    override fun setDsp(settings: EqualizerSettings) {
        dspConfig = equalizerDspConfig(settings)
        port?.setGlobalDspConfig(dspConfig)
    }

    override fun setGains(current: ItemGain, preloaded: ItemGain?) {
        port?.setNormalizationGains(current.gainDb, preloaded?.gainDb ?: 0f)
    }

    override fun setNormalization(settings: NormalizationSettings) = Unit

    override fun close() {
        port?.close()
        port = null
        preloadedItem = null
        pendingEvents.clear()
        lastFinished = false
        lastOutputDisconnected = false
    }
}
