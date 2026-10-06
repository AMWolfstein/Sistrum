package me.misa198.airmedy.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T035a: the position-only store is merged onto the full session only when it belongs to the
 * session's current track; the full-session JSON encoding must keep round-tripping unchanged.
 */
class PlaybackSessionStoreTest {

    private fun session(
        trackIds: List<String> = listOf("a", "b"),
        currentIndex: Int = 0,
        positionMs: Long = 1_000L,
    ) = PlaybackSession(
        queue = PlaybackQueueSnapshot(
            originalTrackIds = trackIds,
            activeTrackIds = trackIds,
            currentIndex = currentIndex,
        ),
        positionMs = positionMs,
    )

    @Test
    fun `mergeSavedPosition applies a position for the session current track`() {
        val base = session(trackIds = listOf("a", "b"), currentIndex = 0, positionMs = 1_000L)

        val merged = mergeSavedPosition(base, SavedPosition("a", 42_000L))

        assertEquals(42_000L, merged.positionMs)
        assertEquals(base.queue, merged.queue)
    }

    @Test
    fun `mergeSavedPosition ignores a position for another track`() {
        val base = session(trackIds = listOf("a", "b"), currentIndex = 0, positionMs = 1_000L)

        assertEquals(base, mergeSavedPosition(base, SavedPosition("b", 42_000L)))
    }

    @Test
    fun `mergeSavedPosition ignores null and negative positions`() {
        val base = session(trackIds = listOf("a", "b"), currentIndex = 0, positionMs = 1_000L)

        assertEquals(base, mergeSavedPosition(base, null))
        assertEquals(base, mergeSavedPosition(base, SavedPosition("a", -1L)))
    }

    @Test
    fun `session encoding round-trips queue and position`() {
        val original = session(trackIds = listOf("x", "y", "z"), currentIndex = 2, positionMs = 7_500L)

        assertEquals(original, decodePlaybackSession(encodePlaybackSession(original)))
    }
}
