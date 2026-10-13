package com.amwolfstein.sistrum.ui.screens

import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDetailsViewModelTest {
    private val first = LibraryTrack("first", "First", "Artist", metadataJson = """{"duration":61000}""")
    private val second = LibraryTrack("second", "Second", "Artist", metadataJson = """{"duration":3660000}""")

    @Test
    fun resolvesTracksInPlaylistOrderAndSkipsUnavailableIds() {
        val state = PlaylistDetailsUiState(
            playlists = listOf(LibraryPlaylist("mix", "Mix", listOf("second", "missing", "first"), "{}")),
            allTracks = listOf(first, second),
        )

        val result = playlistDetailsUiStateFor(state, "mix")

        assertEquals(listOf("second", "first"), result.tracks.map { it.id })
        assertEquals(3721L, playlistTotalDurationSeconds(result.tracks))
    }

    /** "duration" is LocalTrack.durationMillis; summing it as seconds showed totals ~1000x too long. */
    @Test
    fun totalDurationConvertsMillisecondsOnceAfterSumming() {
        val halfSecondTracks = List(3) { LibraryTrack("t$it", "T", "Artist", metadataJson = """{"duration":1500}""") }
        // 4500 ms -> 4 s; converting per track would truncate each to 1 s and give 3.
        assertEquals(4L, playlistTotalDurationSeconds(halfSecondTracks))
    }

    @Test
    fun derivesFavoritesTracksFromSyncedTrackMetadata() {
        val favorite = LibraryTrack("favorite", "Favorite", "Artist", metadataJson = """{"is_favorite":true}""")
        val notFavorite = LibraryTrack("other", "Other", "Artist", metadataJson = """{"is_favorite":false}""")
        val result = playlistDetailsUiStateFor(
            PlaylistDetailsUiState(allTracks = listOf(favorite, notFavorite)),
            FavoritesPlaylistId,
        )

        assertEquals(FavoritesPlaylistId, result.playlist?.id)
        assertEquals(listOf("favorite"), result.tracks.map { it.id })
    }

    @Test
    fun returnsEmptyStateForUnknownPlaylist() {
        assertNull(playlistDetailsUiStateFor(PlaylistDetailsUiState(), "missing").playlist)
    }

    @Test
    fun formatsDurationLikeDesktop() {
        val day = { value: Long -> "$value d" }
        val hour = { value: Long -> "$value hr" }
        val minute = { value: Long -> "$value min" }
        val second = { value: Long -> "$value s" }

        assertEquals("1 hr 1 min", formatPlaylistTotalDuration(3660L, day, hour, minute, second))
        assertEquals("2 min", formatPlaylistTotalDuration(120L, day, hour, minute, second))
        assertEquals("59 s", formatPlaylistTotalDuration(59L, day, hour, minute, second))
        assertEquals("52 d 3 hr", formatPlaylistTotalDuration(52L * 86_400L + 3L * 3_600L + 30L * 60L, day, hour, minute, second))
    }
}
