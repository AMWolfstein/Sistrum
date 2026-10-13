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
    private var settings = settings
    private var currentPhase = TransitionPhase.Idle
    private var fadeDurationMs: Long? = null
    private var incoming: PlaybackItem? = null
    private var incomingReady = false
    // The incoming item that failed to prepare; not retried while it stays the next item.
    private var suppressedTrackId: String? = null

    val phase: TransitionPhase get() = currentPhase
    /** Duration of the running fade, null when not Transitioning. */
    val runningFadeMs: Long? get() = fadeDurationMs
    /** Item B holds (Preparing or Transitioning), else null. */
    val incomingItem: PlaybackItem? get() = incoming

    fun onManualSkip(target: PlaybackItem): List<TransitionCommand> = emptyList() // T060c

    fun onSkipReadyTimeout(): List<TransitionCommand> = emptyList() // T060c

    /** New settings; apply to later transitions only, never to a running fade (FR-032). */
    fun updateSettings(settings: TransitionSettings): List<TransitionCommand> {
        this.settings = settings
        if (phase == TransitionPhase.Preparing && settings.crossfadeMs == 0L) {
            return releaseIncoming()
        }
        return emptyList()
    }
    /** Position tick of the current track. [next] = queue successor (with repeat-one: the current item itself). */
    fun onPosition(current: PlaybackItem, remainingMs: Long, next: PlaybackItem?, repeatOne: Boolean): List<TransitionCommand> {
        if (phase != TransitionPhase.Idle && phase != TransitionPhase.Scheduled) return emptyList()
        if (next?.trackId != suppressedTrackId) suppressedTrackId = null
        if (settings.crossfadeMs <= 0L || next == null || next.trackId == suppressedTrackId ||
            (repeatOne && !settings.repeatOneCrossfade)
        ) {
            reset()
            return emptyList()
        }
        if (remainingMs > settings.crossfadeMs + settings.preBufferMs) {
            currentPhase = TransitionPhase.Scheduled
            return emptyList()
        }
        incoming = next
        incomingReady = false
        currentPhase = TransitionPhase.Preparing
        return listOf(TransitionCommand.PrepareIncoming(next))
    }

    fun onIncomingReady(): List<TransitionCommand> {
        if (phase == TransitionPhase.Preparing) incomingReady = true
        return emptyList()
    }
    /** The coordinator decided the fade starts now with [durationMs] (already clamped). */
    fun beginFade(durationMs: Long): List<TransitionCommand> {
        if (phase != TransitionPhase.Preparing) return emptyList()
        if (!incomingReady) {
            val item = checkNotNull(incoming)
            reset()
            return listOf(TransitionCommand.ReleaseIncoming, TransitionCommand.AppendGapless(item))
        }
        currentPhase = TransitionPhase.Transitioning
        fadeDurationMs = durationMs
        return listOf(TransitionCommand.StartFade(durationMs))
    }

    fun onFadeComplete(): List<TransitionCommand> {
        if (phase != TransitionPhase.Transitioning) return emptyList()
        reset()
        return listOf(TransitionCommand.ReleaseOutgoing)
    }

    fun snap(reason: SnapReason): List<TransitionCommand> = when (phase) {
        TransitionPhase.Transitioning -> snapFade()
        TransitionPhase.Preparing -> if (reason == SnapReason.QueueEdit || reason == SnapReason.Stop) {
            releaseIncoming()
        } else {
            emptyList()
        }
        else -> emptyList()
    }
    /** Manual next; [afterIncoming] = the queue item after the incoming one (i+2), null at queue end. */
    fun onNext(afterIncoming: PlaybackItem?): List<TransitionCommand> = when (phase) {
        TransitionPhase.Transitioning -> {
            val commands = snapFade()
            if (afterIncoming != null) commands + TransitionCommand.HardCut(afterIncoming, 0L) else commands
        }
        TransitionPhase.Preparing -> releaseIncoming()
        else -> emptyList()
    }
    /** Manual previous; [incomingPositionMs] = incoming player's position; [outgoing] = outgoing item. */
    fun onPrevious(incomingPositionMs: Long, outgoing: PlaybackItem): List<TransitionCommand> {
        if (phase != TransitionPhase.Transitioning) return emptyList()
        val item = if (incomingPositionMs <= 3_000L) outgoing else checkNotNull(incoming)
        return snapFade() + TransitionCommand.HardCut(item, 0L)
    }

    fun onIncomingError(): List<TransitionCommand> {
        if (phase != TransitionPhase.Preparing) return emptyList()
        suppressedTrackId = checkNotNull(incoming).trackId
        return releaseIncoming()
    }
    /** A player error while Transitioning (either player). */
    fun onPlayerError(): List<TransitionCommand> =
        if (phase == TransitionPhase.Transitioning) snapFade() else emptyList()

    private fun releaseIncoming(): List<TransitionCommand> {
        reset()
        return listOf(TransitionCommand.ReleaseIncoming)
    }

    private fun snapFade(): List<TransitionCommand> {
        reset()
        return listOf(TransitionCommand.SnapFade, TransitionCommand.ReleaseOutgoing)
    }

    private fun reset() {
        currentPhase = TransitionPhase.Idle
        incoming = null
        incomingReady = false
        fadeDurationMs = null
    }
}
