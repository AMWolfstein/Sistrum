package me.misa198.airmedy.player.media3

import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.media3.TransitionCommand.*
import org.junit.Assert.*
import org.junit.Test

/** FR-031a: ordered commands and captured duration for a manual transition. */
class TransitionControllerSkipTest {
    private val a = PlaybackItem("a", "A", "x", "/a")
    private val b = PlaybackItem("b", "B", "x", "/b")
    private val c = PlaybackItem("c", "C", "x", "/c")
    private val settings = TransitionSettings(4_000L, crossfadeOnManualSkip = true)

    @Test
    fun skipSettingDefaultsOffAndHardCutsInIdle() {
        val defaults = TransitionSettings(4_000L)
        assertFalse(defaults.crossfadeOnManualSkip)
        val controller = TransitionController(defaults)
        assertCommands(controller.onManualSkip(c), HardCut(c))
        assertIdle(controller)
    }

    @Test
    fun disabledSkipReleasesPreparingIncomingBeforeHardCut() {
        val controller = automaticPreparing(settings.copy(crossfadeOnManualSkip = false))
        assertCommands(controller.onManualSkip(c), ReleaseIncoming, HardCut(c))
        assertIdle(controller)
    }

    @Test
    fun disabledSkipSnapsRunningFadeBeforeHardCut() {
        val controller = automaticFading(settings.copy(crossfadeOnManualSkip = false))
        assertCommands(controller.onManualSkip(c), SnapFade, ReleaseOutgoing, HardCut(c))
        assertIdle(controller)
    }

    @Test
    fun zeroDurationWithSkipEnabledHardCutsInIdle() {
        val controller = TransitionController(settings.copy(crossfadeMs = 0L))
        assertCommands(controller.onManualSkip(b), HardCut(b))
        assertIdle(controller)
    }

    @Test
    fun zeroDurationDuringRunningFadeSnapsBeforeHardCut() {
        val controller = automaticFading()
        assertCommands(controller.updateSettings(settings.copy(crossfadeMs = 0L)))
        assertCommands(controller.onManualSkip(c), SnapFade, ReleaseOutgoing, HardCut(c))
        assertIdle(controller)
    }

    @Test
    fun manualSkipDuringAutomaticFadeSnapsReleasesAndHardCuts() {
        val controller = automaticFading()
        assertCommands(controller.onManualSkip(c), SnapFade, ReleaseOutgoing, HardCut(c))
        assertIdle(controller)
        assertCommands(controller.onFadeComplete())
        assertIdle(controller)
    }

    @Test
    fun manualSkipDuringSkipFadeSnapsReleasesAndHardCuts() {
        val controller = skipFading()
        assertCommands(controller.onManualSkip(c), SnapFade, ReleaseOutgoing, HardCut(c))
        assertIdle(controller)
        assertCommands(controller.onFadeComplete())
    }

    @Test
    fun idleSkipPreparesTargetAndWaitsForReadiness() {
        val controller = TransitionController(settings)
        assertCommands(controller.onManualSkip(c), PrepareIncoming(c))
        assertPreparing(controller, c)
        assertCommands(controller.onPosition(a, 0L, b, false))
        assertPreparing(controller, c)
    }

    @Test
    fun scheduledSkipPreparesTargetInsteadOfAutomaticSuccessor() {
        val controller = TransitionController(settings)
        assertCommands(controller.onPosition(a, 60_000L, b, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.onManualSkip(c), PrepareIncoming(c))
        assertPreparing(controller, c)
    }

    @Test
    fun skipReleasesDifferentUnreadyAutomaticIncomingAndPreparesTarget() {
        val controller = automaticPreparing()
        assertCommands(controller.onManualSkip(c), ReleaseIncoming, PrepareIncoming(c))
        assertPreparing(controller, c)
        assertCommands(controller.onIncomingReady(), StartFade(2_000L))
        assertRunning(controller, c, 2_000L)
    }

    @Test
    fun skipReleasesDifferentReadyAutomaticIncomingAndPreparesTarget() {
        val controller = automaticPreparing()
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.onManualSkip(c), ReleaseIncoming, PrepareIncoming(c))
        assertPreparing(controller, c)
        assertCommands(controller.onIncomingReady(), StartFade(2_000L))
        assertRunning(controller, c, 2_000L)
    }

