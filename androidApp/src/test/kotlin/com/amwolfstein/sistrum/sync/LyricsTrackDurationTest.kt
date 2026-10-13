package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The "duration" metadata is LocalTrack.durationMillis. Lyrics lookups previously passed it
 * through as seconds, so LRCLIB rejected every exact match ("duration: must be between 1 and
 * 3600") and Kugou was sent milliseconds x 1000.
 */
class LyricsTrackDurationTest {

    private fun track(metadata: String) =
        LibraryTrack("t", "Chandelier", "Sia", album = "1000 Forms of Fear", metadataJson = metadata, audioPath = "/music/c.opus")

    @Test fun `lyrics duration is whole seconds from millisecond metadata`() {
        // Real value from a device scan: 216127 ms is LRCLIB's 216 s.
        assertEquals(216, track("""{"duration":216127}""").toLyricsTrack().duration)
    }

    @Test fun `lyrics duration rounds to the nearest second`() {
        assertEquals(217, track("""{"duration":216600}""").toLyricsTrack().duration)
    }

    @Test fun `missing or non-positive duration is zero, which providers omit`() {
        assertEquals(0, track("""{}""").toLyricsTrack().duration)
        assertEquals(0, track("""{"duration":-1}""").toLyricsTrack().duration)
    }

    @Test fun `durationMillis reads the raw millisecond value`() {
        assertEquals(216127L, track("""{"duration":216127}""").durationMillis())
        assertNull(track("""{"duration":0}""").durationMillis())
    }
}
