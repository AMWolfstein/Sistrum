package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MediaProvider rejects the whole scan query ("Invalid column samplerate") when asked for
 * SAMPLERATE/BITS_PER_SAMPLE on a version that lacks them. Before this was handled, every
 * Android 12 device, and Android 13-15 devices without the MediaProvider update, got a
 * silently empty library.
 */
class ScanProjectionTest {

    private val sampleRate = MediaStoreLibraryScanner.ColumnSampleRate
    private val bitsPerSample = MediaStoreLibraryScanner.ColumnBitsPerSample

    @Test fun `audio-format columns follow the documented availability`() {
        // since="36", sdks="33:15,34:15,35:15" (platforms/android-36/data/api-versions.xml)
        assertFalse(hasAudioFormatColumns(sdkInt = 31, tiramisuExtensionVersion = 0), "Android 12")
        assertFalse(hasAudioFormatColumns(sdkInt = 32, tiramisuExtensionVersion = 0), "Android 12L")
        assertFalse(hasAudioFormatColumns(sdkInt = 33, tiramisuExtensionVersion = 14), "Android 13, not updated")
        assertTrue(hasAudioFormatColumns(sdkInt = 33, tiramisuExtensionVersion = 15), "Android 13, updated")
        assertFalse(hasAudioFormatColumns(sdkInt = 35, tiramisuExtensionVersion = 14), "Android 15, not updated")
        assertTrue(hasAudioFormatColumns(sdkInt = 35, tiramisuExtensionVersion = 22), "Android 15, updated (CPH2307)")
        assertTrue(hasAudioFormatColumns(sdkInt = 36, tiramisuExtensionVersion = 0), "Android 16")
    }

    @Test fun `projection omits the audio-format columns only when unavailable`() {
        val without = scanProjection(includeAudioFormatColumns = false)
        val with = scanProjection(includeAudioFormatColumns = true)
        assertFalse(sampleRate in without || bitsPerSample in without)
        assertTrue(sampleRate in with && bitsPerSample in with)
        assertContentEquals(without, with.filterNot { it == sampleRate || it == bitsPerSample }.toTypedArray())
        assertTrue(MediaStoreLibraryScanner.ColumnId in without && MediaStoreLibraryScanner.ColumnDuration in without)
    }

    @Test fun `unavailable columns are never requested`() {
        val requested = mutableListOf<Array<String>>()
        val result = queryWithAudioFormatFallback(includeAudioFormatColumns = false) { projection -> requested += projection; "cursor" }
        assertEquals("cursor", result)
        assertEquals(1, requested.size)
        assertFalse(sampleRate in requested.single())
    }

    @Test fun `a provider that rejects the columns is retried once without them`() {
        val requested = mutableListOf<Array<String>>()
        val result = queryWithAudioFormatFallback(includeAudioFormatColumns = true) { projection ->
            requested += projection
            if (sampleRate in projection) throw IllegalArgumentException("Invalid column $sampleRate")
            "cursor"
        }
        assertEquals("cursor", result)
        assertEquals(2, requested.size)
        assertTrue(sampleRate in requested[0])
        assertFalse(sampleRate in requested[1] || bitsPerSample in requested[1])
    }

    @Test fun `a provider that accepts the columns is queried once`() {
        var calls = 0
        queryWithAudioFormatFallback(includeAudioFormatColumns = true) { calls++ }
        assertEquals(1, calls)
    }

    @Test fun `failures unrelated to the columns still propagate`() {
        assertFailsWith<SecurityException> {
            queryWithAudioFormatFallback(includeAudioFormatColumns = true) { throw SecurityException("no permission") }
        }
        // A retry that also fails is not swallowed either.
        assertFailsWith<IllegalArgumentException> {
            queryWithAudioFormatFallback(includeAudioFormatColumns = true) { throw IllegalArgumentException("still bad") }
        }
    }

    private fun prior(hash: String, codec: String) = PriorTrackScanState(hash, 6, 0, "", 0, "", "", "", codec = codec)

    @Test fun `an unchanged file reuses its previously sniffed codec`() {
        assertEquals("alac", reusableCodec(prior("h1", "alac"), "h1"))
    }

    @Test fun `a changed, new or never-sniffed file sniffs its codec again`() {
        assertEquals(null, reusableCodec(prior("h1", "alac"), "h2"))
        assertEquals(null, reusableCodec(null, "h1"))
        assertEquals(null, reusableCodec(prior("h1", ""), "h1"))
    }
}
