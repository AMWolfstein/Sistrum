package me.misa198.airmedy.player

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import me.misa198.airmedy.player.fakes.ReversingDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Required behaviour that already holds; must stay green. These FR-084/FR-085/FR-086/FR-089 cases pass
 * today and guard against regression while T026/T027 add the command path.
 */
class PlaybackCommandPathGuardTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
        artworkPath = "/art/$id.jpg",
    )

    private fun session(trackIds: List<String>, currentIndex: Int, positionMs: Long) = PlaybackSession(
        queue = PlaybackQueueSnapshot(
            originalTrackIds = trackIds,
            activeTrackIds = trackIds,
            currentIndex = currentIndex,
        ),
        positionMs = positionMs,
    )

    // ------------------------------------------------------------------ FR-084

    @Test
    fun `FR-084 cancelling a suspended command propagates without an uncaught exception`() = runTest {
        val dispatcher = ReversingDispatcher()
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, throwable -> uncaught += throwable } + dispatcher)
        val harness = PlaybackCoordinatorHarness(this, coordinatorScope = scope)
        harness.resolver.suspendForever = true

        val job = harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        dispatcher.runAll()
        scope.cancel()
        dispatcher.runAll()
        assertTrue(job.isCompleted)

        assertTrue(job.isCancelled)
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    // ------------------------------------------------------------------ FR-085

    @Test
    fun `FR-085 a paused restore does not request focus`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a"), 0, 1_000L), setOf("a"))

        assertEquals(0, harness.focus.requests)
    }

    @Test
    fun `FR-085 stop abandons focus once`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.send(PlaybackService.ActionStop)

        assertEquals(1, harness.focus.abandonments)
    }

    @Test
    fun `FR-085 a user pause clears a pending resume on focus gain`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPauseForTransientFocusLoss)

        harness.send(PlaybackService.ActionPause)
        harness.send(PlaybackService.ActionRestoreFocus)

        assertEquals(PlaybackState.Paused(item("a"), 0L, 180_000L), harness.flows.state.value)
    }

    // ------------------------------------------------------------------ FR-086

    @Test
    fun `FR-086 a failing play prepare closes every created engine`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepare = IllegalStateException("boom")

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        assertTrue(harness.engines.created.isNotEmpty())
        assertTrue(harness.engines.created.all { it.closed })
    }

    // ------------------------------------------------------------------ FR-089

    @Test
    fun `FR-089 two automatic transitions in one tick are delivered in order`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))

        harness.engine.emit(EngineEvent.GaplessAdvanced(item("b")), EngineEvent.GaplessAdvanced(item("c")))
        harness.tick()

        assertEquals(listOf("a" to 0L, "b" to 0L, "c" to 0L), harness.scrobble.starts)
        assertEquals("c", harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Playing(item("c"), 0L, 180_000L), harness.flows.state.value)
        val started = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptStarted>().map { it.value.trackId }
        assertEquals(listOf("a", "b", "c"), started)
    }
}
