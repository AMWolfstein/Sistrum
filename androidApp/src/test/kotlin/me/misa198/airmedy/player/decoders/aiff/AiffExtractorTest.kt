@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package me.misa198.airmedy.player.decoders.aiff

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.test.utils.FakeExtractorInput
import me.misa198.airmedy.player.decoders.CodecProbe
import me.misa198.airmedy.player.decoders.FormatKey
import me.misa198.airmedy.player.decoders.defaultDecoderRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Records the [Format] and sample bytes an extractor publishes. */
private class RecordingTrackOutput : TrackOutput {
    var lastFormat: Format? = null

    val bytes = ByteArrayOutputStream()
    val sampleTimesUs = mutableListOf<Long>()
    val sampleSizes = mutableListOf<Int>()

    override fun format(format: Format) {
        lastFormat = format
    }

    override fun sampleData(data: ParsableByteArray, length: Int, offset: Int) {
        bytes.write(data.data, data.position + offset, length)
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int = length

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        sampleTimesUs.add(timeUs)
        sampleSizes.add(size)
    }
}

private class RecordingExtractorOutput : ExtractorOutput {
    val tracks = mutableListOf<RecordingTrackOutput>()
    var seekMap: SeekMap? = null

    override fun track(id: Int, type: Int): TrackOutput =
        RecordingTrackOutput().also { tracks.add(it) }

    override fun endTracks() = Unit

    override fun seekMap(seekMap: SeekMap) {
        this.seekMap = seekMap
    }
}

private fun extract(bytes: ByteArray): RecordingExtractorOutput {
    val input = FakeExtractorInput.Builder().setData(bytes).build()
    val output = RecordingExtractorOutput()
    val extractor = AiffExtractor()
    extractor.init(output)
    val holder = PositionHolder()
    while (extractor.read(input, holder) != Extractor.RESULT_END_OF_INPUT) {
        // Drain until the extractor stops.
    }
    return output
}

class AiffExtractorTest {

    // --- Container building --------------------------------------------------

    private fun u16Bytes(value: Int): ByteArray =
        byteArrayOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    private fun u32Bytes(value: Long): ByteArray = ByteArray(4) { i ->
        ((value ushr (8 * (3 - i))) and 0xFF).toByte()
    }

    /** The 80-bit IEEE 754 extended float the COMM chunk uses for sample rate. */
    private fun extended80(value: Double): ByteArray {
        val result = ByteArray(10)
        if (value == 0.0) return result
        val bits = java.lang.Double.doubleToLongBits(value)
        val sign = (bits ushr 63) and 1L
        val unbiased = ((bits ushr 52) and 0x7FFL).toInt() - 1023
        val exponent = unbiased + 16383
        val mantissa = (1L shl 63) or ((bits and 0x000FFFFFFFFFFFFFL) shl 11)
        result[0] = ((sign shl 7) or ((exponent ushr 8) and 0x7F).toLong()).toByte()
        result[1] = (exponent and 0xFF).toByte()
        for (i in 0 until 8) {
            result[2 + i] = ((mantissa ushr (8 * (7 - i))) and 0xFF).toByte()
        }
        return result
    }

    private fun chunk(id: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(u32Bytes(body.size.toLong()))
        out.write(body)
        if (body.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }

    private fun commBody(
        channels: Int,
        frames: Int,
        bits: Int,
        sampleRate: Double,
        compression: String?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u16Bytes(channels))
        out.write(u32Bytes(frames.toLong()))
        out.write(u16Bytes(bits))
        out.write(extended80(sampleRate))
        compression?.let { out.write(it.toByteArray(Charsets.US_ASCII)) }
        return out.toByteArray()
    }

