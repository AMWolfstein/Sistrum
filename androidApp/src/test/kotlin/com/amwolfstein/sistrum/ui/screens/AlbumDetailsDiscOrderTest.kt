package com.amwolfstein.sistrum.ui.screens

import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailsDiscOrderTest {
    @Test
    fun discThenTrackOrderSurvivesRepeatedTrackNumbersAndMissingTags() {
        val tracks = listOf(
            LibraryTrack("untagged", "Untagged", "Artist", albumId = "album", syncOrder = 0),
            LibraryTrack("disc2", "Disc two", "Artist", albumId = "album", discNumber = 2, trackNumber = 1),
            LibraryTrack("disc1b", "Disc one B", "Artist", albumId = "album", discNumber = 1, trackNumber = 2),
            LibraryTrack("disc1a", "Disc one A", "Artist", albumId = "album", discNumber = 1, trackNumber = 1),
        )
        val details = albumDetailsUiStateFor(
            AlbumDetailsUiState(albums = listOf(LibraryAlbum("album", "Album", "Artist")), tracks = tracks), "album",
        )
        assertEquals(listOf("disc1a", "disc1b", "disc2", "untagged"), details.tracks.map { it.id })
        val request = requireNotNull(albumPlaybackRequestFor(details.tracks, "disc2"))
        assertEquals(listOf("disc1a", "disc1b", "disc2", "untagged"), request.trackIds)
        assertEquals(2, request.startIndex)
    }
}
