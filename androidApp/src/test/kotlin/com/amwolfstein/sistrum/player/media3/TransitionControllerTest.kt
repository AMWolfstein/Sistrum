package com.amwolfstein.sistrum.player.media3

import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.media3.TransitionCommand.*
import org.junit.Assert.*
import org.junit.Test

class TransitionControllerTest {
    private val a = PlaybackItem("a", "A", "x", "/a")
    private val b = PlaybackItem("b", "B", "x", "/b")
    private val c = PlaybackItem("c", "C", "x", "/c")
    private val d = PlaybackItem("d", "D", "x", "/d")
    private val settings = TransitionSettings(crossfadeMs = 6_000L)

    @Test
    fun startsIdleAndSchedulesKnownSuccessorOutsideWindow() {
        val controller = TransitionController(settings)
        assertIdle(controller)
        assertCommands(controller.onPosition(a, 60_000L, b, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertNull(controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }

    @Test
    fun preparesIncomingExactlyAtInclusiveWindowBoundary() {
        val controller = TransitionController(settings)
        assertCommands(controller.onPosition(a, 11_001L, b, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.onPosition(a, 11_000L, b, false), PrepareIncoming(b))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(b, controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }

    @Test
    fun preparingTicksNeverDuplicatePrepareIncoming() {
        val controller = preparing()
        for (remaining in listOf(11_000L, 10_000L, 6_000L, 0L)) {
            assertCommands(controller.onPosition(a, remaining, b, false))
            assertEquals(TransitionPhase.Preparing, controller.phase)
            assertEquals(b, controller.incomingItem)
        }
    }

    @Test
    fun crossfadeOffNeverPreparesIncomingAndStaysIdle() {
        preparing() // Positive control: the same window prepares B with crossfade enabled.
        val controller = TransitionController(TransitionSettings(crossfadeMs = 0L))
        for (remaining in listOf(60_000L, 11_001L, 11_000L, 6_000L, 0L)) {
            assertCommands(controller.onPosition(a, remaining, b, false))
            assertIdle(controller)
        }
    }

    @Test
    fun queueEndNeverPreparesIncomingAndStaysIdle() {
        preparing() // Positive control for the otherwise no-op queue-end assertions.
        val controller = TransitionController(settings)
        for (remaining in listOf(60_000L, 11_000L, 0L)) {
            assertCommands(controller.onPosition(a, remaining, null, false))
            assertIdle(controller)
        }
    }

    @Test
    fun readyIncomingStartsFadeAndCompletionReleasesOutgoing() {
        val controller = preparing()
        assertCommands(controller.onIncomingReady())
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(b, controller.incomingItem)
        assertCommands(controller.beginFade(6_000L), StartFade(6_000L))
        assertRunning(controller, 6_000L, b)
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
    }

    @Test
    fun unreadyIncomingFallsBackToGaplessInCommandOrder() {
        val controller = preparing()
        assertCommands(controller.beginFade(6_000L), ReleaseIncoming, AppendGapless(b))
        assertIdle(controller)
    }

    @Test
    fun beginFadeInIdleOrScheduledEmitsNothingAndPreservesPhase() {
        val controller = TransitionController(settings)
        assertCommands(controller.beginFade(6_000L))
        assertIdle(controller)
        assertCommands(controller.onPosition(a, 60_000L, b, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.beginFade(6_000L))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertNull(controller.runningFadeMs)
        assertNull(controller.incomingItem)
    }

    @Test
    fun pauseDuringFadeSnapsAndReleasesOutgoing() = assertSnapDuringFade(SnapReason.Pause)

    @Test
    fun seekDuringFadeSnapsAndReleasesOutgoing() = assertSnapDuringFade(SnapReason.Seek)

    @Test
    fun stopDuringFadeSnapsAndReleasesOutgoing() = assertSnapDuringFade(SnapReason.Stop)

    @Test
    fun queueEditDuringFadeSnapsAndReleasesOutgoing() = assertSnapDuringFade(SnapReason.QueueEdit)

    @Test
    fun queueEditWhilePreparingReleasesStaleIncoming() = assertReleaseWhilePreparing(SnapReason.QueueEdit)

    @Test
    fun stopWhilePreparingReleasesIncoming() = assertReleaseWhilePreparing(SnapReason.Stop)

    @Test
    fun pauseWhilePreparingKeepsIncoming() = assertKeepWhilePreparing(SnapReason.Pause)

    @Test
    fun seekWhilePreparingKeepsIncoming() = assertKeepWhilePreparing(SnapReason.Seek)

    @Test
    fun nextDuringFadeSnapsReleasesAndHardCutsToItemAfterIncoming() {
        val controller = fading()
        assertCommands(controller.onNext(c), SnapFade, ReleaseOutgoing, HardCut(c, 0L))
        assertIdle(controller)
    }

    @Test
    fun nextDuringFadeAtQueueEndOnlySnapsAndReleases() {
        val controller = fading()
        assertCommands(controller.onNext(null), SnapFade, ReleaseOutgoing)
        assertIdle(controller)
    }

    @Test
    fun nextWhilePreparingReleasesIncoming() {
        val controller = preparing()
        assertCommands(controller.onNext(c), ReleaseIncoming)
        assertIdle(controller)
    }

    @Test
    fun nextInIdleEmitsNothingForNormalSkip() {
        preparing() // Positive control so this test cannot pass against an all-no-op controller.
        val controller = TransitionController(settings)
        assertCommands(controller.onNext(c))
        assertIdle(controller)
    }

    @Test
    fun previousAtThreeSecondBoundaryHardCutsToOutgoing() = assertPrevious(3_000L, a)

    @Test
    fun previousAtZeroHardCutsToOutgoing() = assertPrevious(0L, a)

    @Test
    fun previousPastThreeSecondsRestartsIncoming() = assertPrevious(3_001L, b)

    @Test
    fun repeatOnePreparesCurrentItemOnIncomingPlayer() {
        val controller = TransitionController(settings)
        assertCommands(controller.onPosition(a, 11_000L, a, true), PrepareIncoming(a))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(a, controller.incomingItem)
    }

    @Test
    fun repeatOneCrossfadeDisabledLeavesSinglePlayerIdle() {
        val enabled = TransitionController(settings)
        assertCommands(enabled.onPosition(a, 11_000L, a, true), PrepareIncoming(a))
        val controller = TransitionController(TransitionSettings(6_000L, repeatOneCrossfade = false))
        for (remaining in listOf(60_000L, 11_000L, 0L)) {
            assertCommands(controller.onPosition(a, remaining, a, true))
            assertIdle(controller)
        }
    }

    @Test
    fun longerSettingsPreserveRunningFadeAndApplyToNextWindow() {
        val controller = fading()
        assertCommands(controller.updateSettings(TransitionSettings(crossfadeMs = 12_000L)))
        assertRunning(controller, 6_000L, b)
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onPosition(b, 17_001L, c, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.onPosition(b, 17_000L, c, false), PrepareIncoming(c))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(c, controller.incomingItem)
    }

    @Test
    fun disablingCrossfadePreservesRunningFadeAndAppliesAfterCompletion() {
        val controller = fading()
        assertCommands(controller.updateSettings(TransitionSettings(crossfadeMs = 12_000L)))
        assertRunning(controller, 6_000L, b)
        assertCommands(controller.updateSettings(TransitionSettings(crossfadeMs = 0L)))
        assertRunning(controller, 6_000L, b)
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onPosition(b, 0L, c, false))
        assertIdle(controller)
    }

    @Test
    fun disablingCrossfadeWhilePreparingReleasesIncomingImmediately() {
        val controller = preparing()
        assertCommands(controller.updateSettings(TransitionSettings(crossfadeMs = 0L)), ReleaseIncoming)
        assertIdle(controller)
        assertCommands(controller.onPosition(a, 0L, b, false))
        assertIdle(controller)
    }

    @Test
    fun changingToNonzeroSettingsWhilePreparingKeepsIncoming() {
        val controller = preparing()
        assertCommands(controller.updateSettings(TransitionSettings(crossfadeMs = 12_000L)))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(b, controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }

    @Test
    fun positionTicksDuringFadeNeverPrepareItemAfterIncoming() {
        val controller = fading()
        for (next in listOf(c, d, null)) {
            for (remaining in listOf(60_000L, 11_000L, 0L)) {
                assertCommands(controller.onPosition(b, remaining, next, false))
                assertRunning(controller, 6_000L, b)
            }
        }
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onPosition(b, 11_000L, c, false), PrepareIncoming(c))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(c, controller.incomingItem)
    }

    @Test
    fun secondAutomaticTransitionPreparesOnSingleTickAndStartsWithoutAnotherTick() {
        val controller = fading()
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onPosition(b, 2_000L, c, false), PrepareIncoming(c))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(c, controller.incomingItem)
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.beginFade(2_000L), StartFade(2_000L))
        assertRunning(controller, 2_000L, c)
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
    }

    @Test
    fun beginFadeWhileTransitioningDoesNotRestartOrChangeDuration() {
        val controller = fading()
        assertCommands(controller.beginFade(2_500L))
        assertRunning(controller, 6_000L, b)
    }

    @Test
    fun seekImmediatelyAfterFadeStartsSnapsAndReleasesOutgoing() {
        val controller = preparing()
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.beginFade(6_000L), StartFade(6_000L))
        assertCommands(controller.snap(SnapReason.Seek), SnapFade, ReleaseOutgoing)
        assertIdle(controller)
    }

    @Test
    fun incomingErrorReleasesAndSuppressesRetriesByTrackIdButAllowsDifferentItem() {
        val controller = preparing()
        assertCommands(controller.onIncomingError(), ReleaseIncoming)
        assertIdle(controller)
        for (remaining in listOf(11_000L, 6_000L, 0L)) {
            assertCommands(controller.onPosition(a, remaining, b, false))
            assertFalse(controller.phase == TransitionPhase.Preparing)
            assertNotEquals(TransitionPhase.Cleanup, controller.phase)
            assertNull(controller.incomingItem)
        }
        val sameTrackWithChangedMetadata = b.copy(title = "Updated B", audioPath = "/updated-b")
        assertCommands(controller.onPosition(a, 0L, sameTrackWithChangedMetadata, false))
        assertFalse(controller.phase == TransitionPhase.Preparing)
        assertNull(controller.incomingItem)
        assertCommands(controller.onPosition(a, 11_000L, c, false), PrepareIncoming(c))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(c, controller.incomingItem)
    }

    @Test
    fun playerErrorDuringFadeSnapsAndReleasesOutgoing() {
        val controller = fading()
        assertCommands(controller.onPlayerError(), SnapFade, ReleaseOutgoing)
        assertIdle(controller)
    }

    @Test
    fun playerAndIncomingErrorsInIdleEmitNothing() {
        preparing() // Positive control for both idle error no-ops.
        val controller = TransitionController(settings)
        assertCommands(controller.onPlayerError())
        assertIdle(controller)
        assertCommands(controller.onIncomingError())
        assertIdle(controller)
    }

    @Test
    fun futureSettingDefaultsKeepRepeatOneCrossfadeAndHardCutManualNext() {
        assertTrue(settings.repeatOneCrossfade)
        assertFalse(settings.crossfadeOnManualSkip)
        assertEquals(5_000L, settings.preBufferMs)
        val controller = fading()
        assertCommands(controller.onNext(c), SnapFade, ReleaseOutgoing, HardCut(c, 0L))
        assertIdle(controller)
    }

    @Test
    fun fractionalSecondFadeUsesMillisecondsForWindowAndStartCommand() {
        val controller = TransitionController(TransitionSettings(crossfadeMs = 2_500L))
        assertCommands(controller.onPosition(a, 7_501L, b, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.onPosition(a, 7_500L, b, false), PrepareIncoming(b))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(b, controller.incomingItem)
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.beginFade(2_500L), StartFade(2_500L))
        assertRunning(controller, 2_500L, b)
    }

    private fun preparing(): TransitionController {
        val controller = TransitionController(settings)
        assertCommands(controller.onPosition(a, 11_000L, b, false), PrepareIncoming(b))
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(b, controller.incomingItem)
        assertNull(controller.runningFadeMs)
        return controller
    }

    private fun fading(): TransitionController {
        val controller = preparing()
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.beginFade(6_000L), StartFade(6_000L))
        assertRunning(controller, 6_000L, b)
        return controller
    }

    private fun assertSnapDuringFade(reason: SnapReason) {
        val controller = fading()
        assertCommands(controller.snap(reason), SnapFade, ReleaseOutgoing)
        assertIdle(controller)
    }

    private fun assertReleaseWhilePreparing(reason: SnapReason) {
        val controller = preparing()
        assertCommands(controller.snap(reason), ReleaseIncoming)
        assertIdle(controller)
    }

    private fun assertKeepWhilePreparing(reason: SnapReason) {
        val controller = preparing()
        assertCommands(controller.snap(reason))
        assertNotEquals(TransitionPhase.Cleanup, controller.phase)
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertEquals(b, controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }

    private fun assertPrevious(positionMs: Long, expected: PlaybackItem) {
        val controller = fading()
        assertCommands(controller.onPrevious(positionMs, a), SnapFade, ReleaseOutgoing, HardCut(expected, 0L))
        assertIdle(controller)
    }

    private fun assertCommands(actual: List<TransitionCommand>, vararg expected: TransitionCommand) {
        assertEquals(expected.toList(), actual)
    }

    private fun assertIdle(controller: TransitionController) {
        assertNotEquals(TransitionPhase.Cleanup, controller.phase)
        assertEquals(TransitionPhase.Idle, controller.phase)
        assertNull(controller.runningFadeMs)
        assertNull(controller.incomingItem)
    }

    private fun assertRunning(controller: TransitionController, durationMs: Long, item: PlaybackItem) {
        assertEquals(TransitionPhase.Transitioning, controller.phase)
        assertEquals(durationMs, controller.runningFadeMs)
        assertEquals(item, controller.incomingItem)
    }
}
