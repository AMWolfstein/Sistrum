package me.misa198.airmedy.player.engine

import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem

/** Which playback backend an [PlayerEngine] instance drives. */
internal enum class EngineKind { Native, Media3 }

/**
 * Native engine only: the service's analysis-based gain handed to the native
 * `normalizationGainDb` parameter. The Media3 engine ignores it and resolves
 * gain itself from the track's `Format`.
 */
internal data class ItemGain(val gainDb: Float) {
    companion object {
        val Unity = ItemGain(0f)
    }
}

/**
 * Internal playback seam shared by the native and Media3 backends. All calls
 * arrive from the service's single command consumer, so implementations need no
 * thread safety beyond that. Both implementations may emit [EngineEvent]s; an
 * automatic advance produces exactly one [EngineEvent.TransitionStarted] or
 * [EngineEvent.GaplessAdvanced] per advance, delivered in order, even when two
 * happen within one position tick (FR-089). A failed [prepare] or [preloadNext]
 * throws after releasing anything it created, so failures never leak resources
 * (FR-086). "Playing" is reported only once output actually started; a start
 * failure surfaces as [EngineEvent.Error] (FR-090).
 */
internal interface PlayerEngine : Closeable {
    /** Backend identity for the engine. */
    val kind: EngineKind

    /** Ordered stream of playback transitions, endings, disconnects and errors. */
    val events: Flow<EngineEvent>

    /**
     * Opens [item] as the current source. Throws after releasing anything it
     * created on failure (FR-086).
     */
    suspend fun prepare(item: PlaybackItem, gain: ItemGain, startPositionMs: Long, startPaused: Boolean)

    /**
     * Loads the following [item] into the idle slot without interrupting the
     * current source. Throws after releasing anything it created on failure
     * (FR-086).
     */
    suspend fun preloadNext(item: PlaybackItem, gain: ItemGain)

    /** Retires the preloaded slot, if any. */
    fun clearPreloaded()

    /** Whether a preloaded item is ready to advance to. */
    fun hasPreloaded(): Boolean

    /** Starts or resumes output; playing is reported only after output started (FR-090). */
    fun play()

    /** Pauses output. */
    fun pause()

    /** Seeks the current source to [positionMs]. */
    fun seekTo(positionMs: Long)

    /** Current playback position in milliseconds. */
    fun positionMs(): Long

    /** Duration of the current source in milliseconds. */
    fun durationMs(): Long

    /**
     * Starts a crossfade over [durationMs]. Returns false, as a no-op, when
     * nothing is preloaded or a fade is already running.
     */
    fun beginCrossfade(durationMs: Long): Boolean

    /** Whether a crossfade is currently running. */
    fun isCrossfading(): Boolean

    /** Stops the outgoing source immediately; the incoming source continues at full level. */
    fun snapCrossfade()

    /** Sets the focus gain (1.0 or 0.2), ramped inside the engine. */
    fun setFocusGain(gain: Float)

    /** Applies equalizer settings: 10 bands, preamp and stereo width. */
    fun setDsp(settings: EqualizerSettings)

    /** Native only: sets the service-side analysis gain for current and preloaded. */
    fun setGains(current: ItemGain, preloaded: ItemGain?)

    /**
     * Media3 only: retargets both players with a ramped gain change (FR-046a).
     * The native engine may treat this as a no-op.
     */
    fun setNormalization(settings: NormalizationSettings)
}
