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
import com.amwolfstein.sistrum.codecs.codec.wavpack.ErrorCode
import com.amwolfstein.sistrum.codecs.codec.wavpack.WavPackException
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureLog
import com.amwolfstein.sistrum.player.decoders.*
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import com.amwolfstein.sistrum.player.media3.tagSniff
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

class KotlinWavPackProviderTest {
    private val provider = KotlinWavPackProvider()
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "codecs/src/test/resources/wavpack/libwavpack.tsv").isFile }
    private val oracleDir = System.getenv("SISTRUM_ORACLE_DIR")
        ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle"
    private val corpus = System.getProperty("waxflow.corpus") ?: System.getenv("WAXFLOW_CORPUS") ?: "$oracleDir/corpus"
    private val owned = System.getProperty("oracle.owned") ?: "$oracleDir/owned"

    private fun manifest(path: String, pin: String): List<Map<String, String>> {
        val lines = File(root, path).readLines()
        assertTrue("Oracle pin changed: $path", lines.any { it.endsWith("\t$pin") })
        val data = lines.filter { !it.startsWith('#') && it.isNotEmpty() }
        val columns = data.first().split('\t')
        return data.drop(1).map { columns.zip(it.split('\t')).toMap() }
    }
    private val legacy by lazy {
        manifest("androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv", "b7857aff88820ad37936026421d1641e64611dbe")
            .filter { it.getValue("file").endsWith(".wv") || it.getValue("file").endsWith(".wvc") }
    }
    private val extended by lazy {
        manifest("codecs/src/test/resources/wavpack/libwavpack.tsv", "4827b9889665b937b6ed71b9c6c0123152cd7a02")
            .filter { it.getValue("correction") == "false" }
    }
    private fun source(row: Map<String, String>): ByteArray {
        val name = row.getValue("file")
        val file = if (name.startsWith("generated/")) File("$owned/wavpack", name) else File(corpus, name)
        assertTrue("Missing corpus $file; rebuild: python3 scripts/oracle-corpus.py --rebuild", file.isFile)
        val data = file.readBytes()
        assertEquals(name, row["file_sha256"] ?: row.getValue("sha256"), hash(data))
        return data
    }

    @Test fun everyOriginalWavPackFixtureMatchesItsCurrentOracleOrNamedFailure() {
        assertEquals(67, legacy.size)
        for (row in legacy) {
            val name = row.getValue("file")
            val replacement = extended.singleOrNull { it.getValue("file") == name }
            if (replacement != null) {
                checkExtended(replacement)
            } else if (row.getValue("status") == "ok") {
                checkPcm(row, row.getValue("pcm_sha256"), row.getValue("frames").toLong(),
                    row.getValue("rate").toInt(), row.getValue("channels").toInt(), row.getValue("bits").toInt(), false)
            } else {
                val data = source(row)
                val extractor = provider.extractors().single()()
                try {
                    val recognized = extractor.sniff(input(data))
                    if (name.endsWith("vers-397-hybrid.wvc")) assertFalse(name, recognized) else assertTrue(name, recognized)
                    val error = assertThrows(ParserException::class.java) { decode(data) }
                    val expected = if (name.endsWith(".wvc") && row.getValue("status").contains("hybrid streams"))
                        "wavpack: block has no wv bitstream" else if (!recognized) "wavpack: not a WavPack file"
                        else row.getValue("status").removePrefix("refused: waxflow: ")
                    assertEquals(name, expected, error.cause?.message)
                    assertTrue(error.cause is WavPackException)
                    if ((error.cause as WavPackException).code == ErrorCode.UNSUPPORTED)
                        assertEquals(expected, provider.refusal(data.take(provider.refusalHeaderBytes).toByteArray()))
                } finally { extractor.release() }
            }
        }
    }

    @Test fun everyExtendedStandaloneFixtureMatchesLibwavpackOracleOrExplicitDsdRefusal() {
        assertTrue(extended.isNotEmpty())
        extended.forEach(::checkExtended)
    }

    private fun checkExtended(row: Map<String, String>) {
        val info = row.getValue("info").split(';')
        if (info.getOrNull(1) == "1") {
            val data = source(row)
            val reason = "wavpack: DSD output requires PCM conversion"
            assertEquals(reason, provider.refusal(data.take(provider.refusalHeaderBytes).toByteArray()))
            val error = assertThrows(ParserException::class.java) { decode(data) }
            assertFalse(error.contentIsMalformed)
            assertEquals(reason, error.cause?.message)
            assertEquals(ErrorCode.UNSUPPORTED, (error.cause as WavPackException).code)
        } else if (row.getValue("exit") != "0") {
            val error = assertThrows(ParserException::class.java) { decode(source(row)) }
            val legacyRefusal = row.getValue("info").isEmpty()
            assertEquals(!legacyRefusal, error.contentIsMalformed)
            assertEquals(if (legacyRefusal) ErrorCode.UNSUPPORTED else ErrorCode.MALFORMED,
                (error.cause as WavPackException).code)
            if (legacyRefusal) {
                val oldRow = legacy.single { it.getValue("file") == row.getValue("file") }
                assertEquals(oldRow.getValue("status").removePrefix("refused: waxflow: "), error.cause?.message)
                assertEquals(error.cause?.message, provider.refusal(source(row).take(provider.refusalHeaderBytes).toByteArray()))
            }
        } else {
            checkPcm(row, row.getValue("raw_sha256"), info[5].toLong(), info[0].toInt(), info[3].toInt(),
                ((info[1].toInt() + 7) / 8) * 8, info[2] == "float")
        }
    }

    private fun checkPcm(row: Map<String, String>, sha: String, frames: Long, rate: Int, channels: Int, bits: Int, float: Boolean) {
        val output = decode(source(row))
        assertEquals(row.getValue("file"), sha, output.sink.digest.digest().hex())
        assertEquals(row.getValue("file"), frames * channels * (bits / 8), output.sink.bytes)
        assertEquals(MimeTypes.AUDIO_RAW, output.sink.format!!.sampleMimeType)
        assertEquals(rate, output.sink.format!!.sampleRate)
        assertEquals(channels, output.sink.format!!.channelCount)
        assertEquals(if (float) C.ENCODING_PCM_FLOAT else when (bits) {
            8 -> C.ENCODING_PCM_8BIT; 16 -> C.ENCODING_PCM_16BIT; 24 -> C.ENCODING_PCM_24BIT; else -> C.ENCODING_PCM_32BIT
        }, output.sink.format!!.pcmEncoding)
        assertEquals(0, output.sink.format!!.encoderDelay)
        assertEquals(0, output.sink.format!!.encoderPadding)
        assertEquals(frames * 1_000_000 / rate, output.map!!.durationUs)
        assertTrue(output.map!!.isSeekable)
    }

    @Test fun registryAddsWavPackAfterPlatformAndPreservesExistingProviders() {
        val providers = defaultDecoderProviders(CodecProbe { false })
        assertEquals(listOf("platform", "kotlin-wavpack", "kotlin-aiff"), providers.map { it.id })
        assertEquals(provider.id, defaultDecoderRegistry(CodecProbe { false }).resolve(FormatKey("wv", "wv"))?.id)
        assertTrue(provider.isAvailable(FormatKey("WV", "wv")))
        assertFalse(provider.isAvailable(FormatKey("flac", "flac")))
    }

    @Test fun sniffUsesMagicAndRejectsUnrelatedOrTruncatedHeaders() {
        val extractor = provider.extractors().single()()
        try {
            for (size in 0..3) assertFalse(extractor.sniff(input("wvpk".toByteArray().copyOf(size))))
            assertTrue(extractor.sniff(input("wvpk".toByteArray())))
            for (data in listOf("MZ unrelated executable".toByteArray(), "RIFFxxxxWAVEdataxxxxPCM".toByteArray(), "fLaC".toByteArray())) {
                assertFalse(extractor.sniff(input(data)))
                assertNull(provider.refusal(data))
            }
        } finally { extractor.release() }
    }

    @Test fun refusedExtractorRecordsProviderIdAndSpecificReasonInFailureLog() {
        val row = legacy.first { it.getValue("file").endsWith("vers-10.wv") }
        val data = source(row)
        val recorder = ProviderRecorder()
        val uri = "file:///refused.wv"
        val extractor = tagSniff(provider.extractors().single()()) { recorder.record(uri, provider.id) }
        val file = File.createTempFile("wavpack-failure-", ".jsonl")
        try {
            assertTrue(extractor.sniff(input(data)))
            val error = assertThrows(ParserException::class.java) { decode(data, extractor = extractor) }
            val log = DecodeFailureLog(file)
            log.record(DecodeFailureEntry(1, "refused.wv", "wv", "wv", checkNotNull(recorder.providerFor(uri)), error.cause!!.message!!))
            assertEquals(provider.id, log.entries().single().provider)
            assertEquals(provider.refusal(data), log.entries().single().error)
            assertTrue(log.exportText().contains("provider=kotlin-wavpack"))
        } finally { extractor.release(); file.delete() }
    }

    @Test fun hostRealtimeFactorAfterFiveWarmupsAndFiveMeasuredProviderDecodes() {
        val rows = (legacy.filter { it.getValue("status") == "ok" }.map { row ->
            Triple(row, row.getValue("frames").toLong(), row.getValue("rate").toInt())
        } + extended.filter { it.getValue("exit") == "0" && it.getValue("info").split(';')[1] != "1" }.map { row ->
            val info = row.getValue("info").split(';'); Triple(row, info[5].toLong(), info[0].toInt())
        }).distinctBy { it.first.getValue("file") }
        val report = File(root, "androidApp/build/reports/wavpack-provider-speed.tsv")
        report.parentFile.mkdirs()
        val lines = mutableListOf("file\taudio_seconds\tmedian_decode_seconds\trtf\tspeed_x")
        for ((row, frames, rate) in rows) {
            val data = source(row)
            repeat(5) { decode(data, capture = false) }
            val times = List(5) { timedDecode(data) }.sorted()
            val seconds = times[2] / 1e9
            val audio = frames.toDouble() / rate
            val rtf = seconds / audio
            assertTrue("${row["file"]}: slower than realtime ($rtf)", rtf < 1.0)
            lines += "${row["file"]}\t$audio\t$seconds\t$rtf\t${1 / rtf}"
        }
        report.writeText(lines.joinToString("\n", postfix = "\n"))
        println("WavPack provider speed: ${rows.size} files; report: $report; host proxy only")
    }

    private fun timedDecode(data: ByteArray): Long {
        val extractor = provider.extractors().single()()
        val input = input(data)
        val holder = PositionHolder()
        extractor.init(Output(false))
        try {
            // Opening/format publication outside the timer; cache reads, packing and output inside.
            assertEquals(Extractor.RESULT_CONTINUE, extractor.read(input, holder))
            val start = System.nanoTime()
            while (extractor.read(input, holder) != Extractor.RESULT_END_OF_INPUT) { }
            return System.nanoTime() - start
        } finally { extractor.release() }
    }
    private fun decode(data: ByteArray, capture: Boolean = true, extractor: Extractor = provider.extractors().single()()): Output {
        val output = Output(capture)
        val input = input(data)
        val holder = PositionHolder()
        extractor.init(output)
        try {
            while (true) {
                val result = extractor.read(input, holder)
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
        override fun format(format: Format) { this.format = format }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (capture) digest.update(data.data, data.position, length)
            data.skipBytes(length); bytes += length
        }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            throw AssertionError("Expected decoded PCM")
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) = Unit
    }
    companion object {
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        @JvmStatic @BeforeClass fun enableParserBoundsChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true) }
        @JvmStatic @AfterClass fun restoreParserBoundsChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null) }
    }
}
