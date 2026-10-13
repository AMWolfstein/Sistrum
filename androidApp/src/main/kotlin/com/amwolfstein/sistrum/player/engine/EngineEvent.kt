package com.amwolfstein.sistrum.player.engine

import com.amwolfstein.sistrum.player.PlaybackItem
import java.io.IOException

/**
 * Decode failure during prepare, as specified by the 2026-10-14 T062d/T062e amendment in
 * `specs/001-media3-migration/contracts/player-engine.md` (FR-064).
 */
internal class EngineDecodeException(val provider: String, val format: String, cause: Throwable) :
    IOException(cause.message, cause)

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
