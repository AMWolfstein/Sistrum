package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * MediaStore reports a file without an artist tag as "<unknown>", which the app used to
 * show verbatim and even list as an artist.
 */
class MediaStoreTagValueTest {

    @Test fun `MediaStore's unknown placeholder counts as a missing tag`() {
        assertNull(mediaStoreTagValue("<unknown>"))
        assertNull(mediaStoreTagValue("  <unknown> "))
    }

    @Test fun `blank and absent values are missing`() {
        assertNull(mediaStoreTagValue(null))
        assertNull(mediaStoreTagValue(""))
        assertNull(mediaStoreTagValue("   "))
    }

    @Test fun `real values are kept, trimmed`() {
        assertEquals("Eminem", mediaStoreTagValue(" Eminem "))
        assertEquals("<unknown> remix", mediaStoreTagValue("<unknown> remix"))
    }

    @Test fun `a missing artist then shows the app's own fallback`() {
        assertEquals("Unknown artist", trackDisplayArtists(mediaStoreTagValue("<unknown>") ?: ""))
    }
}
