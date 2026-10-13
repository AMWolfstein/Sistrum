package com.amwolfstein.sistrum.player

import java.io.IOException
import kotlinx.coroutines.test.runTest
import com.amwolfstein.sistrum.player.engine.EngineDecodeException
import com.amwolfstein.sistrum.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Existing prepare failure behavior that the FR-064 amendment must preserve. */
class PlaybackCoordinatorPrepareFailureGuardTest {

    @Test
    fun `a plain prepare exception fails without skipping or recording a decode failure`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepare = IllegalStateException("boom")

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as PlaybackState.Failed).trackId)
        assertEquals("a", harness.queue.snapshot().currentTrackId)
        assertEquals(1, harness.engines.created.size)
        assertTrue(harness.decodeFailures.entries.isEmpty())
        assertTrue(harness.log.lines.none { it.contains("Decode failure") })
    }

    @Test
    fun `an engine whose prepare throws a plain exception is closed`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepare = IllegalStateException("boom")

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        assertEquals(1, harness.engines.created.size)
        val failedEngine = harness.engines.created.first()
        assertEquals(listOf("a"), failedEngine.prepareAttempts.map { it.trackId })
        assertTrue(failedEngine.closed)
        assertEquals("close", failedEngine.calls.last())
    }

    @Test
    fun `an engine whose prepare throws a decode exception is closed`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepareFor = { item ->
            if (item.trackId == "a") EngineDecodeException("none", "wma", IOException("unrecognized")) else null
        }

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        assertTrue(harness.engines.created.isNotEmpty())
        val failedEngine = harness.engines.created.first()
        assertEquals(listOf("a"), failedEngine.prepareAttempts.map { it.trackId })
        assertTrue(failedEngine.closed)
        assertEquals("close", failedEngine.calls.last())
    }
}
