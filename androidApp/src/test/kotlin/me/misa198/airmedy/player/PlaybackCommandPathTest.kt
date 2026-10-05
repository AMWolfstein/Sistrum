package me.misa198.airmedy.player

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import me.misa198.airmedy.player.fakes.ReversingDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests first (T018) for FR-081…091; T026/T027 make them pass without modifying them. Every test here
 * is expected to FAIL today with an assertion, never with another exception, a crash or an uncaught
 * coroutine exception.
 */
class PlaybackCommandPathTest {

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

    /** Runs the coordinator on a [ReversingDispatcher] scope with a recording exception handler. */
    private class CommandPathFixture(val testScope: TestScope) {
        val dispatcher = ReversingDispatcher()
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, throwable -> uncaught += throwable } + dispatcher)
        val harness = PlaybackCoordinatorHarness(testScope, coordinatorScope = scope)
    }

    // ------------------------------------------------------------------ FR-081

    @Test
    fun `FR-081 focus loss then gain are handled in arrival order`() = runTest {
        val fx = CommandPathFixture(this)
        fx.harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        fx.dispatcher.runAll()

        fx.harness.coordinator.dispatch(PlaybackService.ActionPauseForTransientFocusLoss)
        fx.harness.coordinator.dispatch(PlaybackService.ActionRestoreFocus)
        fx.dispatcher.runAll()

        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), fx.harness.flows.state.value)
        assertEquals(emptyList<Throwable>(), fx.uncaught)
    }

    @Test
    fun `FR-081 pause then resume are handled in arrival order`() = runTest {
        val fx = CommandPathFixture(this)
        fx.harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        fx.dispatcher.runAll()

        fx.harness.coordinator.dispatch(PlaybackService.ActionPause)
        fx.harness.coordinator.dispatch(PlaybackService.ActionResume)
        fx.dispatcher.runAll()

        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), fx.harness.flows.state.value)
        assertEquals(emptyList<Throwable>(), fx.uncaught)
    }

    @Test
    fun `FR-081 resume then pause are handled in arrival order`() = runTest {
        val fx = CommandPathFixture(this)
        fx.harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        fx.dispatcher.runAll()

        fx.harness.coordinator.dispatch(PlaybackService.ActionResume)
        fx.harness.coordinator.dispatch(PlaybackService.ActionPause)
        fx.dispatcher.runAll()

        assertEquals(PlaybackState.Paused(item("a"), 0L, 180_000L), fx.harness.flows.state.value)
        assertEquals(emptyList<Throwable>(), fx.uncaught)
    }

    // ------------------------------------------------------------------ FR-084

    @Test
    fun `FR-084 a throwing position during a tick surfaces as failed without a crash`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.failPosition = IllegalStateException("position read failed")

        val result = runCatching { harness.tick() }

        assertTrue(result.isSuccess)
        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as? PlaybackState.Failed)?.trackId)
    }

    @Test
    fun `FR-084 a throwing resolver during play fails the command without a crash`() = runTest {
        val fx = CommandPathFixture(this)
        fx.harness.resolver.failWith = IllegalStateException("resolve failed")

        val job = fx.harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        fx.dispatcher.runAll()
        assertTrue(job.isCompleted)

        assertFalse(job.isCancelled)
        assertEquals(emptyList<Throwable>(), fx.uncaught)
        val state = fx.harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as? PlaybackState.Failed)?.trackId)
    }

    // ------------------------------------------------------------------ FR-085

    @Test
    fun `FR-085 a paused track change does not request focus again`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.send(PlaybackService.ActionPause)

        harness.send(PlaybackService.ActionNext)

        assertEquals(1, harness.focus.requests)
    }

    @Test
    fun `FR-085 a failing prepare on play abandons focus`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepare = IllegalStateException("boom")

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        assertEquals(1, harness.focus.abandonments)
    }

    @Test
    fun `FR-085 stop clears a pending resume on focus gain`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPauseForTransientFocusLoss)
        harness.send(PlaybackService.ActionStop)
        val enginesAfterStop = harness.engines.created.size

        harness.send(PlaybackService.ActionRestoreFocus)

        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertEquals(enginesAfterStop, harness.engines.created.size)
    }

    // ------------------------------------------------------------------ FR-086

    @Test
    fun `FR-086 a failing restore prepare closes every created engine`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepare = IllegalStateException("boom")

        harness.restore(session(listOf("a"), 0, 1_000L), setOf("a"))

        assertTrue(harness.engines.created.isNotEmpty())
        assertTrue(harness.engines.created.all { it.closed })
    }

    // ------------------------------------------------------------------ FR-090

    @Test
    fun `FR-090 playing is reported only after output started`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        assertEquals(PlaybackState.Preparing(item("a")), harness.flows.state.value)
        assertTrue(harness.nowPlaying.published.none { it.first.trackId == "a" && it.second == TransportState.Playing })

        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()

        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), harness.flows.state.value)
    }

    @Test
    fun `FR-090 resume reports playing only after output started`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()
        harness.send(PlaybackService.ActionPause)

        harness.send(PlaybackService.ActionResume)

        assertFalse(harness.flows.state.value is PlaybackState.Playing)

        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()

        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), harness.flows.state.value)
    }

    @Test
    fun `FR-090 a start failure before output started never reports playing`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("decode")))

        harness.tick()

        assertTrue(harness.nowPlaying.published.none { it.first.trackId == "a" && it.second == TransportState.Playing })
        assertTrue(harness.nowPlaying.transportStates.contains(TransportState.Error))
    }

    // ------------------------------------------------------------------ FR-091

    @Test
    fun `FR-091 select queue item goes through the command path`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))

        val job = harness.coordinator.selectQueueItem(2L)
        harness.advance()

        assertTrue(job.isCompleted)
        assertEquals("c", harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Playing(item("c"), 0L, 180_000L), harness.flows.state.value)
    }

    @Test
    fun `FR-091 select queue item resolves the index at execution time`() = runTest {
        val fx = CommandPathFixture(this)
        fx.harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        fx.dispatcher.runAll()

        fx.harness.coordinator.dispatch(PlaybackService.ActionReorder, trackIds = listOf("c", "b", "a"))
        val job = fx.harness.coordinator.selectQueueItem(0L)
        fx.dispatcher.runAll()

        assertTrue(job.isCompleted)
        assertEquals("c", fx.harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Playing(item("c"), 0L, 180_000L), fx.harness.flows.state.value)
        assertEquals(emptyList<Throwable>(), fx.uncaught)
    }
}
