package me.misa198.airmedy.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorShutdownTest {

    @Test
    fun `shutdown cancels the in-flight command and returns`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.resolver.suspendForever = true

        val job = harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.advance()
        assertFalse(job.isCompleted)

        harness.coordinator.shutdown()

        assertTrue(job.isCancelled)
    }

    @Test
    fun `commands after shutdown are refused`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.coordinator.shutdown()

        val job = harness.coordinator.dispatch(PlaybackService.ActionPause)
        assertTrue(job.isCancelled)

        val thrown = runCatching { harness.coordinator.withCommandLock { } }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `queued commands are cancelled by shutdown`() = runTest {
        val harness = PlaybackCoordinatorHarness(this, markRestoredOnInit = false)

        val first = harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        val second = harness.coordinator.dispatch(PlaybackService.ActionPause)

        harness.coordinator.shutdown()

        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
    }

    @Test
    fun `shutdown is idempotent`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.resolver.suspendForever = true
        harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.advance()

        harness.coordinator.shutdown()
        harness.coordinator.shutdown()
    }

    @Test
    fun `onIdle fires once after the last queued command not between commands`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        var onIdleCalls = 0
        harness.coordinator.onIdle = { onIdleCalls++ }

        harness.coordinator.dispatch(PlaybackService.ActionPause)
        harness.coordinator.dispatch(PlaybackService.ActionPause)
        harness.coordinator.dispatch(PlaybackService.ActionPause)
        harness.advance()

        assertEquals(1, onIdleCalls)
        assertTrue(harness.coordinator.isIdle())
    }

    @Test
    fun `isIdle is false while commands are held before restore`() = runTest {
        val harness = PlaybackCoordinatorHarness(this, markRestoredOnInit = false)

        harness.coordinator.dispatch(PlaybackService.ActionPause)
        assertFalse(harness.coordinator.isIdle())

        harness.coordinator.markRestored()
        harness.advance()

        assertTrue(harness.coordinator.isIdle())
    }

    @Test
    fun `isIdle is false until restored even with an empty queue`() = runTest {
        val harness = PlaybackCoordinatorHarness(this, markRestoredOnInit = false)

        assertFalse(harness.coordinator.isIdle())

        harness.coordinator.markRestored()
        harness.advance()

        assertTrue(harness.coordinator.isIdle())
    }

    @Test
    fun `isRestored reflects markRestored`() = runTest {
        val harness = PlaybackCoordinatorHarness(this, markRestoredOnInit = false)
        assertFalse(harness.coordinator.isRestored())

        harness.coordinator.markRestored()

        assertTrue(harness.coordinator.isRestored())
    }
}
