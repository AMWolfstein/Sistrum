package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FR-092: equalizer and crossfade preference updates are de-duplicated. Equalizer, crossfade and
 * other playback preferences share one DataStore, so any write re-emits every settings flow with
 * identical values; the coordinator must not re-prepare the next track or touch the engine unless a
 * value that affects playback actually changed.
 *
 * NOTE: `PlaybackPreferences.settings`/`EqualizerPreferences.settings` are DataStore-backed and
 * cannot be collected in a host unit test (no Android `Context`), so their `.distinctUntilChanged()`
 * is not exercised here. The coordinator-level guard is the tested behavior below.
 */
class PreferenceChurnTest {

    private fun enginePreloadCalls(harness: PlaybackCoordinatorHarness): Int =
        harness.engine.calls.count { it == "clearPreloaded" || it == "preloadNext" }

    private fun engineSetDspCalls(harness: PlaybackCoordinatorHarness): Int =
        harness.engine.calls.count { it == "setDsp" }

    // 1. Repeating the same crossfade preference must not re-prepare the next track.
    @Test
    fun `repeated playback settings do not re-prepare the next track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        // Empty the native preload slot so the first application has observable work to do.
        harness.engine.preloadedItem = null

        harness.coordinator.withCommandLock { harness.coordinator.onPlaybackSettings(5, true) }
        val afterFirst = enginePreloadCalls(harness)
        // Empty the slot again: without de-duplication the repeats would now re-prepare the next track.
        harness.engine.preloadedItem = null

        harness.coordinator.withCommandLock { harness.coordinator.onPlaybackSettings(5, true) }
        harness.coordinator.withCommandLock { harness.coordinator.onPlaybackSettings(5, true) }

        assertEquals(afterFirst, enginePreloadCalls(harness))
    }

    // 2. A real crossfade change is applied.
    @Test
    fun `a changed crossfade length applies`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        harness.coordinator.withCommandLock { harness.coordinator.onPlaybackSettings(5, true) }
        harness.coordinator.withCommandLock { harness.coordinator.onPlaybackSettings(6, true) }

        assertEquals(6, harness.flows.crossfadeSeconds.value)
    }

    // 3. Repeating the same equalizer settings reaches the engine exactly once. The fake records
    //    only the `setDsp` call name (not its argument), so the observable effect is the call count.
    @Test
    fun `repeated equalizer settings reach the engine once`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        val settings = EqualizerSettings(enabled = true, presetKey = "rock")

        val before = engineSetDspCalls(harness)
        harness.coordinator.withCommandLock { harness.coordinator.onEqualizerSettings(settings) }
        harness.coordinator.withCommandLock { harness.coordinator.onEqualizerSettings(settings) }
        harness.coordinator.withCommandLock { harness.coordinator.onEqualizerSettings(settings) }

        assertEquals(before + 1, engineSetDspCalls(harness))
    }

    // 4. A changed EqualizerSettings is not de-duplicated away.
    @Test
    fun `a changed equalizer setting reaches the engine`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        harness.coordinator.withCommandLock {
            harness.coordinator.onEqualizerSettings(EqualizerSettings(enabled = true, presetKey = "rock"))
        }
        val afterFirst = engineSetDspCalls(harness)
        harness.coordinator.withCommandLock {
            harness.coordinator.onEqualizerSettings(EqualizerSettings(enabled = true, presetKey = "jazz"))
        }

        assertEquals(afterFirst + 1, engineSetDspCalls(harness))
    }
}
