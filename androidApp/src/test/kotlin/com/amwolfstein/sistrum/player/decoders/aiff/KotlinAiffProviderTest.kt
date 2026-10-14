@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.aiff

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
import com.amwolfstein.sistrum.player.decoders.CodecProbe
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.amwolfstein.sistrum.player.decoders.defaultDecoderRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.amwolfstein.sistrum.codecs.container.aiff.AiffException
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

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        bytes.write(data.data, data.position, length)
        data.skipBytes(length)
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
    val extractor = KotlinAiffProvider().extractors().single()()
    extractor.init(output)
    val holder = PositionHolder()
    try {
        while (extractor.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { }
        return output
    } finally { extractor.release() }
}

class KotlinAiffProviderTest {

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
        val frames = samples.size / (channels * ((bits + 7) / 8))
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
    fun `standard sample rates are published exactly and zero is malformed`() {
        listOf(44100, 48000, 96000, 192000).forEach { rate ->
            assertEquals(rate, extract(aiff(sampleRate = rate.toDouble(), samples = byteArrayOf(0, 0))).tracks.single().lastFormat!!.sampleRate)
        }
        val error = assertThrows(ParserException::class.java) {
            extract(aiff(sampleRate = 0.0, samples = byteArrayOf(0, 0)))
        }
        assertTrue(error.contentIsMalformed)
        assertEquals("aiff: sample rate 0.0", error.cause!!.message)
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

        val dataStart = 0L
        val seekPoints = seekMap.getSeekPoints(expectedDurationUs / 2)
        val point = seekPoints.first
        assertTrue("adapter reopens the source at zero", point.position == dataStart)
        assertEquals("position should be frame-aligned", 0L, (point.position - dataStart) % 2)
        assertTrue("time should be in range", point.timeUs in 0..expectedDurationUs)
    }

    // --- 7: named refusals ---------------------------------------------------

    @Test
    fun `float and compressed AIFF-C are admitted`() {
        for (compression in listOf("fl32", "fl64", "ima4", "alaw", "ulaw")) {
            val bits = if (compression == "fl64") 64 else if (compression == "fl32") 32 else 16
            assertNull(KotlinAiffProvider().refusal(aiff(form = "AIFC", compression = compression, bits = bits, samples = ByteArray(if (compression == "ima4") 34 else bits / 8))))
        }
    }

    @Test
    fun `aiffRefusal accepts playable files and returns null otherwise`() {
        assertNull(KotlinAiffProvider().refusal(aiff(form = "AIFC", compression = "NONE", samples = byteArrayOf(1, 2))))
        assertNull(KotlinAiffProvider().refusal(aiff(form = "AIFC", compression = "twos", samples = byteArrayOf(1, 2))))
        assertNull(KotlinAiffProvider().refusal(aiff(form = "AIFC", compression = "sowt", samples = byteArrayOf(1, 2))))
        assertNull(KotlinAiffProvider().refusal(aiff(form = "AIFF", samples = byteArrayOf(1, 2))))

        val notAForm = ByteArray(24) { it.toByte() }
        assertNull(KotlinAiffProvider().refusal(notAForm))
    }

    @Test
    fun `12-bit PCM is decoded with its declared valid depth`() {
        val track = extract(aiff(bits = 12, samples = byteArrayOf(0x12, 0x30))).tracks.single()
        assertEquals(C.ENCODING_PCM_16BIT, track.lastFormat!!.pcmEncoding)
        assertArrayEquals(byteArrayOf(0x30, 0x12), track.bytes.toByteArray())
    }

    @Test
    fun `the extractor preserves the typed MAC3 refusal`() {
        val bytes = aiff(form = "AIFC", compression = "MAC3", samples = byteArrayOf(1, 2))
        val exception = assertThrows(ParserException::class.java) { extract(bytes) }
        assertNotNull(exception.message)
        assertFalse(exception.contentIsMalformed)
        assertTrue(exception.cause is AiffException)
        assertTrue(exception.message.contains("aiff: this build does not read MAC3 (compression type \"MAC3\") from an AIFF-C"))
    }

    @Test
    fun `aiffRefusal returns null for an overflowing declared chunk size`() {
        val first = rawChunk("ANNO", 2, byteArrayOf(1, 2))
        val second = rawChunk("JUNK", 0x7FFFFFFFL, byteArrayOf(1, 2, 3, 4))
        assertNull(KotlinAiffProvider().refusal(formHeader("AIFF", first, second)))
    }

