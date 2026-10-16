package com.amwolfstein.sistrum

import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.searchFtsMatch
import com.amwolfstein.sistrum.ui.screens.searchTracks
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchArabicTest {
    @Test fun arabicTitleAndArtistPrefixesMatchLocalTracksWithDiacritics() {
        val tracks = listOf(
            LibraryTrack("match", "أَلْف ليلة وليلة", "أم كلثوم"),
            LibraryTrack("other", "الأطلال", "أم كلثوم"),
        )
        assertEquals(listOf("match"), searchTracks(tracks, "ألف كلث").map { it.id })
        assertEquals(listOf("match"), searchTracks(tracks, "ليلة", setOf("match")).map { it.id })
        assertEquals("الف* AND كلث*", searchFtsMatch("أَلْف كلث"))
        assertEquals(emptyList<String>(), searchTracks(tracks, "مفقود").map { it.id })
    }
}
