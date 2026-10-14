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

import androidx.media3.extractor.wav.WavExtractor
import com.amwolfstein.sistrum.player.media3.ProviderExtractorsFactory
import com.amwolfstein.sistrum.player.decoders.aiff.KotlinAiffProvider

class KotlinPcmVariantsProviderTest {
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
            .filter { row ->
                val name = row.getValue("file")
                name.endsWith(".wav") && (name.contains("msadpcm") || name.contains("alaw") || name.contains("ulaw")) ||
                    name.endsWith(".aifc") && (name.contains("ima4") || name.contains("alaw") || name.contains("ulaw"))
            }
    }
    private fun provider(row: Map<String, String>): DecoderProvider = KotlinPcmVariantsProvider()
    private fun source(row: Map<String, String>): ByteArray {
        val file = File(corpus, row.getValue("file"))
        assertTrue("Missing $file; rebuild: python3 scripts/oracle-corpus.py --rebuild", file.isFile)
        return file.readBytes().also { assertEquals(row.getValue("file"), row.getValue("file_sha256"), hash(it)) }
    }

    @Test fun allFourteenOriginalFixturesMatchExactOraclePcmFormatAndSampleCounts() {
        assertEquals(14, rows.size)
        rows.forEach(::checkOracle)
    }

    @Test fun platformWavSelectionFallsBackOnlyWherePlatformCannotDecode() {
        for (available in listOf(true, false)) {
            val providers = defaultDecoderProviders(CodecProbe { available })
            val factory = ProviderExtractorsFactory(providers, object : ExtractorsFactory {
                override fun createExtractors(): Array<Extractor> = arrayOf(WavExtractor())
            }, ProviderRecorder())
            for (row in rows) {
                val data = source(row)
                val extractors = factory.createExtractors()
                try {
                    val selected = extractors.first { it.sniff(input(data)) }
                    val platform = available && row.getValue("file").endsWith(".wav") && !row.getValue("file").contains("msadpcm")
                    assertEquals("${row["file"]} platform=$available", platform, selected.underlyingImplementation is WavExtractor)
                    if (!platform) {
                        val output = decode(KotlinPcmVariantsProvider(), data, selected)
                        assertEquals(row["pcm_sha256"], hash(output.sink.pcm.toByteArray()))
                    }
                } finally { extractors.forEach { it.release() } }
            }
            val registry = defaultDecoderRegistry(CodecProbe { available })
            for (codec in listOf("alaw", "ulaw")) assertEquals(if (available) "platform" else "kotlin-pcm-variants", registry.resolve(FormatKey("wav", codec))?.id)
            assertEquals("platform", registry.resolve(FormatKey("wav", "ima"))?.id)
            assertEquals("platform", registry.resolve(FormatKey("wav", "wav"))?.id)
            assertEquals("kotlin-pcm-variants", registry.resolve(FormatKey("wav", "msadpcm"))?.id)
            assertEquals("kotlin-aiff", registry.resolve(FormatKey("aiff", "aiff"))?.id)
        }
    }

    @Test fun aiffProviderLiftsOnlySupportedCompressedRefusalsAndKeepsLegacyNamedFailures() {
        val legacy = KotlinAiffProvider()
        rows.filter { it.getValue("file").endsWith(".wav") }.forEach {
            val data = source(it)
            val extractors = legacy.extractors().map { factory -> factory() }
            try { assertTrue(extractors.none { extractor -> extractor.sniff(input(data)) }) }
            finally { extractors.forEach { extractor -> extractor.release() } }
        }
        rows.filter { it.getValue("file").endsWith(".aifc") }.forEach {
            val data = source(it)
            assertNull(legacy.refusal(data))
            val extractors = legacy.extractors().map { factory -> factory() }
            try {
                val selected = extractors.first { extractor -> extractor.sniff(input(data)) }
                assertEquals(it["pcm_sha256"], hash(decode(legacy, data, selected).sink.pcm.toByteArray()))
            } finally { extractors.forEach { extractor -> extractor.release() } }
        }
        val data = source(rows.first { it.getValue("file").endsWith(".aifc") })
        val comm = data.toString(Charsets.ISO_8859_1).indexOf("COMM")
        for ((compression, reason) in listOf("MAC3" to "compressed AIFF-C (MAC3)", "zzzz" to "compressed AIFF-C (zzzz)", "fl32" to "float AIFF-C (fl32)")) {
            val changed = data.copyOf().also { compression.toByteArray().copyInto(it, comm + 26) }
            assertEquals(reason, legacy.refusal(changed))
            assertFalse(KotlinPcmVariantsProvider().extractors().single()().sniff(input(changed)))
        }
    }

    @Test fun leadingMetadataBeyondHeaderProbeStillRoutesMsAdpcmAndCompressedAiff() {
        for (row in rows.filter { it.getValue("file").contains("msadpcm") || it.getValue("file").contains("ima4") }) {
            val data = source(row)
            val chunk = ByteArray(8200)
            "JUNK".toByteArray().copyInto(chunk)
            java.nio.ByteBuffer.wrap(chunk).order(if (data[0] == 'F'.code.toByte()) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4, 8192)
            val changed = data.copyOfRange(0, 12) + chunk + data.copyOfRange(12, data.size)
            val e = KotlinPcmVariantsProvider().extractors().single()()
            try {
                val inp = input(changed)
                assertTrue(e.sniff(inp))
                assertEquals(0L, inp.position)
                assertEquals(0L, inp.peekPosition)
                assertEquals(row["pcm_sha256"], hash(decode(KotlinPcmVariantsProvider(), changed).sink.pcm.toByteArray()))
            } finally { e.release() }
        }
    }

    @Test fun wavImaAndPcmRemainWithPlatformAndTruncatedUnrelatedHeadersAreRejected() {
        val ms = source(rows.first { it.getValue("file").contains("msadpcm") })
        val fmt = ms.toString(Charsets.ISO_8859_1).indexOf("fmt ")
        for (tag in listOf(1, 3, 17)) {
            val data = ms.copyOf().also { it[fmt + 8] = tag.toByte(); it[fmt + 9] = 0 }
            val e = KotlinPcmVariantsProvider().extractors().single()()
            try { assertFalse(e.sniff(input(data))) } finally { e.release() }
            val factory = ProviderExtractorsFactory(defaultDecoderProviders(CodecProbe { false }), object : ExtractorsFactory {
                override fun createExtractors(): Array<Extractor> = arrayOf(WavExtractor())
            }, ProviderRecorder())
            val extractors = factory.createExtractors()
            try {
                val selected = extractors.first { it.sniff(input(data)) }
                assertTrue("WAV tag $tag retains platform priority", selected.underlyingImplementation is WavExtractor)
            } finally { extractors.forEach { it.release() } }
        }
        for (data in listOf(ByteArray(0), "RIFF".toByteArray(), "fLaCxxxxxxxx".toByteArray(), ms.copyOf(fmt + 9))) {
            val e = KotlinPcmVariantsProvider().extractors().single()()
            try { assertFalse(e.sniff(input(data))); assertNull(KotlinPcmVariantsProvider().refusal(data)) } finally { e.release() }
        }
    }

    @Test fun unsupportedMsChannelCountKeepsNamedTypedRefusal() {
        val data = source(rows.first { it.getValue("file").contains("msadpcm") })
        val fmt = data.toString(Charsets.ISO_8859_1).indexOf("fmt ")
        data[fmt + 10] = 3
        val p = KotlinPcmVariantsProvider()
        val reason = "wav: 3 channels of MS ADPCM, whose nibbles alternate between at most 2"
        assertEquals(reason, p.refusal(data.copyOf(minOf(data.size, p.refusalHeaderBytes))))
        val error = assertThrows(ParserException::class.java) { decode(p, data) }
        assertFalse(error.contentIsMalformed)
        assertTrue(error.cause is PcmVariantUnsupportedException)
        assertEquals(reason, error.cause!!.message)
        assertTrue(error.cause!!.cause is java.io.IOException)
    }

    @Test fun malformedCompressedDataKeepsProviderNameAndOriginalCauseInFailureLog() {
        val row = rows.first { it.getValue("file").contains("msadpcm") }
        val data = source(row)
        val start = data.toString(Charsets.ISO_8859_1).indexOf("data") + 8
        data[start] = 127 // invalid MS predictor index
        val p = KotlinPcmVariantsProvider()
        val recorder = ProviderRecorder()
        val uri = "file:///malformed.wav"
        val extractor = tagSniff(p.extractors().single()()) { recorder.record(uri, p.id) }
        assertTrue(extractor.sniff(input(data)))
        val error = assertThrows(ParserException::class.java) { decode(p, data, extractor) }
        assertTrue(error.contentIsMalformed)
        assertNotNull(error.cause)
        val file = File.createTempFile("pcm-variant-refusal-", ".jsonl")
        try {
            val log = DecodeFailureLog(file)
            log.record(DecodeFailureEntry(1, "malformed", "wav", "msadpcm", checkNotNull(recorder.providerFor(uri)), error.cause!!.message!!))
            assertEquals(p.id, log.entries().single().provider)
            assertEquals(error.cause!!.message, log.entries().single().error)
        } finally { file.delete() }
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
