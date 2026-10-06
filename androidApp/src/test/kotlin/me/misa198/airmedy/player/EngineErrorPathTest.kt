package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-064: a provider decode failure shows the normal playback error, is logged with the provider
 * name and the file's format, and skips to the next track. No retry on another engine.
 */
class EngineErrorPathTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
        artworkPath = "/art/$id.jpg",
    )

    @Test
    fun `a decode failure is logged and skips to the next track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        assertEquals(1, harness.engines.created.size)

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("bad frame")))
        harness.tick()

        val line = harness.log.lines.lastOrNull { it.contains("provider=platform") }
        assertNotNull(line)
        assertTrue(line!!.contains("format=flac"))
        assertTrue(line.contains("id=a"))
        assertTrue(harness.nowPlaying.transportStates.contains(TransportState.Error))
        assertEquals("b", harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Playing(item("b"), 0L, 180_000L), harness.flows.state.value)
        // Exactly one new engine: the successor "b"; "a" was never retried.
        assertEquals(2, harness.engines.created.size)
    }

    @Test
    fun `a decode failure on the last track fails`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("bad frame")))
        harness.tick()

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as PlaybackState.Failed).trackId)
        assertEquals(1, harness.engines.created.size)
    }

    @Test
    fun `a decode failure before output started skips without reporting playing`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("bad frame")))
        harness.tick()

        assertTrue(harness.nowPlaying.published.none { it.first.trackId == "a" && it.second == TransportState.Playing })
        assertEquals("b", harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Preparing(item("b")), harness.flows.state.value)
        assertEquals(2, harness.engines.created.size)
    }

    @Test
    fun `a decode failure under repeat one fails without looping`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.One)

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("bad frame")))
        harness.tick()

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as PlaybackState.Failed).trackId)
        assertEquals(1, harness.engines.created.size)
    }

    @Test
    fun `repeat all fails once every track has decoded once`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.All)
        harness.engines.onPrepare = {
            harness.engines.created.last().emit(EngineEvent.Error("platform", "flac", RuntimeException("bad")))
        }

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertTrue(harness.engines.created.size <= 4)
    }

    @Test
    fun `a decode failure on a restored paused session stays paused on the next track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.restore(
            PlaybackSession(
                queue = PlaybackQueueSnapshot(
                    originalTrackIds = listOf("a", "b"),
                    activeTrackIds = listOf("a", "b"),
                    currentIndex = 0,
                ),
                positionMs = 0L,
            ),
            setOf("a", "b"),
        )
        assertEquals(1, harness.engines.created.size)

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("bad frame")))
        harness.tick()

        assertEquals(PlaybackState.Paused(item("b"), 0L, 180_000L), harness.flows.state.value)
        assertTrue(harness.engine.prepareCalls.last().startPaused)
        assertTrue(harness.nowPlaying.published.none { it.second == TransportState.Playing })
        assertEquals(2, harness.engines.created.size)
    }

    @Test
    fun `a long queue caps synchronous decode skips`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.onPrepare = {
            harness.engines.created.last().emit(EngineEvent.Error("platform", "flac", RuntimeException("bad")))
        }
        val tracks = (0 until 50).map { "t$it" }

        harness.send(PlaybackService.ActionPlay, trackIds = tracks)

        assertTrue(harness.flows.state.value is PlaybackState.Failed)
        assertTrue(harness.engines.created.size <= 21)
    }

    @Test
    fun `a successful output start resets the consecutive decode failure guard`() = runTest {
        // Without the reset the third failure would exceed the queue size and fail; with it the
        // guard keeps skipping, which is the observable difference asserted here.
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.autoOutputStarted = false
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.All)

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("first")))
        harness.tick()
        assertEquals("b", harness.queue.snapshot().currentTrackId)

        harness.engine.emit(EngineEvent.OutputStarted)
        harness.tick()
        assertEquals(PlaybackState.Playing(item("b"), 0L, 180_000L), harness.flows.state.value)

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("second")))
        harness.tick()
        assertEquals("a", harness.queue.snapshot().currentTrackId)

        harness.engine.emit(EngineEvent.Error("platform", "flac", RuntimeException("third")))
        harness.tick()

        assertFalse(harness.flows.state.value is PlaybackState.Failed)
        assertEquals("b", harness.queue.snapshot().currentTrackId)
    }
}
