@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.media3

import android.net.Uri
import android.os.Parcel
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.test.utils.FakeExtractorInput
import androidx.media3.test.utils.FakeExtractorOutput
import androidx.media3.test.utils.FakeTrackOutput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe

/** Exercises the production MP3 wrapper with independent synthetic frames and Media3's extractor. */
class Mp3LameProvenanceTest {
    @Before
    fun enableParserBoundsChecks() {
        // Media3's automatic test detection reads the null host Build.FINGERPRINT stub.
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true)
    }

    @After
    fun restoreParserBoundsChecks() {
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
    }

    @Test
    fun rawLameProvenanceLeavesMedia3TrimUntouched() {
        val track = extract(wrappedMp3(), mp3Stream())
        assertProvenance(checkNotNull(track.lastFormat))
        assertEquals(10, track.sampleCount)
    }

    @Test
    fun rawLameProvenanceBehindOneKiBId3() {
        val track = extract(wrappedMp3(), id3Tag() + mp3Stream())
        assertProvenance(checkNotNull(track.lastFormat))
        assertEquals(10, track.sampleCount)
    }

    @Test
    fun noArgFactoryCarriesProvenanceWhenSniffIsSkipped() {
        // DefaultExtractorsFactory's no-arg overload uses the null host Uri.EMPTY stub.
        val extractor = ProviderExtractorsFactory(
            emptyList(), base = ExtractorsFactory { arrayOf(Mp3Extractor()) }, recorder = ProviderRecorder(),
        )
            .createExtractors().first { it.underlyingImplementation is Mp3Extractor }
        val track = extract(extractor, id3Tag() + mp3Stream(), sniff = false)
        assertProvenance(checkNotNull(track.lastFormat))
        assertEquals(10, track.sampleCount)
    }

    @Test
    fun sampleBytesMatchUnwrappedExtractor() {
        for (prefix in listOf(byteArrayOf(), id3Tag())) {
            val stream = prefix + mp3Stream()
            val wrapped = extract(wrappedMp3(), stream)
            val plain = extract(Mp3Extractor(), stream)
            assertEquals(10, wrapped.sampleCount)
            assertEquals(plain.sampleCount, wrapped.sampleCount)
            for (index in 0 until plain.sampleCount) {
                assertArrayEquals("sample $index", plain.getSampleData(index), wrapped.getSampleData(index))
            }
        }
    }

    @Test
    fun xingWithFewerThan24LameBytesHasNoProvenance() {
        val frame = audioFrame()
        "Xing".toByteArray(Charsets.US_ASCII).copyInto(frame, 36)
        // TOC + quality end at 148, leaving only 8 bytes of LAME data in this valid 156-byte frame.
        frame[43] = 0x0C
        val shortFrame = frame.copyOf(156)
        shortFrame[2] = 0x30 // MPEG-1 Layer III, 48 kbps at 44.1 kHz: 156-byte frame.
        "LAME".toByteArray(Charsets.US_ASCII).copyInto(shortFrame, 148)
        val track = extract(wrappedMp3(), shortFrame + ByteArray(156 * 10).also { frames ->
            repeat(10) { index -> shortFrame.copyOfRange(0, 4).copyInto(frames, index * 156) }
        })
        assertNull(lameInfo(checkNotNull(track.lastFormat)))
        assertEquals(10, track.sampleCount)
    }

    @Test
    fun threeByteInputIsRejectedWithoutWrapperException() {
        val extractor = wrappedMp3()
        val input = input(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte()))
        assertFalse(extractor.sniff(input))
        assertEquals(0L, input.position)
        extractor.release()
    }

    private fun wrappedMp3(): Extractor = ProviderExtractorsFactory(emptyList(), recorder = ProviderRecorder())
        .createExtractors(hostMp3Uri(), emptyMap())
        .first { it.underlyingImplementation is Mp3Extractor }

    private fun extract(extractor: Extractor, data: ByteArray, sniff: Boolean = true): FakeTrackOutput {
        val input = input(data)
        val tracks = mutableListOf<FakeTrackOutput>()
        // Keep the factory-created tracks: Android's host SparseArray is a stub.
        val output = FakeExtractorOutput { _, type -> FakeTrackOutput(type, false).also { tracks += it } }
        try {
            if (sniff) assertTrue(extractor.sniff(input))
            input.resetPeekPosition()
            extractor.init(output)
            val seekPosition = PositionHolder()
            var reads = 0
            while (true) {
                assertTrue("extractor must finish", reads++ < 1000)
                when (extractor.read(input, seekPosition)) {
                    Extractor.RESULT_END_OF_INPUT -> break
                    Extractor.RESULT_SEEK -> input.setPosition(seekPosition.position.toInt())
                }
            }
            assertTrue(output.tracksEnded)
            return tracks.single { it.type == C.TRACK_TYPE_AUDIO }
        } finally {
            extractor.release()
        }
    }

    private fun input(data: ByteArray): FakeExtractorInput = FakeExtractorInput.Builder().setData(data).build()

    private fun assertProvenance(format: Format) {
        assertEquals(LameGaplessInfo("LAME3.100", 576, 1344), lameInfo(format))
        assertEquals(1105, format.encoderDelay)
        assertEquals(815, format.encoderPadding)
    }

    private fun lameInfo(format: Format): LameGaplessInfo? {
        val metadata = format.metadata ?: return null
        return (0 until metadata.length()).map { metadata[it] }.filterIsInstance<LameGaplessInfo>().singleOrNull()
    }

    private fun audioFrame(): ByteArray = ByteArray(417).also {
        byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0).copyInto(it)
    }

    private fun mp3Stream(): ByteArray {
        val info = audioFrame()
        "Info".toByteArray(Charsets.US_ASCII).copyInto(info, 36)
        info[43] = 3 // Frame count and byte count, no TOC or quality.
        info[47] = 11
        val size = 417 * 11
        repeat(4) { index -> info[48 + index] = (size ushr (24 - index * 8)).toByte() }
        "LAME3.100".toByteArray(Charsets.US_ASCII).copyInto(info, 52)
        info[73] = 0x24
        info[74] = 0x05
        info[75] = 0x40 // Packed raw delay/padding: 576/1344.
        return info + ByteArray(417 * 10).also { frames ->
            repeat(10) { index -> audioFrame().copyInto(frames, index * 417) }
        }
    }

    private fun id3Tag(): ByteArray = ByteArray(1024).also {
        "ID3".toByteArray(Charsets.US_ASCII).copyInto(it)
        it[3] = 4 // ID3v2.4 with an empty, padded body.
        it[8] = 7
        it[9] = 118 // Syncsafe body size = 1014, plus the 10-byte header.
    }
}

/**
 * Android's host Uri.parse/EMPTY are null. Supply the two URI properties the factory uses,
 * bypassing Uri's package-private constructor, which apps cannot call even in a host test.
 */
private fun hostMp3Uri(): Uri {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
    return (field.get(null) as Unsafe).allocateInstance(HostMp3Uri::class.java) as Uri
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private class HostMp3Uri : Uri() {
    override fun toString(): String = "file:///provenance.mp3"
    override fun getLastPathSegment(): String = "provenance.mp3"
    override fun getScheme(): String = "file"
    override fun getPath(): String = "/provenance.mp3"
    override fun getEncodedPath(): String = path
    override fun getPathSegments(): List<String> = listOf(lastPathSegment)
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getSchemeSpecificPart(): String = "///provenance.mp3"
    override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun buildUpon(): Uri.Builder = error("unused in the extractor test")
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = error("unused in the extractor test")
}
