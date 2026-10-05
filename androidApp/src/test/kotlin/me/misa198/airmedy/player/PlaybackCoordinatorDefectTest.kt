package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests first (T018) for the coordinator defects found at T016 (owner decision 2026-10-06): duck kept
 * across a manual track change (FR-022/FR-035, fixed in T027), restore keeps the saved track when an
 * earlier track is missing (FR-013, fixed in T030) and output recovery does not restart Last.fm
 * (FR-015, fixed in T027). Every test here is expected to FAIL today with an assertion.
 */
class PlaybackCoordinatorDefectTest {

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

    @Test
    fun `FR-022 duck is kept across a manual track change`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.send(PlaybackService.ActionDuck)

        harness.send(PlaybackService.ActionNext)

        assertEquals(listOf(0.2f), harness.engine.focusGains)
    }

    @Test
    fun `FR-013 restore keeps the saved track when an earlier track is missing`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b", "c", "d"), currentIndex = 2, positionMs = 30_000L), setOf("a", "c", "d"))

        assertEquals("c", harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Paused(item("c"), 30_000L, 180_000L), harness.flows.state.value)
        assertEquals(item("c"), harness.engine.prepareCalls.single().item)
        assertEquals(30_000L, harness.engine.prepareCalls.single().startPositionMs)
        assertEquals(true, harness.engine.prepareCalls.single().startPaused)
    }

    @Test
    fun `FR-015 output recovery does not restart Last dot fm`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.engine.position = 5_000L

        harness.engine.emit(EngineEvent.OutputDisconnected)
        harness.tick()

        assertEquals(listOf("a" to 0L), harness.scrobble.starts)
    }
}
