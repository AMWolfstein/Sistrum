package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.engine.ItemGain
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorNormalizationTest {
    private val settings = NormalizationSettings(enabled = true, targetLufs = -18f)

    @Test
    fun media3ReceivesSettingsBeforeEveryPrepare() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.kind = EngineKind.Media3
        harness.coordinator.onNormalizationSettings(settings)
        harness.engines.onPrepare = {
            assertEquals(listOf(settings), harness.engine.normalizationCalls)
        }
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.send(PlaybackService.ActionNext)
        assertEquals(2, harness.engines.created.size)
        harness.engines.created.forEach {
            assertEquals(listOf(settings), it.normalizationCalls)
            val setAt = it.calls.indexOf("setNormalization")
            assertTrue("setNormalization must be called", setAt >= 0)
            assertTrue("setNormalization must precede prepare", setAt < it.calls.indexOf("prepare"))
        }
    }

    @Test
    fun media3SettingsChangesRetargetWithoutSetGains() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.kind = EngineKind.Media3
        harness.coordinator.onNormalizationSettings(settings)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        val changed = settings.copy(targetLufs = -14f, preventClip = false)
        harness.coordinator.onNormalizationSettings(changed)
        assertEquals(listOf(settings, changed), harness.engine.normalizationCalls)
        assertTrue(harness.engine.gainCalls.isEmpty())
        assertTrue("setGains" !in harness.engine.calls)
        assertEquals(0, harness.library.activeAnalysesCalls)
        assertEquals(0, harness.library.disableNormalizationCalls)
    }

    @Test
    fun media3PreparesAndPreloadsUnityWithoutLibraryAnalysis() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.kind = EngineKind.Media3
        harness.coordinator.onNormalizationSettings(settings)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        assertEquals(ItemGain.Unity, harness.engine.prepareCalls.single().gain)
        assertEquals(ItemGain.Unity, harness.engine.preloadCalls.single().second)
        assertEquals(0, harness.library.activeAnalysesCalls)
        assertEquals(0, harness.library.disableNormalizationCalls)
    }

    @Test
    fun media3RestoreAlsoSeedsSettingsAndUsesUnity() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.kind = EngineKind.Media3
        harness.coordinator.onNormalizationSettings(settings)
        harness.restore(
            PlaybackSession(PlaybackQueueSnapshot(
                originalTrackIds = listOf("a", "b"), activeTrackIds = listOf("a", "b"), currentIndex = 0,
            ), positionMs = 1_000L),
            setOf("a", "b"),
        )
        assertEquals(listOf(settings), harness.engine.normalizationCalls)
        assertTrue(harness.engine.calls.indexOf("setNormalization") < harness.engine.calls.indexOf("prepare"))
        assertTrue(harness.engine.prepareCalls.single().startPaused)
        assertEquals(ItemGain.Unity, harness.engine.prepareCalls.single().gain)
        assertEquals(ItemGain.Unity, harness.engine.preloadCalls.single().second)
        assertEquals(0, harness.library.activeAnalysesCalls)
        assertEquals(0, harness.library.disableNormalizationCalls)
    }

    @Test
    fun nativeKeepsAnalysisDisableAndGainRefreshBehavior() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.coordinator.onNormalizationSettings(settings)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        assertEquals(2, harness.library.activeAnalysesCalls)
        assertEquals(2, harness.library.disableNormalizationCalls)
        assertEquals(listOf(settings), harness.engine.normalizationCalls)
        assertTrue(harness.engine.calls.indexOf("setNormalization") < harness.engine.calls.indexOf("prepare"))
        val changed = settings.copy(targetLufs = -14f)
        harness.coordinator.onNormalizationSettings(changed)
        assertEquals(listOf(settings, changed), harness.engine.normalizationCalls)
        assertEquals(listOf(ItemGain.Unity to ItemGain.Unity), harness.engine.gainCalls)
        assertEquals(4, harness.library.activeAnalysesCalls)
        assertEquals(4, harness.library.disableNormalizationCalls)
    }
}
