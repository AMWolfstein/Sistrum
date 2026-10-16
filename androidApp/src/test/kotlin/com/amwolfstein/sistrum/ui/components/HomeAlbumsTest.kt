package com.amwolfstein.sistrum.ui.components

import com.amwolfstein.sistrum.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class HomeAlbumsTest {
    @Test fun groupsAlbumsInRecentQueueOrderWithoutReorderingTracks() {
        val tracks = listOf(
            LibraryTrack(id = "new", title = "New", artists = "A", albumId = "album-a"),
            LibraryTrack(id = "other", title = "Other", artists = "B", albumId = "album-b"),
            LibraryTrack(id = "older", title = "Older", artists = "A", albumId = "album-a"),
        )
        assertEquals(listOf(listOf("new", "older"), listOf("other")),
            recentHomeAlbums(tracks).map { album -> album.map { it.id } })
        assertEquals(listOf("new", "other", "older"), tracks.map { it.id })
    }

    @Test fun emptyAndUnknownAlbumsDoNotMergeUnrelatedTracks() {
        assertEquals(emptyList<List<LibraryTrack>>(), recentHomeAlbums(emptyList()))
        val tracks = listOf("one", "two").map { LibraryTrack(id = it, title = it, artists = "A") }
        assertEquals(listOf(listOf(tracks[0]), listOf(tracks[1])), recentHomeAlbums(tracks))
    }

    @Test fun homeReferencesOnlyLocalArtworkAndNoNetworkClasses() {
        val source = File("src/main/kotlin/com/amwolfstein/sistrum/ui/components/HomeContent.kt").readText()
        for (forbidden in listOf("HttpFetcher", "okhttp", "retrofit", "java.net", "coil", "https://", "http://")) {
            assertFalse("Home must remain local: $forbidden", source.contains(forbidden, ignoreCase = true))
        }
    }
}
