package com.amwolfstein.sistrum.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsMatchingTest {
    @Test fun kugouTieBreaksOnProviderScore() {
        val winner = bestLyricsCandidate(listOf(LyricsCandidate("Song", "Artist", 180.0, 1), LyricsCandidate("Song", "Artist", 180.0, 2)), "song", "artist", 180)
        assertEquals(2, winner?.providerScore)
    }

    @Test fun candidatesMoreThanFiveSecondsOffAreRejected() {
        assertNull(bestLyricsCandidate(listOf(LyricsCandidate("Song", "Artist", 190.0)), "song", "artist", 180))
        assertEquals(185.0, bestLyricsCandidate(listOf(LyricsCandidate("Song", "Artist", 185.0)), "song", "artist", 180)?.durationSeconds)
    }

    @Test fun unknownTrackDurationSkipsTheDurationCheck() {
        // Duration 0 means unreadable, not a real length: every candidate used to be rejected.
        val candidates = listOf(LyricsCandidate("Other Song", "Artist", 200.0), LyricsCandidate("Song", "Artist", 241.0))
        assertEquals("Song", bestLyricsCandidate(candidates, "song", "artist", 0)?.title)
    }

    @Test fun unknownTrackDurationStillRequiresATitleMatchAndPrefersTheArtistMatch() {
        assertNull(bestLyricsCandidate(listOf(LyricsCandidate("Completely Different", "Artist", 180.0)), "song", "artist", 0))
        val winner = bestLyricsCandidate(listOf(LyricsCandidate("Song", "Someone Else", 180.0), LyricsCandidate("Song", "Artist", 300.0)), "song", "artist", 0)
        assertEquals("Artist", winner?.artist)
    }
}
