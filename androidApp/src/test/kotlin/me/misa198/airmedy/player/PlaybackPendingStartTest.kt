package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A seek during a pending start must not drop the pending OutputStarted wait: after the engine
 * reports output started, Playing is published at the sought position instead of staying Paused.
 */
class PlaybackPendingStartTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
        artworkPath = "/art/$id.jpg",
    )

    @Test
    fun `a seek during a pending resume still reports playing at the seek position`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()

        harness.send(PlaybackService.ActionPause)
        harness.send(PlaybackService.ActionResume)
        harness.send(PlaybackService.ActionSeek, positionMs = 42_000L)

        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()

        assertEquals(PlaybackState.Playing(item("a"), 42_000L, 180_000L), harness.flows.state.value)
    }

    @Test
    fun `an error drained in the starting command fails without restoring the foreground`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false
        harness.engines.onPrepare = {
            harness.engines.created.last().emit(EngineEvent.Error("platform", "flac", RuntimeException("x")))
        }

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as PlaybackState.Failed).trackId)
        assertEquals(0, harness.nowPlaying.foreground)
        assertTrue(harness.nowPlaying.foregroundItems.isEmpty())
    }

    @Test
    fun `a pause during a pending start keeps the Last dot fm start`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPause)

        assertEquals(listOf("a" to 0L), harness.scrobble.starts)
        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Paused)
        assertEquals(item("a"), (state as PlaybackState.Paused).item)
    }

    @Test
    fun `a seek during a pending start is applied`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionSeek, positionMs = 42_000L)

        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()

        assertEquals(PlaybackState.Playing(item("a"), 42_000L, 180_000L), harness.flows.state.value)
    }

    @Test
    fun `a transient focus loss during a pending start resumes on focus gain`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPauseForTransientFocusLoss)
        harness.send(PlaybackService.ActionRestoreFocus)

        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Playing)
        assertEquals(item("a"), (state as PlaybackState.Playing).item)
    }
}
