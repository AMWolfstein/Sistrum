package me.misa198.airmedy.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T035a: while Playing the coordinator queues a light position-only save every 10 s, restarting the
 * window after any full save, never saving while not playing, and coalescing behind a busy drainer
 * without ever writing the full session on the periodic path.
 */
class PeriodicSessionSaveTest {

    @Test
    fun `position save only fires ten seconds after the last full save`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.position = 5_000L

        harness.clock.elapsed = 9_999L
        harness.tick()
        assertTrue(harness.sessionStore.positionSaves.isEmpty())

        harness.clock.elapsed = 10_000L
        harness.tick()

        assertEquals(listOf(SavedPosition("a", 5_000L)), harness.sessionStore.positionSaves)
    }

    @Test
    fun `thirty five seconds of playback writes three position saves and no new full saves`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        val fullSaves = harness.sessionStore.saved.size

        var elapsed = 0L
        repeat(175) {
            elapsed += 200L
            harness.clock.elapsed = elapsed
            harness.tick()
        }

        assertEquals(3, harness.sessionStore.positionSaves.size)
        assertEquals(fullSaves, harness.sessionStore.saved.size)
    }

    @Test
    fun `paused playback never queues a position save`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPause)

        var elapsed = 0L
        repeat(150) {
            elapsed += 200L
            harness.clock.elapsed = elapsed
            harness.tick()
        }

        assertTrue(harness.sessionStore.positionSaves.isEmpty())
    }

    @Test
    fun `a full save restarts the ten second window`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.clock.elapsed = 5_000L
        harness.tick()
        assertTrue(harness.sessionStore.positionSaves.isEmpty())

        harness.send(PlaybackService.ActionSeek, positionMs = 3_000L)

        harness.clock.elapsed = 14_999L
        harness.tick()
        assertTrue(harness.sessionStore.positionSaves.isEmpty())

        harness.clock.elapsed = 15_000L
        harness.tick()

        assertEquals(1, harness.sessionStore.positionSaves.size)
    }

    @Test
    fun `two due periodic saves coalesce to the latest while the drainer is busy`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.position = 1_000L
        val gate = CompletableDeferred<Unit>()
        harness.sessionStore.saveGate = gate

        harness.clock.elapsed = 10_000L
        harness.tick()
        assertTrue(harness.sessionStore.positionSaves.isEmpty())

        harness.clock.elapsed = 20_000L
        harness.engine.position = 2_000L
        harness.tick()

        harness.clock.elapsed = 30_000L
        harness.engine.position = 3_000L
        harness.tick()

        gate.complete(Unit)
        harness.advance()

        assertEquals(
            listOf(SavedPosition("a", 1_000L), SavedPosition("a", 3_000L)),
            harness.sessionStore.positionSaves,
        )
    }

    @Test
    fun `a full save drops a pending position save`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.position = 1_000L
        val gate = CompletableDeferred<Unit>()
        harness.sessionStore.saveGate = gate

        // Occupy the drainer with the first periodic write.
        harness.clock.elapsed = 10_000L
        harness.tick()
        assertTrue(harness.sessionStore.positionSaves.isEmpty())

        // A newer position stays pending behind the blocked write.
        harness.clock.elapsed = 20_000L
        harness.engine.position = 2_000L
        harness.tick()
        val fullSavesBefore = harness.sessionStore.saved.size

        // The full save must drop the pending position and win.
        harness.send(PlaybackService.ActionSeek, positionMs = 7_000L)

        gate.complete(Unit)
        harness.advance()

        assertEquals(listOf(SavedPosition("a", 1_000L)), harness.sessionStore.positionSaves)
        assertEquals(fullSavesBefore + 1, harness.sessionStore.saved.size)
        assertEquals(7_000L, harness.sessionStore.saved.last().positionMs)
    }
}
