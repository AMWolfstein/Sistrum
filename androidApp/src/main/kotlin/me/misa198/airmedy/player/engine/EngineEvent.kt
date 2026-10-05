package me.misa198.airmedy.player.engine

import me.misa198.airmedy.player.PlaybackItem

/**
 * Events emitted by a [PlayerEngine]. An automatic advance emits exactly one
 * [TransitionStarted] or [GaplessAdvanced], in order, even when two happen
 * within one position tick (FR-089).
 */
internal sealed interface EngineEvent {
    /** Emitted at fade start for an automatic crossfade into [incoming] over [fadeMs]. */
    data class TransitionStarted(val incoming: PlaybackItem, val fadeMs: Long) : EngineEvent

    /** Emitted for an automatic gapless advance into [incoming]. */
    data class GaplessAdvanced(val incoming: PlaybackItem) : EngineEvent

    /** The current item ended and nothing was preloaded. */
    data object Ended : EngineEvent

    /** The audio output route was disconnected. */
    data object OutputDisconnected : EngineEvent

    /**
     * Audio output actually started after an unpaused prepare or play() (FR-090).
     * The coordinator reports Playing only after it.
     */
    data object OutputStarted : EngineEvent

    /** Playback failed for [format] from [provider] with [cause]. */
    data class Error(val provider: String, val format: String, val cause: Throwable) : EngineEvent
}
