package com.amwolfstein.sistrum.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTrackMetadataFallbackTest {
    @Test
    fun usesUnknownLabelsWhenTrackMetadataIsBlank() {
        assertEquals("Unknown song", trackDisplayTitle("  "))
        assertEquals("Unknown artist", trackDisplayArtists(" "))
    }
}
