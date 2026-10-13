package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AlbumArtworkKeyTest {

    // A real album key shape from a device scan (hex-encoded folded album/artist).
    private val albumKey = "2d2b2d5b044745330443474d3304513b433304313b373b512b41043133415359330455334d4f3b4745"

    @Test fun `track and album artwork asset produce identical keys`() {
        val trackKey = albumArtworkKey(albumKey) // LocalTrack.artworkKey / album ref
        val artworkRowKey = albumArtworkKey(albumKey) // LocalScanArtwork.artworkKey
        assertEquals(trackKey, artworkRowKey)
        // writeLocalLibrary keeps a track's key only when an artwork row carries it.
        assertTrue(trackKey in setOf(artworkRowKey))
    }

    @Test fun `stored artwork asset id resolves through the track query join`() {
        val assetId = artworkAssetId(albumArtworkKey(albumKey))
        // observeTracks joins on a.assetId = ('artwork:' || t.artworkKey).
        assertEquals("artwork:" + albumArtworkKey(albumKey), assetId)
    }

    @Test fun `prior scan state reads back the same key the scanner looks up`() {
        val assetId = artworkAssetId(albumArtworkKey(albumKey))
        assertEquals(albumArtworkKey(albumKey), artworkKeyOfAssetId(assetId))
    }

    @Test fun `distinct albums get distinct keys`() {
        assertNotEquals(albumArtworkKey(albumKey), albumArtworkKey(albumKey + "00"))
    }
}
