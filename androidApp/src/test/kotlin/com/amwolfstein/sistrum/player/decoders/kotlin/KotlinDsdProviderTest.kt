@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.*
import androidx.media3.test.utils.FakeExtractorInput
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd
import com.amwolfstein.sistrum.codecs.container.ByteBufferSource
import com.amwolfstein.sistrum.codecs.audio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureLog
import com.amwolfstein.sistrum.player.decoders.*
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import com.amwolfstein.sistrum.player.media3.tagSniff
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

class KotlinDsdProviderTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "docs/dsd/ORACLE.md").isFile }
    private val owned = System.getProperty("oracle.owned") ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle/owned"
    private val rows by lazy {
        val lines = File(root, "androidApp/src/test/resources/dsd/oracle-fixtures.tsv").readLines()
        lines.drop(1).filter { it.isNotBlank() }.map { lines.first().split('\t').zip(it.split('\t')).toMap() }
            .also { assertEquals(20, it.size) }
    }
    private fun source(row: Map<String, String>) = File("$owned/dsd", row.getValue("file")).readBytes().also {
        assertEquals(row.getValue("file_sha256"), hash(it))
    }
    private fun floats(b: FloatBuffer): ByteArray = ByteBuffer.allocate(b.frames * b.channels * 4)
        .order(ByteOrder.LITTLE_ENDIAN).also { bytes ->
            for (i in 0 until b.frames * b.channels) bytes.putFloat(b.samples[i])
        }.array()
    private fun production(data: ByteArray): Pair<Dsd.Info, ByteArray> {
        val dsd = Dsd.open(ByteBuffer.wrap(data))
        val pcm = ByteArrayOutputStream()
        while (true) pcm.write(floats(dsd.decodeBlock() ?: break))
        return dsd.info to pcm.toByteArray()
    }

    @Test fun everySuccessfulOracleFixtureMatchesProductionConverterExactly() {
        val playable = rows.filter { it.getValue("file") != "dst-compressed.dff" }
        assertEquals(19, playable.size)
        for (row in playable) {
            val data = source(row)
            val (info, expected) = production(data)
            val out = decode(KotlinDsdProvider(), data)
            assertArrayEquals(row.getValue("file"), expected, out.sink.pcm.toByteArray())
            assertEquals(info.totalSamples * info.channels * 4, out.sink.bytes)
            assertEquals(info.sampleRate, out.sink.format!!.sampleRate)
            assertEquals(info.channels, out.sink.format!!.channelCount)
            assertEquals(C.ENCODING_PCM_FLOAT, out.sink.format!!.pcmEncoding)
            assertEquals(MimeTypes.AUDIO_RAW, out.sink.format!!.sampleMimeType)
            assertEquals(0, out.sink.format!!.encoderDelay)
            assertEquals(0, out.sink.format!!.encoderPadding)
            assertEquals(info.totalSamples * 1_000_000 / info.sampleRate, out.map!!.durationUs)
            assertTrue(out.map!!.isSeekable)
            assertEquals(if (info.dsdSampleRate == 2822400) 88200 else 176400, info.sampleRate)
        }
    }

    @Test fun forwardBackwardAndEofSeeksRestoreFilterHistoryForEveryPlayableFixture() {
        for (row in rows.filter { it.getValue("file") != "dst-compressed.dff" }) {
            checkSeeks(KotlinDsdProvider(), source(row))
        }
    }
    private fun checkSeeks(p: DecoderProvider, data: ByteArray) {
        val linear = decode(p, data).sink
        val rate = linear.format!!.sampleRate
        val frameBytes = linear.format!!.channelCount * 4
        val total = linear.bytes / frameBytes
        val all = linear.pcm.toByteArray()
        val e = p.extractors().single()()
        val inp = input(data)
        val holder = PositionHolder()
        try {
            e.init(Output(false))
            while (e.read(inp, holder) != Extractor.RESULT_END_OF_INPUT) { }
            for (target in listOf(total / 2, 1L, total - 1, 0L, total)) {
                val out = Output(true)
                e.init(out)
                e.seek(0, (target * 1_000_000 + rate - 1) / rate)
                while (e.read(inp, holder) != Extractor.RESULT_END_OF_INPUT) { }
                assertArrayEquals("at $target", all.copyOfRange((target * frameBytes).toInt(), all.size), out.sink.pcm.toByteArray())
                if (target < total) assertEquals(target * 1_000_000 / rate, out.sink.times.first())
                else assertTrue(out.sink.times.isEmpty())
            }
        } finally { e.release() }
    }

    @Test fun highRateDsdUsesHqResamplerAndSeeksExactly() {
        for (targetRate in listOf(352800, 705600)) {
            val data = source(rows.single { it.getValue("file") == "dsd256-2ch.dsf" })
            val p = KotlinDsdProvider(targetRate)
            val result = decode(p, data)
            assertEquals(176400, result.sink.format!!.sampleRate)
            val raw = Dsd.open(ByteBuffer.wrap(data), targetRate)
            val planar = Array(2) { FloatArray(raw.info.totalSamples.toInt()) }
            while (true) {
                val b = raw.decodeBlock() ?: break
                for (ch in 0..1) for (i in 0 until b.frames) planar[ch][b.position.toInt() + i] = b.samples[i * 2 + ch]
            }
            val resampler = com.amwolfstein.sistrum.dsp.resample.Resampler(targetRate, 176400, 2,
                com.amwolfstein.sistrum.dsp.resample.Profile.HQ)
            val total = com.amwolfstein.sistrum.dsp.resample.Resampler.outputLen(raw.info.totalSamples, targetRate, 176400).toInt()
            val expected = Array(2) { FloatArray(total) }
            val produced = resampler.process(expected, planar)
            assertEquals(planar[0].size, resampler.consumed)
            assertEquals(total, produced + resampler.drain(expected, produced))
            val bytes = ByteBuffer.allocate(total * 8).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until total) for (ch in 0..1) bytes.putFloat(expected[ch][i])
            assertArrayEquals(bytes.array(), result.sink.pcm.toByteArray())
            checkSeeks(p, data)
        }
    }

    @Test fun wavpackDsdSeeksMatchLinearPcmForAllStandaloneDsdRows() {
        val lines = File(root, "codecs/src/test/resources/wavpack/libwavpack.tsv").readLines().filter { !it.startsWith('#') && it.isNotBlank() }
        val fixtures = lines.drop(1).map { lines.first().split('\t').zip(it.split('\t')).toMap() }
            .filter { it.getValue("correction") == "false" && it.getValue("info").split(';').getOrNull(1) == "1" }
        assertEquals(14, fixtures.size)
        for (row in fixtures) {
            val name = row.getValue("file")
            val file = if (name.startsWith("generated/")) File("$owned/wavpack", name)
                else File(System.getenv("WAXFLOW_CORPUS") ?: "${File(owned).parent}/corpus", name)
            val data = file.readBytes()
            assertEquals(row.getValue("sha256"), hash(data))
            if (row.getValue("exit") == "0") checkSeeks(KotlinWavPackProvider(), data)
            else {
                val error = assertThrows(ParserException::class.java) { decode(KotlinWavPackProvider(), data) }
                assertTrue(error.contentIsMalformed)
                assertEquals(com.amwolfstein.sistrum.codecs.codec.wavpack.ErrorCode.MALFORMED,
                    (error.cause as com.amwolfstein.sistrum.codecs.codec.wavpack.WavPackException).code)
            }
        }
    }

    @Test fun pinnedDffRefusalsRetainNamedCauseAndProviderLog() {
        val p = KotlinDsdProvider()
        val refused = rows.filter { it.getValue("file") == "dst-compressed.dff" }
        assertEquals(1, refused.size)
        for (row in refused) {
            val data = source(row)
            val reason = row.getValue("status").removePrefix("refused: ")
            assertEquals(reason, p.refusal(data.take(p.refusalHeaderBytes).toByteArray()))
            val recorder = ProviderRecorder()
            val uri = "file:///" + row.getValue("file")
            val e = tagSniff(p.extractors().single()()) { recorder.record(uri, p.id) }
            assertTrue(e.sniff(input(data)))
            val error = assertThrows(ParserException::class.java) { decode(p, data, e) }
            assertFalse(error.contentIsMalformed)
            assertTrue(error.cause is DsdUnsupportedException)
            assertEquals(reason, error.cause!!.message)
            val file = File.createTempFile("dsd-provider-", ".jsonl")
            try {
                val log = DecodeFailureLog(file)
                log.record(DecodeFailureEntry(1, uri, "test", "test", checkNotNull(recorder.providerFor(uri)), reason))
                assertEquals(p.id, log.entries().single().provider)
                assertEquals(reason, log.entries().single().error)
            } finally { file.delete() }
        }
    }

    @Test fun registryAndSniffExposeDsdWithoutChangingPlatformPriority() {
        val p = KotlinDsdProvider()
        for (extension in listOf("dsf", "dff")) {
            assertEquals(p.id, defaultDecoderRegistry(CodecProbe { false }).resolve(FormatKey(extension, extension))?.id)
            assertTrue(p.isAvailable(FormatKey(extension.uppercase(), extension)))
            assertNull(defaultDecoderRegistry(CodecProbe { false }).skipReason(FormatKey(extension, extension)))
        }
        assertFalse(p.isAvailable(FormatKey("wv", "wv")))
        assertEquals("platform", defaultDecoderRegistry(CodecProbe { true }).resolve(FormatKey("flac", "flac"))?.id)
        val e = p.extractors().single()()
        try {
            for (magic in listOf("DSD ", "FRM8")) {
                assertTrue(e.sniff(input(magic.toByteArray())))
                for (n in 0..3) assertFalse(e.sniff(input(magic.toByteArray().copyOf(n))))
            }
            for (magic in listOf("wvpk", "fLaC", "RIFF")) {
                assertFalse(e.sniff(input(magic.toByteArray())))
                assertNull(p.refusal(magic.toByteArray()))
            }
        } finally { e.release() }
    }
    private fun decode(p: DecoderProvider, data: ByteArray, extractor: Extractor = p.extractors().single()()): Output {
        val output = Output(true)
        val inp = input(data)
        val holder = PositionHolder()
        extractor.init(output)
        try {
            while (true) {
                val result = extractor.read(inp, holder)
                if (result == Extractor.RESULT_END_OF_INPUT) return output
                assertEquals(Extractor.RESULT_CONTINUE, result)
            }
        } finally { extractor.release() }
    }
    private fun input(data: ByteArray) = FakeExtractorInput.Builder().setData(data).build()
    private class Output(capture: Boolean) : ExtractorOutput {
        val sink = Sink(capture)
        var map: SeekMap? = null
        override fun track(id: Int, type: Int): TrackOutput { assertEquals(C.TRACK_TYPE_AUDIO, type); return sink }
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) { map = seekMap }
    }
    private class Sink(private val capture: Boolean) : TrackOutput {
        var format: Format? = null
        var bytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val pcm = java.io.ByteArrayOutputStream()
        val times = mutableListOf<Long>()
        var borrowed: ByteArray? = null
        override fun format(format: Format) { this.format = format }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (capture) { digest.update(data.data, data.position, length); pcm.write(data.data, data.position, length) }
            borrowed?.let { assertSame("PCM packing storage must be reused", it, data.data) }
            borrowed = data.data
            data.skipBytes(length); bytes += length
        }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            throw AssertionError("Expected decoded PCM")
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) { times += timeUs }
    }
    companion object {
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        @JvmStatic @BeforeClass fun enableParserBoundsChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true) }
        @JvmStatic @AfterClass fun restoreParserBoundsChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null) }
    }
}
