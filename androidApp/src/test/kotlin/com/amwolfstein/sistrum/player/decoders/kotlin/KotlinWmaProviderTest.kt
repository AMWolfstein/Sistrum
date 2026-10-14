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
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureLog
import com.amwolfstein.sistrum.player.decoders.*
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import com.amwolfstein.sistrum.player.media3.tagSniff
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

class KotlinWmaProviderTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").isFile }
    private val oracleDir = System.getenv("SISTRUM_ORACLE_DIR")
        ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle"
    private val corpus = System.getProperty("waxflow.corpus") ?: System.getenv("WAXFLOW_CORPUS") ?: "$oracleDir/corpus"
    private val rows by lazy {
        val lines = File(root, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").readLines()
        assertTrue(lines.any { it == "# waxflow_fork_commit\tb7857aff88820ad37936026421d1641e64611dbe" })
        val data = lines.filter { !it.startsWith('#') && it.isNotEmpty() }
        val columns = data.first().split('\t')
        data.drop(1).map { columns.zip(it.split('\t')).toMap() }
            .filter { it.getValue("file").startsWith("waxflow-wma") || it.getValue("file") == "waxflow-testdata/sine-s16.wma" }
    }
    private fun provider(row: Map<String, String>): DecoderProvider = KotlinWmaProvider()
    private fun source(row: Map<String, String>): ByteArray {
        val file = File(corpus, row.getValue("file"))
        assertTrue("Missing $file; rebuild: python3 scripts/oracle-corpus.py --rebuild", file.isFile)
        return file.readBytes().also { assertEquals(row.getValue("file"), row.getValue("file_sha256"), hash(it)) }
    }

    @Test fun everyWmaFamilyFixtureMatchesOraclePcmFormatAndSampleCount() {
        for (family in listOf("wma", "wmalossless", "wmapro", "wmavoice")) {
            assertTrue("Missing $family corpus", rows.any { it.getValue("file").startsWith("waxflow-$family-tests/") })
        }
        rows.filter { it.getValue("status") == "ok" }.forEach(::checkOracle)
    }
    private fun checkOracle(row: Map<String, String>) {
        assertEquals("ok", row.getValue("status"))
        val p = provider(row)
        val data = source(row)
        val extractor = p.extractors().single()()
        try { assertTrue(row.getValue("file"), extractor.sniff(input(data))) } finally { extractor.release() }
        val output = decode(p, data)
        assertEquals(row.getValue("file"), row.getValue("pcm_sha256"), output.sink.digest.digest().hex())
        val frames = row.getValue("frames").toLong()
        val channels = row.getValue("channels").toInt()
        val bits = row.getValue("bits").toInt()
        val rate = row.getValue("rate").toInt()
        assertEquals(frames * channels * (bits / 8), output.sink.bytes)
        val format = checkNotNull(output.sink.format)
        assertEquals(MimeTypes.AUDIO_RAW, format.sampleMimeType)
        assertEquals(rate, format.sampleRate)
        assertEquals(channels, format.channelCount)
        // FR-002: retain the reference's native PCM representation. The oracle generator
        // only recognizes outer WAVE tag 3 as float; multichannel Pro uses tag 0xfffe.
        // WmaProOracleTest verifies those very same hashes as raw FloatBuffer bits.
        val lossless = row.getValue("file").startsWith("waxflow-wmalossless-tests/")
        assertEquals(if (lossless) {
            assertTrue(bits == 16 || bits == 24)
            if (bits == 16) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_24BIT
        } else {
            assertEquals(32, bits)
            C.ENCODING_PCM_FLOAT
        }, format.pcmEncoding)
        assertEquals(0, format.encoderDelay)
        assertEquals(0, format.encoderPadding)
        assertTrue(output.map!!.durationUs == C.TIME_UNSET || output.map!!.durationUs >= 0)
        assertTrue(output.map!!.isSeekable)
        assertEquals(0L, output.sink.times.first())
        assertTrue(output.sink.times.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun seeksForwardBackwardAndAfterEofMatchOracleVerifiedPcmForEveryFixture() {
        for (row in rows.filter { it.getValue("status") == "ok" }) {
            val p = provider(row)
            val data = source(row)
            val linear = decode(p, data).sink.pcm.toByteArray()
            assertEquals(row.getValue("pcm_sha256"), hash(linear))
            val total = row.getValue("frames").toLong()
            val rate = row.getValue("rate").toInt()
            val frameBytes = row.getValue("channels").toInt() * row.getValue("bits").toInt() / 8
            val extractor = p.extractors().single()()
            val inp = input(data)
            val holder = PositionHolder()
            try {
                extractor.init(Output(false))
                while (extractor.read(inp, holder) != Extractor.RESULT_END_OF_INPUT) { }
                for (target in listOf(total / 2, 1L, total - 1, 0L, total)) {
                    // Round upward to a microsecond that maps back to this exact sample.
                    val us = (target * 1_000_000 + rate - 1) / rate
                    val out = Output(true)
                    extractor.init(out)
                    extractor.seek(0, us)
                    while (extractor.read(inp, holder) != Extractor.RESULT_END_OF_INPUT) { }
                    assertArrayEquals("${row["file"]} at $target", linear.copyOfRange((target * frameBytes).toInt(), linear.size), out.sink.pcm.toByteArray())
                    if (target < total) assertEquals(target * 1_000_000 / rate, out.sink.times.first())
                    else assertTrue(out.sink.times.isEmpty())
                }
            } finally { extractor.release() }
        }
    }

    @Test fun understatedAndUnknownAsfDurationNeverTruncatePcmOrClampSeeks() {
        for (family in listOf("wma", "wmalossless", "wmapro", "wmavoice")) {
            val row = rows.first { it.getValue("file").startsWith("waxflow-$family-tests/") && it.getValue("status") == "ok" }
            val original = source(row)
            val linear = decode(provider(row), original).sink.pcm.toByteArray()
            assertEquals(row.getValue("pcm_sha256"), hash(linear))
            val guid = "a1dcab8c47a9cf118ee400c00c205365".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val at = original.indices.first { offset ->
                offset + guid.size <= original.size && guid.indices.all { original[offset + it] == guid[it] }
            } + 24
            val rate = row.getValue("rate").toInt()
            val total = row.getValue("frames").toLong()
            val target = total / 2
            val frameBytes = row.getValue("channels").toInt() * row.getValue("bits").toInt() / 8
            for (unknown in listOf(false, true)) {
                val data = original.copyOf()
                val fields = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                // File Properties: play duration in 100ns, preroll in ms.
                val preroll = fields.getLong(at + 56)
                fields.putLong(at + 40, if (unknown) 0 else preroll * 10000 + 10000)
                val e = provider(row).extractors().single()()
                val inp = input(data)
                val holder = PositionHolder()
                try {
                    val all = Output(true)
                    e.init(all)
                    while (e.read(inp, holder) != Extractor.RESULT_END_OF_INPUT) { }
                    assertArrayEquals("$family full PCM, unknown=$unknown", linear, all.sink.pcm.toByteArray())
                    assertEquals(if (unknown) C.TIME_UNSET else ((rate + 500) / 1000).toLong() * 1_000_000 / rate, all.map!!.durationUs)
                    val us = (target * 1_000_000 + rate - 1) / rate
                    assertEquals(target * 1_000_000 / rate, all.map!!.getSeekPoints(us).first.timeUs)
                    val tail = Output(true)
                    e.init(tail)
                    e.seek(0, us)
                    while (e.read(inp, holder) != Extractor.RESULT_END_OF_INPUT) { }
                    assertArrayEquals("$family seek beyond advisory duration", linear.copyOfRange((target * frameBytes).toInt(), linear.size), tail.sink.pcm.toByteArray())
                } finally { e.release() }
            }
        }
    }

    @Test fun registryOffersWmaFamilyAfterPlatformAndPreservesOtherProviders() {
        val providers = defaultDecoderProviders(CodecProbe { false })
        assertEquals(listOf("platform", "kotlin-wavpack", "kotlin-ape", "kotlin-musepack", "kotlin-wma", "kotlin-dsd", "kotlin-pcm-variants", "kotlin-alac", "kotlin-aiff"), providers.map { it.id })
        val p = KotlinWmaProvider()
        assertTrue(p.isAvailable(FormatKey("WMA", "wma")))
        assertFalse(p.isAvailable(FormatKey("flac", "flac")))
        assertEquals(p.id, defaultDecoderRegistry(CodecProbe { false }).resolve(FormatKey("wma", "wma"))?.id)
        assertEquals("platform", defaultDecoderRegistry(CodecProbe { true }).resolve(FormatKey("flac", "flac"))?.id)
    }

    @Test fun sniffOnlyAcceptsCompleteAsfGuidAtStart() {
        val p = KotlinWmaProvider()
        val data = source(rows.first { it.getValue("status") == "ok" })
        val e = p.extractors().single()()
        try {
            assertTrue(e.sniff(input(data)))
            for (n in 0..15) assertFalse(e.sniff(input(data.copyOf(n))))
            for (other in listOf("fLaC".toByteArray(), "RIFFxxxxWAVE".toByteArray(), byteArrayOf(0) + data)) {
                assertFalse(e.sniff(input(other)))
                assertNull(p.refusal(other))
            }
        } finally { e.release() }
    }

    @Test fun everyCommittedRefusalKeepsCodecNameTypedCauseAndFailureLog() {
        val refused = rows.filter { it.getValue("status") != "ok" }
        for (family in listOf("wma", "wmalossless", "wmapro", "wmavoice")) {
            assertTrue("Missing $family refusals", refused.any { it.getValue("file").startsWith("waxflow-$family-tests/") })
        }
        for (row in refused) {
            val p = provider(row)
            val data = source(row)
            val reason = row.getValue("status").removePrefix("refused: waxflow: ")
            assertEquals(row.getValue("file"), reason, p.refusal(data.take(p.refusalHeaderBytes).toByteArray()))
            val recorder = ProviderRecorder()
            val uri = "file:///refused.wma"
            val extractor = tagSniff(p.extractors().single()()) { recorder.record(uri, p.id) }
            assertTrue(extractor.sniff(input(data)))
            val error = assertThrows(ParserException::class.java) { decode(p, data, extractor) }
            assertFalse(error.contentIsMalformed)
            assertEquals(reason, error.cause!!.message)
            assertTrue(error.cause is com.amwolfstein.sistrum.codecs.container.asf.AsfException)
            assertTrue((error.cause as com.amwolfstein.sistrum.codecs.container.asf.AsfException).unsupported)
            val file = File.createTempFile("wma-refusal-", ".jsonl")
            try {
                val log = DecodeFailureLog(file)
                log.record(DecodeFailureEntry(1, "refused", "wma", "wma", checkNotNull(recorder.providerFor(uri)), reason))
                assertEquals(p.id, log.entries().single().provider)
                assertEquals(reason, log.entries().single().error)
                assertTrue(log.exportText().contains("provider=kotlin-wma"))
            } finally { file.delete() }
        }
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
