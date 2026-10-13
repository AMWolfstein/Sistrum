package com.amwolfstein.sistrum

import com.amwolfstein.sistrum.ui.screens.matchesLibraryTextFilter
import com.amwolfstein.sistrum.ui.screens.normalizedLibrarySearchText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTextSearchTest {
    @Test
    fun foldsDiacriticsWhitespaceAndCaseLikeDesktopSearch() {
        assertTrue(matchesLibraryTextFilter("beyonce", "Beyoncé"))
        assertTrue(matchesLibraryTextFilter("MUSE", "Muse"))
        assertTrue(matchesLibraryTextFilter("", "Anything"))
        assertFalse(matchesLibraryTextFilter("ambient", "Electronic", "Rock"))
        assertTrue(normalizedLibrarySearchText("Đặng") == "dang")
    }

    @Test
    fun matchesAnyConfiguredSearchField() {
        assertTrue(matchesLibraryTextFilter("discovery", "One More Time", "Daft Punk", "Discovery"))
        assertTrue(matchesLibraryTextFilter("daft", "One More Time", "Daft Punk", "Discovery"))
        assertFalse(matchesLibraryTextFilter("jazz", "One More Time", "Daft Punk", "Discovery"))
    }
}