    private fun ssndBody(samples: ByteArray, offset: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32Bytes(offset.toLong()))
        out.write(u32Bytes(0))
        if (offset > 0) out.write(ByteArray(offset))
        out.write(samples)
        return out.toByteArray()
    }

    /** A raw chunk with a declared size that need not match the body length. */
    private fun rawChunk(id: String, declaredSize: Long, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(u32Bytes(declaredSize))
        out.write(body)
        return out.toByteArray()
    }

    private fun formHeader(form: String, vararg chunks: ByteArray): ByteArray {
        val body = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.US_ASCII))
        out.write(u32Bytes((4 + body.size).toLong()))
        out.write(form.toByteArray(Charsets.US_ASCII))
        out.write(body)
        return out.toByteArray()
    }

    private fun aiff(
        form: String = "AIFF",
        channels: Int = 1,
        sampleRate: Double = 44100.0,
        bits: Int = 16,
        samples: ByteArray,
        compression: String? = if (form == "AIFC") "NONE" else null,
        ssndOffset: Int = 0,
    ): ByteArray {
        val frames = samples.size / (channels * (bits / 8))
        val comm = chunk("COMM", commBody(channels, frames, bits, sampleRate, compression))
        val ssnd = chunk("SSND", ssndBody(samples, ssndOffset))
        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.US_ASCII))
        out.write(u32Bytes((4 + comm.size + ssnd.size).toLong()))
        out.write(form.toByteArray(Charsets.US_ASCII))
        out.write(comm)
        out.write(ssnd)
        return out.toByteArray()
    }

    // --- 1: 16-bit big-endian NONE ------------------------------------------

    @Test
    fun `16-bit big-endian NONE publishes raw PCM and swaps to little-endian`() {
        val output = extract(aiff(channels = 1, bits = 16, samples = byteArrayOf(0x01, 0x02, 0x03, 0x04)))

        val track = output.tracks.single()
        val format = track.lastFormat!!
        assertEquals(MimeTypes.AUDIO_RAW, format.sampleMimeType)
        assertEquals(C.ENCODING_PCM_16BIT, format.pcmEncoding)
        assertEquals(44100, format.sampleRate)
        assertEquals(1, format.channelCount)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x04, 0x03), track.bytes.toByteArray())
    }

    // --- 2: 24-bit and 32-bit ------------------------------------------------

    @Test
    fun `24-bit big-endian samples are swapped`() {
        val output = extract(aiff(bits = 24, samples = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)))

        val track = output.tracks.single()
        assertEquals(C.ENCODING_PCM_24BIT, track.lastFormat!!.pcmEncoding)
        assertArrayEquals(byteArrayOf(0x03, 0x02, 0x01, 0x06, 0x05, 0x04), track.bytes.toByteArray())
    }

    @Test
    fun `32-bit big-endian samples are swapped`() {
        val output = extract(
            aiff(bits = 32, samples = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)),
        )

        val track = output.tracks.single()
        assertEquals(C.ENCODING_PCM_32BIT, track.lastFormat!!.pcmEncoding)
        assertArrayEquals(byteArrayOf(4, 3, 2, 1, 8, 7, 6, 5), track.bytes.toByteArray())
    }

    // --- 3: 8-bit bias in both byte orders -----------------------------------

    @Test
    fun `8-bit AIFF and 8-bit sowt both get the bias`() {
        val samples = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val expected = byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0x00, 0x7F)

        val plain = extract(aiff(bits = 8, samples = samples))
        val plainTrack = plain.tracks.single()
        assertEquals(C.ENCODING_PCM_8BIT, plainTrack.lastFormat!!.pcmEncoding)
        assertArrayEquals(expected, plainTrack.bytes.toByteArray())

        val sowt = extract(aiff(form = "AIFC", compression = "sowt", bits = 8, samples = samples))
        assertArrayEquals(expected, sowt.tracks.single().bytes.toByteArray())
    }

    // --- 4: sowt/twos byte order --------------------------------------------

    @Test
    fun `sowt 16-bit is unchanged and twos 16-bit is swapped`() {
        val samples = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val sowt = extract(aiff(form = "AIFC", compression = "sowt", bits = 16, samples = samples))
        assertArrayEquals(samples, sowt.tracks.single().bytes.toByteArray())

        val twos = extract(aiff(form = "AIFC", compression = "twos", bits = 16, samples = samples))
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x04, 0x03), twos.tracks.single().bytes.toByteArray())
    }

    // --- 5: the 80-bit extended float ---------------------------------------

    @Test
    fun `extendedFloat decodes the standard sample rates exactly`() {
        listOf(44100.0, 48000.0, 96000.0, 192000.0).forEach { rate ->
            assertEquals(rate, extended80(rate).extendedFloat(0), 0.0)
        }
        assertEquals(0.0, extended80(0.0).extendedFloat(0), 0.0)
    }

    // --- 6: seek map ---------------------------------------------------------

    @Test
    fun `seek map is seekable with a correct duration and frame-aligned points`() {
        val sampleRate = 44100.0
        val frames = 1000
        val samples = ByteArray(frames * 2)
        val output = extract(aiff(channels = 1, sampleRate = sampleRate, bits = 16, samples = samples))

        val seekMap = output.seekMap!!
        assertTrue(seekMap.isSeekable())

        val bytesPerSecond = 2 * sampleRate.toInt()
        val expectedDurationUs = samples.size.toLong() * C.MICROS_PER_SECOND / bytesPerSecond
        assertEquals(expectedDurationUs, seekMap.getDurationUs())

        val dataStart = 54L
        val seekPoints = seekMap.getSeekPoints(expectedDurationUs / 2)
        val point = seekPoints.first
        assertTrue("position should land inside SSND", point.position in dataStart until dataStart + samples.size)
        assertEquals("position should be frame-aligned", 0L, (point.position - dataStart) % 2)
        assertTrue("time should be in range", point.timeUs in 0..expectedDurationUs)
    }

    // --- 7: named refusals ---------------------------------------------------

    @Test
    fun `aiffRefusal names float and compressed AIFF-C`() {
        assertEquals(
            "float AIFF-C (fl32)",
            aiffRefusal(aiff(form = "AIFC", compression = "fl32", samples = byteArrayOf(1, 2))),
        )
        assertEquals(
            "compressed AIFF-C (ima4)",
            aiffRefusal(aiff(form = "AIFC", compression = "ima4", samples = byteArrayOf(1, 2))),
        )
    }

    @Test
    fun `aiffRefusal accepts playable files and returns null otherwise`() {
        assertNull(aiffRefusal(aiff(form = "AIFC", compression = "NONE", samples = byteArrayOf(1, 2))))
        assertNull(aiffRefusal(aiff(form = "AIFC", compression = "twos", samples = byteArrayOf(1, 2))))
        assertNull(aiffRefusal(aiff(form = "AIFC", compression = "sowt", samples = byteArrayOf(1, 2))))
        assertNull(aiffRefusal(aiff(form = "AIFF", samples = byteArrayOf(1, 2))))

        val notAForm = ByteArray(24) { it.toByte() }
        assertNull(aiffRefusal(notAForm))
    }

    @Test
    fun `aiffRefusal refuses an unsupported sample size`() {
        assertEquals(
            "unsupported AIFF sample size (12)",
            aiffRefusal(aiff(bits = 12, samples = byteArrayOf(1, 2))),
        )
    }

    @Test
    fun `the extractor throws a named ParserException for ima4`() {
        val bytes = aiff(form = "AIFC", compression = "ima4", samples = byteArrayOf(1, 2))
        val input = FakeExtractorInput.Builder().setData(bytes).build()
        val extractor = AiffExtractor()
        extractor.init(RecordingExtractorOutput())

        val exception = assertThrows(ParserException::class.java) {
            extractor.read(input, PositionHolder())
        }
        assertNotNull(exception.message)
        assertTrue(exception.message.contains("compressed AIFF-C (ima4)"))
    }

    @Test
    fun `aiffRefusal returns null for an overflowing declared chunk size`() {
        val first = rawChunk("ANNO", 2, byteArrayOf(1, 2))
        val second = rawChunk("JUNK", 0x7FFFFFFFL, byteArrayOf(1, 2, 3, 4))
        assertNull(aiffRefusal(formHeader("AIFF", first, second)))
    }

    @Test
    fun `aiffRefusal returns null when the compression type is truncated`() {
        val full = aiff(form = "AIFC", compression = "ima4", samples = byteArrayOf(1, 2))
        // COMM body starts at 20; the compression type at 38. Keep 40 bytes,
        // i.e. two bytes into the four-byte type.
        val truncated = full.copyOf(40)
        assertEquals(40, truncated.size)
        assertNull(aiffRefusal(truncated))
    }

    @Test
    fun `aiffRefusal never throws on prefixes or random AIFC headers`() {
        val full = aiff(form = "AIFC", compression = "ima4", samples = byteArrayOf(1, 2))
        for (length in 0..full.size) {
            aiffRefusal(full.copyOf(length))
        }
        assertEquals("compressed AIFF-C (ima4)", aiffRefusal(full))

        val random = java.util.Random(20261006L)
        repeat(200) {
            val extra = random.nextInt(64)
            val bytes = ByteArray(12 + extra)
            random.nextBytes(bytes)
            "FORM".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
            "AIFC".toByteArray(Charsets.US_ASCII).copyInto(bytes, 8)
            aiffRefusal(bytes)
        }
    }

    @Test
    fun `an fl64 file is named as float by the extractor and aiffRefusal`() {
        val bytes = aiff(form = "AIFC", compression = "fl64", bits = 64, samples = ByteArray(8))
        assertEquals("float AIFF-C (fl64)", aiffRefusal(bytes))

        val input = FakeExtractorInput.Builder().setData(bytes).build()
        val extractor = AiffExtractor()
        extractor.init(RecordingExtractorOutput())
        val exception = assertThrows(ParserException::class.java) {
            extractor.read(input, PositionHolder())
        }
        assertTrue(exception.message.contains("float AIFF-C (fl64)"))
    }

    @Test
    fun `seek points for stereo 24-bit are aligned to six-byte frames`() {
        val sampleRate = 44100.0
        val frames = 1000
        val samples = ByteArray(frames * 6)
        val output = extract(aiff(channels = 2, sampleRate = sampleRate, bits = 24, samples = samples))

        val seekMap = output.seekMap!!
        val durationUs = seekMap.getDurationUs()
        for (fraction in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val point = seekMap.getSeekPoints((durationUs * fraction).toLong()).first
            assertEquals("frame alignment", 0L, (point.position - 54) % 6)
            assertTrue("inside SSND", point.position in 54L..(54L + samples.size))
        }
    }

    // --- 8: provider and registry -------------------------------------------

    @Test
    fun `the kotlin-aiff provider is registered for AIFF`() {
        assertEquals("kotlin-aiff", KotlinAiffProvider().id)

        val registry = defaultDecoderRegistry(CodecProbe { false })
        assertEquals("kotlin-aiff", registry.resolve(FormatKey("aiff", "aiff"))?.id)
    }
}