    @Test
    fun `aiffRefusal returns null when the compression type is truncated`() {
        val full = aiff(form = "AIFC", compression = "MAC3", samples = byteArrayOf(1, 2))
        // COMM body starts at 20; the compression type at 38. Keep 40 bytes,
        // i.e. two bytes into the four-byte type.
        val truncated = full.copyOf(40)
        assertEquals(40, truncated.size)
        assertNull(KotlinAiffProvider().refusal(truncated))
    }

    @Test
    fun `aiffRefusal never throws on prefixes or random AIFC headers`() {
        val full = aiff(form = "AIFC", compression = "MAC3", samples = byteArrayOf(1, 2))
        for (length in 0..full.size) {
            KotlinAiffProvider().refusal(full.copyOf(length))
        }
        assertEquals("aiff: this build does not read MAC3 (compression type \"MAC3\") from an AIFF-C", KotlinAiffProvider().refusal(full))

        val random = java.util.Random(20261006L)
        repeat(200) {
            val extra = random.nextInt(64)
            val bytes = ByteArray(12 + extra)
            random.nextBytes(bytes)
            "FORM".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
            "AIFC".toByteArray(Charsets.US_ASCII).copyInto(bytes, 8)
            KotlinAiffProvider().refusal(bytes)
        }
    }

