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
import com.amwolfstein.sistrum.codecs.codec.ape.ApeException
import com.amwolfstein.sistrum.codecs.codec.musepack.MusepackException
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureLog
import com.amwolfstein.sistrum.player.decoders.*
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import com.amwolfstein.sistrum.player.media3.tagSniff
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

class KotlinApeMusepackProviderTest {
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
            .filter { it.getValue("file").endsWith(".ape") || it.getValue("file").endsWith(".mpc") }
    }
    private fun provider(row: Map<String, String>): DecoderProvider =
        if (row.getValue("file").endsWith(".ape")) KotlinApeProvider() else KotlinMusepackProvider()
    private fun source(row: Map<String, String>): ByteArray {
        val file = File(corpus, row.getValue("file"))
        assertTrue("Missing $file; rebuild: python3 scripts/oracle-corpus.py --rebuild", file.isFile)
        return file.readBytes().also { assertEquals(row.getValue("file"), row.getValue("file_sha256"), hash(it)) }
    }

    @Test fun everyApeFixtureMatchesOraclePcmFormatDurationAndTrimmedSampleCount() {
        val fixtures = rows.filter { it.getValue("file").endsWith(".ape") }
        assertTrue("APE corpus missing", fixtures.size >= 10)
        fixtures.forEach(::checkOracle)
    }
    @Test fun everyMusepackSv7Sv8FixtureMatchesOracleFloatBitsAndGaplessLength() {
        val fixtures = rows.filter { it.getValue("file").endsWith(".mpc") }
        assertTrue("Musepack corpus missing", fixtures.size >= 32)
        val versions = fixtures.map { source(it).take(4).toByteArray().decodeToString() }.toSet()
        assertTrue(versions.any { it.startsWith("MP+") })
        assertTrue(versions.contains("MPCK"))
        fixtures.forEach(::checkOracle)
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
        assertEquals(if (row.getValue("sample_format") == "float") C.ENCODING_PCM_FLOAT else when (bits) {
            8 -> C.ENCODING_PCM_8BIT; 16 -> C.ENCODING_PCM_16BIT; else -> C.ENCODING_PCM_24BIT
        }, format.pcmEncoding)
        assertEquals(0, format.encoderDelay)
        assertEquals(0, format.encoderPadding)
        assertEquals(frames * 1_000_000 / rate, output.map!!.durationUs)
        assertTrue(output.map!!.isSeekable)
        assertEquals(0L, output.sink.times.first())
        assertTrue(output.sink.times.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun seeksForwardBackwardAndAfterEofMatchOracleVerifiedPcmForEveryFixture() {
        for (row in rows) {
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

    @Test fun registryAddsBothProvidersWithPlatformFirstAndRetainsWavpackAndAiff() {
        val providers = defaultDecoderProviders(CodecProbe { false })
        assertEquals(listOf("platform", "kotlin-wavpack", "kotlin-ape", "kotlin-musepack", "kotlin-wma", "kotlin-dsd", "kotlin-pcm-variants", "kotlin-alac", "kotlin-aiff"), providers.map { it.id })
        for ((extension, p) in listOf("ape" to KotlinApeProvider(), "mpc" to KotlinMusepackProvider())) {
            assertEquals(p.id, defaultDecoderRegistry(CodecProbe { false }).resolve(FormatKey(extension, extension))?.id)
            assertTrue(p.isAvailable(FormatKey(extension.uppercase(), extension)))
            assertFalse(p.isAvailable(FormatKey("flac", "flac")))
        }
        assertEquals("platform", defaultDecoderRegistry(CodecProbe { true }).resolve(FormatKey("flac", "flac"))?.id)
    }

    @Test fun sniffRecognizesFramingAndRejectsUnrelatedAndShortHeaders() {
        for ((p, magics) in listOf(KotlinApeProvider() to listOf("MAC ", "MACF"), KotlinMusepackProvider() to listOf("MPCK", "MP+\u0007", "MP+\u0004"))) {
            val e = p.extractors().single()()
            try {
                for (magic in magics) {
                    assertTrue(e.sniff(input(magic.toByteArray())))
                    for (n in 0..3) assertFalse(e.sniff(input(magic.toByteArray().copyOf(n))))
                    assertTrue(e.sniff(input("ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray() + magic.toByteArray())))
                }
                for (data in listOf("fLaC".toByteArray(), "RIFFxxxxWAVE".toByteArray(), "unrelated".toByteArray())) {
                    assertFalse(e.sniff(input(data)))
                    assertNull(p.refusal(data))
                }
            } finally { e.release() }
        }
    }

    @Test fun namedTypedRefusalsReachNormalParserPathAndProviderFailureLog() {
        val ape = source(rows.first { it.getValue("file").endsWith(".ape") }).also {
            // Force an unsupported version while keeping the committed valid descriptor layout.
            it[4] = 0xa0.toByte(); it[5] = 0x0f // version 4000
        }
        val oldMpc = ByteArray(32).also { "MP+".toByteArray().copyInto(it); it[3] = 4 }
        for ((p, data, reason) in listOf(
            Triple(KotlinApeProvider(), ape, "ape: stream version 4000 is past the supported 3990"),
            Triple(KotlinMusepackProvider(), oldMpc, "musepack: Musepack SV4 is not supported: only SV7 and SV8 are"),
        )) {
            assertEquals(reason, p.refusal(data.take(p.refusalHeaderBytes).toByteArray()))
            val recorder = ProviderRecorder()
            val uri = "file:///refused.${p.id}"
            val extractor = tagSniff(p.extractors().single()()) { recorder.record(uri, p.id) }
            assertTrue(extractor.sniff(input(data)))
            val error = assertThrows(ParserException::class.java) { decode(p, data, extractor) }
            assertFalse(error.contentIsMalformed)
            assertEquals(reason, error.cause!!.message)
            if (p is KotlinApeProvider) assertEquals(ApeException.Reason.UNSUPPORTED, (error.cause as ApeException).reason)
            else assertTrue((error.cause as MusepackException).unsupported)
            val file = File.createTempFile("ape-mpc-refusal-", ".jsonl")
            try {
                val log = DecodeFailureLog(file)
                log.record(DecodeFailureEntry(1, "refused", "test", "test", checkNotNull(recorder.providerFor(uri)), reason))
                assertEquals(p.id, log.entries().single().provider)
                assertEquals(reason, log.entries().single().error)
                assertTrue(log.exportText().contains("provider=${p.id}"))
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
