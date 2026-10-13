package me.misa198.airmedy.player.media3

import me.misa198.airmedy.player.PlaybackItem

internal enum class TransitionPhase { Idle, Scheduled, Preparing, Transitioning, Cleanup }

internal enum class SnapReason { Pause, Seek, Stop, QueueEdit }

/** Settings captured per transition. Future-setting hooks (FR-031) default to today's behaviour. */
internal data class TransitionSettings(
    val crossfadeMs: Long,
    val preBufferMs: Long = 5_000L,
    val repeatOneCrossfade: Boolean = true,
    val crossfadeOnManualSkip: Boolean = false,
)

internal sealed interface TransitionCommand {
    /** Create player B on the shared session and prepare it with [item]. */
    data class PrepareIncoming(val item: PlaybackItem) : TransitionCommand
    /** Release player B before any fade started with it. */
    data object ReleaseIncoming : TransitionCommand
    /** Start B, swap roles (B becomes current), start both curves, emit one TransitionStarted. */
    data class StartFade(val durationMs: Long) : TransitionCommand
    /** Fallback when B is not ready at fade start: append [item] to A's playlist (gapless, no silence). */
    data class AppendGapless(val item: PlaybackItem) : TransitionCommand
    /** Stop the outgoing player at once; incoming curve forced to 1 and flushed at its position. */
    data object SnapFade : TransitionCommand
    /** Release the outgoing player after a fade or snap. */
    data object ReleaseOutgoing : TransitionCommand
    /** Stop the transition and play [item] alone on one player from [positionMs]. */
    data class HardCut(val item: PlaybackItem, val positionMs: Long = 0L) : TransitionCommand
}

internal class TransitionController(settings: TransitionSettings) {
    val phase: TransitionPhase get() = TransitionPhase.Idle
    /** Duration of the running fade, null when not Transitioning. */
    val runningFadeMs: Long? get() = null
    /** Item B holds (Preparing or Transitioning), else null. */
    val incomingItem: PlaybackItem? get() = null
    /** New settings; apply to later transitions only, never to a running fade (FR-032). */
    fun updateSettings(settings: TransitionSettings): List<TransitionCommand> = emptyList()
    /** Position tick of the current track. [next] = queue successor (with repeat-one: the current item itself). */
    fun onPosition(current: PlaybackItem, remainingMs: Long, next: PlaybackItem?, repeatOne: Boolean): List<TransitionCommand> = emptyList()
    fun onIncomingReady(): List<TransitionCommand> = emptyList()
    /** The coordinator decided the fade starts now with [durationMs] (already clamped). */
    fun beginFade(durationMs: Long): List<TransitionCommand> = emptyList()
    fun onFadeComplete(): List<TransitionCommand> = emptyList()
    fun snap(reason: SnapReason): List<TransitionCommand> = emptyList()
    /** Manual next; [afterIncoming] = the queue item after the incoming one (i+2), null at queue end. */
    fun onNext(afterIncoming: PlaybackItem?): List<TransitionCommand> = emptyList()
    /** Manual previous; [incomingPositionMs] = incoming player's position; [outgoing] = outgoing item. */
    fun onPrevious(incomingPositionMs: Long, outgoing: PlaybackItem): List<TransitionCommand> = emptyList()
    fun onIncomingError(): List<TransitionCommand> = emptyList()
    /** A player error while Transitioning (either player). */
    fun onPlayerError(): List<TransitionCommand> = emptyList()
}
