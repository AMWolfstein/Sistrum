package com.amwolfstein.sistrum.ui.screens

import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.metadataObject
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.ui.components.collectionContextShowsAddToQueue
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailsViewModelTest {
    @Test
    fun detailsUseNumbersBeforeStableManifestFallback() {
        val state = AlbumDetailsUiState(
            albums = listOf(LibraryAlbum("album", "Album", "Artist")),
            tracks = listOf(
                LibraryTrack("missing", "Missing", "Artist", "Album", albumId = "album", syncOrder = 0),
                LibraryTrack("second", "Second", "Artist", "Album", albumId = "album", discNumber = 1, trackNumber = 2, syncOrder = 2),
                LibraryTrack("first", "First", "Artist", "Album", albumId = "album", discNumber = 1, trackNumber = 1, syncOrder = 1),
                LibraryTrack("other", "Other", "Artist", "Other", albumId = "other", trackNumber = 1),
            ),
        )

        val details = albumDetailsUiStateFor(state, "album")

        assertEquals("Album", details.album?.title)
        assertEquals(listOf("first", "second", "missing"), details.tracks.map { it.id })
    }

    @Test
    fun canonicalMetadataRemainsAvailableOutsideTheIndexedProjection() {
        val track = LibraryTrack("id", "Title", "Artist", metadataJson = "{\"isrc\":\"US-ABC-12-34567\"}")

        assertEquals("US-ABC-12-34567", track.metadataObject()?.get("isrc")?.toString()?.trim('"'))
    }

    @Test
    fun detailsReadAlbumIdFromCanonicalMetadataForPreMigrationTracks() {
        val state = AlbumDetailsUiState(
            albums = listOf(LibraryAlbum("album", "Album", "Artist")),
            tracks = listOf(LibraryTrack("legacy", "Legacy", "Artist", metadataJson = "{\"album\":{\"id\":\"album\"}}")),
        )

        assertEquals(listOf("legacy"), albumDetailsUiStateFor(state, "album").tracks.map { it.id })
    }

    @Test
    fun tappedAlbumTrackBuildsTheAlbumQueueAtItsPosition() {
        val tracks = listOf(
            LibraryTrack("one", "One", "Artist"),
            LibraryTrack("two", "Two", "Artist"),
        )

        val request = requireNotNull(albumPlaybackRequestFor(tracks, "two"))

        assertEquals(listOf("one", "two"), request.trackIds)
        assertEquals(1, request.startIndex)
    }

    /** Real millisecond durations from a device scan (3:36 + 3:00.5) total 6:36, not ~110 hours. */
    @Test
    fun totalDurationReadsMillisecondMetadata() {
        val tracks = listOf(
            LibraryTrack("one", "One", "Artist", metadataJson = """{"duration":216127}"""),
            LibraryTrack("two", "Two", "Artist", metadataJson = """{"duration":180500}"""),
        )
        assertEquals(396L, albumTotalDurationSeconds(tracks))
    }

    @Test
    fun sumsNonNegativeTrackDurationsAndFormatsLikeDesktop() {
        val tracks = listOf(
            LibraryTrack("one", "One", "Artist", metadataJson = """{"duration":61000}"""),
            LibraryTrack("two", "Two", "Artist", metadataJson = """{"duration":3660000}"""),
            LibraryTrack("invalid", "Invalid", "Artist", metadataJson = """{"duration":-1}"""),
        )
        val day = { value: Long -> "$value d" }
        val hour = { value: Long -> "$value hr" }
        val minute = { value: Long -> "$value min" }
        val second = { value: Long -> "$value s" }

        assertEquals(3721L, albumTotalDurationSeconds(tracks))
        assertEquals("1 hr 1 min", formatAlbumTotalDuration(3660L, day, hour, minute, second))
        assertEquals("2 min", formatAlbumTotalDuration(120L, day, hour, minute, second))
        assertEquals("59 s", formatAlbumTotalDuration(59L, day, hour, minute, second))
    }

    @Test
    fun collectionQueueActionStaysAvailableUntilEveryTrackIsAlreadyQueued() {
        assertEquals(true, collectionContextShowsAddToQueue(emptyList(), PlaybackQueueSnapshot()))
        assertEquals(true, collectionContextShowsAddToQueue(listOf("one", "two"), PlaybackQueueSnapshot(activeTrackIds = listOf("one"))))
        assertEquals(false, collectionContextShowsAddToQueue(listOf("one", "two"), PlaybackQueueSnapshot(activeTrackIds = listOf("one", "two"))))
    }
}