    @Test
    fun skipReusesMatchingReadyIncomingAndStartsImmediately() {
        val controller = automaticPreparing()
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.onManualSkip(b), StartFade(2_000L))
        assertRunning(controller, b, 2_000L)
    }

    @Test
    fun skipReusesMatchingUnreadyIncomingAndStartsWhenReady() {
        val controller = automaticPreparing()
        assertCommands(controller.onManualSkip(b))
        assertPreparing(controller, b)
        assertCommands(controller.onIncomingReady(), StartFade(2_000L))
        assertRunning(controller, b, 2_000L)
    }

    @Test
    fun incomingReadyForPendingSkipStartsCapturedFadeExactlyOnce() {
        val controller = pendingSkip()
        assertCommands(controller.onIncomingReady(), StartFade(2_000L))
        assertRunning(controller, c, 2_000L)
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.onPosition(c, 0L, b, false))
        assertRunning(controller, c, 2_000L)
    }

    @Test
    fun skipReadyTimeoutReleasesAndHardCutsPendingTargetExactlyOnce() {
        val controller = pendingSkip()
        assertCommands(controller.onSkipReadyTimeout(), ReleaseIncoming, HardCut(c))
        assertIdle(controller)
        assertCommands(controller.onSkipReadyTimeout())
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.onIncomingError())
        assertIdle(controller)
    }

    @Test
    fun skipReadyTimeoutWithoutPendingSkipPreservesIdleScheduledAndAutomaticPreparation() {
        // Positive control: the timeout must actually settle a pending skip.
        val pending = pendingSkip()
        assertCommands(pending.onSkipReadyTimeout(), ReleaseIncoming, HardCut(c))
        val controller = TransitionController(settings)
        assertCommands(controller.onSkipReadyTimeout())
        assertIdle(controller)
        controller.onPosition(a, 60_000L, b, false)
        assertCommands(controller.onSkipReadyTimeout())
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.onPosition(a, 9_000L, b, false), PrepareIncoming(b))
        assertCommands(controller.onSkipReadyTimeout())
        assertPreparing(controller, b)
        controller.onIncomingReady()
        assertCommands(controller.onSkipReadyTimeout())
        assertPreparing(controller, b)
    }

    @Test
    fun skipReadyTimeoutDuringRunningFadeDoesNothing() {
        val controller = skipFading()
        assertCommands(controller.onSkipReadyTimeout())
        assertRunning(controller, c, 2_000L)
    }

    @Test
    fun pendingSkipErrorHardCutsWithoutSuppressingTargetForLaterAutomaticPreparation() {
        val controller = pendingSkip()
        assertCommands(controller.onIncomingError(), ReleaseIncoming, HardCut(c))
        assertIdle(controller)
        assertCommands(controller.onIncomingError())
        assertCommands(controller.onPosition(a, 9_000L, c, false), PrepareIncoming(c))
        assertPreparing(controller, c)
    }

    @Test
    fun everySnapReasonSettlesPendingSkipWithReleaseThenHardCut() {
        SnapReason.entries.forEach { reason ->
            val controller = pendingSkip()
            assertCommands(controller.snap(reason), ReleaseIncoming, HardCut(c))
            assertIdle(controller)
            assertCommands(controller.onSkipReadyTimeout())
            assertCommands(controller.onIncomingReady())
            assertIdle(controller)
        }
    }

    @Test
    fun skipFadeCompletionReleasesOutgoingAndReturnsIdleExactlyOnce() {
        val controller = skipFading()
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onFadeComplete())
        assertCommands(controller.onSkipReadyTimeout())
        assertCommands(controller.onManualSkip(b), PrepareIncoming(b))
        assertPreparing(controller, b)
    }

    @Test
    fun settingsChangesNeverAlterRunningSkipFadeAndApplyAfterCompletion() {
        val controller = skipFading()
        for (updated in listOf(
            settings.copy(crossfadeMs = 12_000L),
            settings.copy(crossfadeMs = 300L),
            settings.copy(crossfadeMs = 0L, crossfadeOnManualSkip = false),
        )) {
            assertCommands(controller.updateSettings(updated))
            assertRunning(controller, c, 2_000L)
        }
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onManualSkip(b), HardCut(b))
        assertIdle(controller)
    }

    @Test
    fun skipFadeClampsFourSecondsToTwoSeconds() = assertClamp(4_000L, 2_000L)

    @Test
    fun skipFadeClampsThreeHundredMillisecondsToFiveHundred() = assertClamp(300L, 500L)

    @Test
    fun skipFadePreservesTwelveHundredMilliseconds() = assertClamp(1_200L, 1_200L)

    private fun assertClamp(configured: Long, expected: Long) {
        // Both a freshly prepared skip and reuse of a ready automatic B use the same clamp.
        val pending = pendingSkip(settings.copy(crossfadeMs = configured))
        assertCommands(pending.onIncomingReady(), StartFade(expected))
        assertRunning(pending, c, expected)
        val ready = automaticPreparing(settings.copy(crossfadeMs = configured))
        ready.onIncomingReady()
        assertCommands(ready.onManualSkip(b), StartFade(expected))
        assertRunning(ready, b, expected)
    }

    private fun automaticPreparing(config: TransitionSettings = settings): TransitionController =
        TransitionController(config).also {
            assertCommands(it.onPosition(a, config.crossfadeMs + config.preBufferMs, b, false), PrepareIncoming(b))
            assertPreparing(it, b)
        }

    private fun automaticFading(config: TransitionSettings = settings): TransitionController =
        automaticPreparing(config).also {
            assertCommands(it.onIncomingReady())
            assertCommands(it.beginFade(config.crossfadeMs), StartFade(config.crossfadeMs))
            assertRunning(it, b, config.crossfadeMs)
        }

    private fun pendingSkip(config: TransitionSettings = settings): TransitionController =
        TransitionController(config).also {
            assertCommands(it.onManualSkip(c), PrepareIncoming(c))
            assertPreparing(it, c)
        }

    private fun skipFading(): TransitionController = pendingSkip().also {
        assertCommands(it.onIncomingReady(), StartFade(2_000L))
        assertRunning(it, c, 2_000L)
    }

    private fun assertCommands(actual: List<TransitionCommand>, vararg expected: TransitionCommand) {
        assertEquals(expected.toList(), actual)
    }

    private fun assertPreparing(controller: TransitionController, item: PlaybackItem) {
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(item, controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }

    private fun assertRunning(controller: TransitionController, item: PlaybackItem, durationMs: Long) {
        assertEquals(TransitionPhase.Transitioning, controller.phase)
        assertEquals(item, controller.incomingItem)
        assertEquals(durationMs, controller.runningFadeMs)
    }

    private fun assertIdle(controller: TransitionController) {
        assertEquals(TransitionPhase.Idle, controller.phase)
        assertNull(controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }
}
