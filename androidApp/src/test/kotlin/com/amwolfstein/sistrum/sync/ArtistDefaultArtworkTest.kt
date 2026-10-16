package com.amwolfstein.sistrum.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistDefaultArtworkTest {
    private val paths = mapOf("old" to "artwork/old.jpg", "new" to "artwork/new.jpg")

    @Test
    fun newestYearWinsRegardlessOfInputOrderAndDateAdded() {
        val old = row("old", year = 2000, added = "2026-10-16T00:00:00Z")
        val newest = row("new", year = 2025, added = "2026-01-01T00:00:00Z")
        for (tracks in listOf(listOf(old, newest), listOf(newest, old))) {
            val artist = libraryArtistsFrom(tracks, paths).single()
            assertEquals("artwork/new.jpg", artist.artworkPath)
            assertFalse(artist.hasCustomArtwork)
        }
    }

    @Test
    fun missingYearsUseMostRecentlyAddedTrack() {
        assertEquals("artwork/new.jpg", libraryArtistsFrom(listOf(
            row("new", added = "2026-10-16T00:00:00Z"),
            row("old", added = "2026-01-01T00:00:00Z"),
        ), paths).single().artworkPath)
    }

    @Test
    fun knownYearRanksAboveMissingYear() {
        assertEquals("artwork/old.jpg", libraryArtistsFrom(listOf(
            row("new", added = "2026-10-16T00:00:00Z"),
            row("old", year = 2000, added = "2026-01-01T00:00:00Z"),
        ), paths).single().artworkPath)
    }

    @Test
    fun equalYearsUseMostRecentlyAddedTrack() {
        assertEquals("artwork/new.jpg", libraryArtistsFrom(listOf(
            row("new", year = 2025, added = "2026-10-16T00:00:00Z"),
            row("old", year = 2025, added = "2026-01-01T00:00:00Z"),
        ), paths).single().artworkPath)
    }

    @Test
    fun equalYearAndDateUseHighestTrackIdRegardlessOfInputOrder() {
        val a = row("a", cover = "old", year = 2025)
        val z = row("z", cover = "new", year = 2025)
        for (tracks in listOf(listOf(a, z), listOf(z, a))) {
            assertEquals("artwork/new.jpg", libraryArtistsFrom(tracks, paths).single().artworkPath)
        }
    }

    @Test
    fun tracksWithoutResolvedArtworkAreSkipped() {
        assertEquals("artwork/old.jpg", libraryArtistsFrom(listOf(
            row("missing", year = 2026), row("old", year = 2000),
        ), paths).single().artworkPath)
    }

    @Test
    fun noArtworkAnywhereUsesPlaceholder() {
        assertNull(libraryArtistsFrom(listOf(row("missing")), paths).single().artworkPath)
        assertEquals(emptyList<LibraryArtist>(), libraryArtistsFrom(emptyList(), paths))
    }

    @Test
    fun manualImageWinsOverManifestAndNewestCoverAndKeepsCustomFlag() {
        val track = row("new", year = 2025).copy(rawJson = row("new", year = 2025).rawJson
            .replace("\"name\":\"Artist A\"", "\"name\":\"Artist A\",\"artwork_key\":\"old\""))
        val artist = libraryArtistsFrom(listOf(track), paths, mapOf("a" to "artist-artwork/manual.jpg")).single()
        assertEquals("artist-artwork/manual.jpg", artist.artworkPath)
        assertTrue(artist.hasCustomArtwork)
        val removed = libraryArtistsFrom(listOf(row("new", year = 2025)), paths).single()
        assertEquals("artwork/new.jpg", removed.artworkPath)
        assertFalse(removed.hasCustomArtwork)
    }

    @Test
    fun collaborationCountsUnderEachEntityWithoutSplittingNames() {
        val artists = libraryArtistsFrom(listOf(
            row("old", year = 2000),
            row("new", year = 2025, artists = """{"id":"a","name":"Artist A"},{"id":"b","name":"B, C & D"}"""),
        ), paths)
        assertEquals(listOf("a", "b"), artists.map { it.id })
        assertEquals(listOf("Artist A", "B, C & D"), artists.map { it.name })
        assertEquals(listOf("artwork/new.jpg", "artwork/new.jpg"), artists.map { it.artworkPath })
    }

    @Test
    fun usesArtworkResolvedByAlbumFlowEvenWhenNewestRowHasNoKey() {
        val newest = row("new", year = 2025, cover = "missing").copy(
            rawJson = row("new", year = 2025, cover = "missing").rawJson.replace("album-new", "album-old"),
        )
        assertEquals("artwork/old.jpg", libraryArtistsFrom(listOf(
            row("old", year = 2000), newest,
        ), paths).single().artworkPath)
    }

    @Test
    fun usesExistingTrackArtworkPathWhenAlbumArtworkIsUnavailable() {
        assertEquals("artwork/track.jpg", libraryArtistsFrom(listOf(
            row("missing").copy(artworkPath = "artwork/track.jpg"),
        ), paths).single().artworkPath)
    }

    private fun row(
        id: String,
        year: Int? = null,
        added: String = "",
        cover: String = id,
        artists: String = """{"id":"a","name":"Artist A"}""",
    ) = LibraryTrackRow(
        id = id, title = id, artists = "", album = "", artworkKey = cover,
        playCount = 0, createdAt = added, artworkPath = null, audioPath = null,
        rawJson = """{"year":${year ?: "null"},"artists":[$artists],"album":{"id":"album-$id","title":"Album $id","artwork_key":"$cover"}}""",
    )
}
