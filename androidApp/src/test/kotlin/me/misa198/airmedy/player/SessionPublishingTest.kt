package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-087 / FR-013: publishing the media-session queue and saving the session must not load the whole
 * library per command, consecutive seeks must be coalesced, saves must land in command order, and a
 * restored session must keep the saved current track even when other queue tracks are unavailable.
 */
class SessionPublishingTest {

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
    fun `publishing a large queue resolves only the current window`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        val ids = (0 until 1_000).map { "t$it" }

        harness.send(PlaybackService.ActionPlay, trackIds = ids, startIndex = 500)

        // Current track (1) + its preload (1) + the 100-entry window = 102 resolves, never 1_000.
        assertTrue(harness.resolver.resolveCalls <= 102)
        val window = harness.nowPlaying.windows.last()
        assertTrue(window.size <= 100)
        assertEquals(500, window.single { it.item.trackId == "t500" }.index)
        assertTrue(window.all { it.index in 0 until 1_000 })

        val callsBefore = harness.resolver.resolveCalls
        val windowsBefore = harness.nowPlaying.windows.size
        repeat(20) { i ->
            when (i % 3) {
                0 -> harness.send(PlaybackService.ActionPause)
                1 -> harness.send(PlaybackService.ActionResume)
                else -> harness.send(PlaybackService.ActionSeek, positionMs = 1_000L + i)
            }
        }

        assertEquals(callsBefore, harness.resolver.resolveCalls)
        assertEquals(windowsBefore, harness.nowPlaying.windows.size)
    }

    @Test
    fun `next resolves only the newly entered window id`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        val ids = (0 until 1_000).map { "t$it" }
        harness.send(PlaybackService.ActionPlay, trackIds = ids, startIndex = 500)
        val baseline = harness.resolver.resolveCalls

        harness.send(PlaybackService.ActionNext)

        // Exactly one newly-entered window id (t575) plus the new current (t501) and its preload
        // (t502). Bound of 4 tolerates preload/order variation while rejecting a whole-library load.
        assertTrue(harness.resolver.resolveCalls - baseline <= 4)
    }

    @Test
    fun `consecutive seeks are coalesced to the last one`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        val jobs = (0 until 50).map { index ->
            harness.coordinator.dispatch(PlaybackService.ActionSeek, positionMs = 1_000L + index)
        }
        harness.advance()

        assertTrue(harness.engine.seeks.size <= 2)
        assertEquals(1_000L + 49, harness.engine.seeks.last())
        assertEquals(1_000L + 49, harness.engine.position)
        assertTrue(jobs.all { it.isCompleted })
        assertTrue(jobs.none { it.isCancelled })
    }

    @Test
    fun `saves land in command order`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        harness.send(PlaybackService.ActionNext)
        harness.send(PlaybackService.ActionNext)

        val order = listOf("a", "b", "c")
        val positions = harness.sessionStore.saved.map { order.indexOf(it.queue.currentTrackId) }
        assertTrue(positions.all { it >= 0 })
        assertTrue(positions.zipWithNext().all { (previous, next) -> previous <= next })
        assertEquals("c", harness.sessionStore.saved.last().queue.currentTrackId)
    }

    @Test
    fun `restore with a saved index of minus one falls back to the first track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b"), currentIndex = -1, positionMs = 42_000L), setOf("a", "b"))

        assertEquals(listOf("a", "b"), harness.queue.snapshot().activeTrackIds)
        assertEquals("a", harness.queue.snapshot().currentTrackId)
        assertEquals(item("a"), harness.engine.prepareCalls.single().item)
        assertEquals(0L, harness.engine.prepareCalls.single().startPositionMs)
        assertEquals(true, harness.engine.prepareCalls.single().startPaused)
    }

    @Test
    fun `restore falls back to the next available track when the saved current is missing`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b", "c", "d"), currentIndex = 2, positionMs = 30_000L), setOf("a", "b", "d"))

        assertEquals("d", harness.queue.snapshot().currentTrackId)
        assertEquals(item("d"), harness.engine.prepareCalls.single().item)
        assertEquals(0L, harness.engine.prepareCalls.single().startPositionMs)
        assertEquals(true, harness.engine.prepareCalls.single().startPaused)
        assertEquals(PlaybackState.Paused(item("d"), 0L, 180_000L), harness.flows.state.value)
    }

    @Test
    fun `restore falls back to the last available track before the saved current`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b", "c", "d"), currentIndex = 2, positionMs = 30_000L), setOf("a", "b"))

        assertEquals("b", harness.queue.snapshot().currentTrackId)
        assertEquals(item("b"), harness.engine.prepareCalls.single().item)
        assertEquals(0L, harness.engine.prepareCalls.single().startPositionMs)
        assertEquals(true, harness.engine.prepareCalls.single().startPaused)
        assertEquals(PlaybackState.Paused(item("b"), 0L, 180_000L), harness.flows.state.value)
    }
}