    @Test
    fun `fl32 and fl64 decode to exact little-endian floats`() {
        for ((compression, bits, samples) in listOf(
            Triple("fl32", 32, byteArrayOf(0x3f, 0x00, 0x00, 0x00)),
            Triple("fl64", 64, byteArrayOf(0x3f, 0xe0.toByte(), 0, 0, 0, 0, 0, 0)),
        )) {
            val bytes = aiff(form = "AIFC", compression = compression, bits = bits, samples = samples)
            assertNull(KotlinAiffProvider().refusal(bytes))
            val track = extract(bytes).tracks.single()
            assertEquals(C.ENCODING_PCM_FLOAT, track.lastFormat!!.pcmEncoding)
            assertArrayEquals(byteArrayOf(0, 0, 0, 0x3f), track.bytes.toByteArray())
        }
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
            assertEquals("frame alignment", 0L, point.position % 6)
            assertEquals("adapter reopens at zero", 0L, point.position)
        }
    }

    @Test
    fun `raw in24 in32 and NONE follow their declared byte orders`() {
        data class Case(val compression: String, val bits: Int, val samples: ByteArray, val expected: ByteArray)
        for ((compression, bits, samples, expected) in listOf(
            Case("raw ", 8, byteArrayOf(0, 127, 0x80.toByte(), 0xff.toByte()), byteArrayOf(0, 127, 0x80.toByte(), 0xff.toByte())),
            Case("in24", 24, byteArrayOf(1, 2, 3), byteArrayOf(3, 2, 1)),
            Case("in32", 32, byteArrayOf(1, 2, 3, 4), byteArrayOf(4, 3, 2, 1)),
            Case("NONE", 16, byteArrayOf(1, 2), byteArrayOf(2, 1)),
        )) {
            val track = extract(aiff(form = "AIFC", compression = compression, bits = bits, samples = samples)).tracks.single()
            assertArrayEquals(compression, expected, track.bytes.toByteArray())
        }
    }

    @Test
    fun `plain mono and stereo seeks reproduce exact frames forward backward and at EOS`() {
        for ((channels, bits) in listOf(1 to 16, 2 to 24)) {
            val frameBytes = channels * bits / 8
            val samples = ByteArray(1000 * frameBytes) { (it % 127).toByte() }
            val bytes = aiff(channels = channels, bits = bits, samples = samples, ssndOffset = 3)
            val expected = samples.asList().chunked(bits / 8).flatMap { it.reversed() }.toByteArray()
            val input = FakeExtractorInput.Builder().setData(bytes).build()
            val extractor = KotlinAiffProvider().extractors().single()()
            val holder = PositionHolder()
            try {
                extractor.init(RecordingExtractorOutput())
                while (extractor.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { }
                for (frame in listOf(500L, 1L, 999L, 0L, 1000L)) {
                    val output = RecordingExtractorOutput()
                    extractor.init(output)
                    extractor.seek(0, (frame * 1_000_000 + 44099) / 44100)
                    while (extractor.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { }
                    val track = output.tracks.single()
                    assertArrayEquals("$channels channels at $frame", expected.copyOfRange((frame * frameBytes).toInt(), expected.size), track.bytes.toByteArray())
                    if (frame < 1000) assertEquals(frame * 1_000_000 / 44100, track.sampleTimesUs.first())
                    else assertTrue(track.sampleTimesUs.isEmpty())
                }
            } finally { extractor.release() }
        }
    }

    @Test
    fun `unsupported depths and MACE retain exact typed decoder reasons`() {
        for ((bytes, reason) in listOf(
            aiff(bits = 40, samples = ByteArray(5)) to "aiff: 40 bits per sample",
            aiff(form = "AIFC", compression = "MAC6", samples = byteArrayOf(0, 0)) to
                "aiff: this build does not read MAC6 (compression type \"MAC6\") from an AIFF-C",
        )) {
            assertEquals(reason, KotlinAiffProvider().refusal(bytes))
            val error = assertThrows(ParserException::class.java) { extract(bytes) }
            assertFalse(error.contentIsMalformed)
            assertTrue(error.cause is AiffException)
            assertTrue((error.cause as AiffException).unsupported)
            assertEquals(reason, error.cause!!.message)
        }
    }

    @Test
    fun `empty odd-channel and high-rate streams preserve exact boundaries`() {
        for ((channels, rate, frames) in listOf(Triple(1, 44100, 0), Triple(3, 192000, 2))) {
            val track = extract(aiff(channels = channels, sampleRate = rate.toDouble(), samples = ByteArray(frames * channels * 2))).tracks.single()
            assertEquals(channels, track.lastFormat!!.channelCount)
            assertEquals(rate, track.lastFormat!!.sampleRate)
            assertArrayEquals(ByteArray(frames * channels * 2), track.bytes.toByteArray())
        }
        val error = assertThrows(ParserException::class.java) { extract("FORM".toByteArray()) }
        assertTrue(error.contentIsMalformed)
        assertTrue(error.cause is AiffException)
        assertFalse((error.cause as AiffException).unsupported)
    }

    @Test
    fun `all original AIFF oracle fixtures retain exact PCM format duration and counts`() {
        val root = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").isFile }
        val lines = java.io.File(root, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }
        val columns = lines.first().split('\t')
        val rows = lines.drop(1).map { columns.zip(it.split('\t')).toMap() }
            .filter { it["status"] == "ok" && (it.getValue("file").endsWith(".aiff") || it.getValue("file").endsWith(".aifc")) }
        assertEquals(18, rows.size)
        val corpus = System.getProperty("waxflow.corpus") ?: System.getenv("WAXFLOW_CORPUS")
            ?: "${System.getenv("SISTRUM_ORACLE_DIR") ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle"}/corpus"
        fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        for (row in rows) {
            val bytes = java.io.File(corpus, row.getValue("file")).readBytes()
            assertEquals(row.getValue("file_sha256"), hash(bytes))
            val output = extract(bytes)
            val track = output.tracks.single()
            val format = track.lastFormat!!
            assertEquals(row.getValue("pcm_sha256"), hash(track.bytes.toByteArray()))
            assertEquals(row.getValue("frames").toLong() * row.getValue("channels").toInt() * row.getValue("bits").toInt() / 8, track.bytes.size().toLong())
            assertEquals(MimeTypes.AUDIO_RAW, format.sampleMimeType)
            assertEquals(if (row["sample_format"] == "float") C.ENCODING_PCM_FLOAT else when (row.getValue("bits").toInt()) {
                8 -> C.ENCODING_PCM_8BIT
                16 -> C.ENCODING_PCM_16BIT
                24 -> C.ENCODING_PCM_24BIT
                else -> C.ENCODING_PCM_32BIT
            }, format.pcmEncoding)
            assertEquals(row.getValue("rate").toInt(), format.sampleRate)
            assertEquals(row.getValue("channels").toInt(), format.channelCount)
            assertEquals(row.getValue("frames").toLong() * 1_000_000 / format.sampleRate, output.seekMap!!.durationUs)
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
