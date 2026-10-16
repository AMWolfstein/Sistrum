package com.amwolfstein.sistrum.ui.screens

import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDetailsViewModelTest {
    @Test
    fun detailsSortAlbumsByCanonicalSortTitleAndTracksByDiscThenTrackNumber() {
        val state = ArtistDetailsUiState(
            allArtists = listOf(LibraryArtist("artist", "Artist")),
            allAlbums = listOf(
                LibraryAlbum("z", "Alpha", "Artist", sortTitle = "Zulu", sortArtist = "Zulu Artist"),
                LibraryAlbum("a", "Zulu", "Artist", sortTitle = "Alpha", sortArtist = "Alpha Artist"),
            ),
            tracks = listOf(
                track("z-two", "z", disc = 1, number = 2, order = 2),
                track("a-missing", "a", order = 0),
                track("z-one", "z", disc = 1, number = 1, order = 1),
                track("a-one", "a", disc = 1, number = 1, order = 1),
                LibraryTrack("other", "Other", "Other", albumId = "a", metadataJson = metadata("other")),
            ),
        )

        val details = artistDetailsUiStateFor(state, "artist")

        assertEquals(listOf("a", "z"), details.albums.map { it.id })
        assertEquals(listOf("a-one", "a-missing", "z-one", "z-two"), details.tracks.map { it.id })
    }

    @Test
    fun detailsUseCanonicalArtistIdsRatherThanDisplayNames() {
        val state = ArtistDetailsUiState(
            allArtists = listOf(LibraryArtist("artist", "Shared Name")),
            allAlbums = listOf(LibraryAlbum("album", "Album")),
            tracks = listOf(
                LibraryTrack("match", "Match", "Shared Name", albumId = "album", metadataJson = metadata("artist")),
                LibraryTrack("different", "Different", "Shared Name", albumId = "album", metadataJson = metadata("another")),
            ),
        )

        assertEquals(listOf("match"), artistDetailsUiStateFor(state, "artist").tracks.map { it.id })
    }

    @Test
    fun releaseSectionsUseSevenSongBoundaryAndOtherAlbumArtists() {
        val details = ArtistDetailsUiState(
            artist = LibraryArtist("artist", "Artist"),
            albums = listOf(
                LibraryAlbum("lp", "LP", "Artist", year = 2020),
                LibraryAlbum("ep", "EP", " artist ", year = 2024),
                LibraryAlbum("single", "Single", "Artist", year = 2025),
                LibraryAlbum("comp", "Compilation", "Various Artists", year = 2023),
                LibraryAlbum("empty", "Empty", "Artist"),
            ),
            albumSongCounts = mapOf("lp" to 7, "ep" to 6, "single" to 1, "comp" to 12, "empty" to 0),
        )
        val sections = artistReleaseSections(details)
        assertEquals("single", sections.latest?.id)
        assertEquals(listOf("lp"), sections.albums.map { it.id })
        assertEquals(listOf("ep", "single"), sections.singles.map { it.id })
        assertEquals(listOf("comp"), sections.compilations.map { it.id })
        assertEquals(12, sections.songCounts["comp"])
    }

    @Test
    fun compilationCountsIncludeOtherArtistsWithoutChangingArtistQueue() {
        val state = ArtistDetailsUiState(
            allArtists = listOf(LibraryArtist("artist", "Artist")),
            allAlbums = listOf(LibraryAlbum("album", "Compilation", "Various Artists")),
            tracks = listOf(track("own", "album", order = 1)) + List(6) {
                LibraryTrack("other-$it", "Other", "Other", albumId = "album", metadataJson = metadata("other"))
            },
        )
        val details = artistDetailsUiStateFor(state, "artist")
        assertEquals(listOf("own"), details.tracks.map { it.id })
        assertEquals(7, artistReleaseSections(details).songCounts["album"])
    }

    @Test
    fun popularSortsByPlayCountKeepingCanonicalOrderForTies() {
        val tracks = listOf(0, 20, 5, 20, 1, 3).mapIndexed { index, count ->
            LibraryTrack("$index", "Song $index", "Artist", playCount = count)
        }
        assertEquals(listOf("1", "3", "2", "5", "4", "0"),
            artistReleaseSections(ArtistDetailsUiState(tracks = tracks)).popular.map { it.id })
    }

    @Test
    fun emptyLibraryHasNoReleaseSectionsAndUnknownYearsUseCanonicalOrder() {
        val empty = artistReleaseSections(ArtistDetailsUiState())
        assertEquals(null, empty.latest)
        assertEquals(emptyList<LibraryAlbum>(), empty.albums + empty.singles + empty.compilations)
        assertEquals(emptyList<LibraryTrack>(), empty.popular)
        val album = LibraryAlbum("first", "First")
        assertEquals(album, artistReleaseSections(ArtistDetailsUiState(albums = listOf(album, LibraryAlbum("second", "Second")))).latest)
    }

    private fun track(id: String, albumId: String, disc: Int = 0, number: Int = 0, order: Int): LibraryTrack =
        LibraryTrack(id, id, "Artist", albumId = albumId, discNumber = disc, trackNumber = number, syncOrder = order, metadataJson = metadata("artist"))

    private fun metadata(artistId: String): String = "{\"artists\":[{\"id\":\"$artistId\"}]}"
}
