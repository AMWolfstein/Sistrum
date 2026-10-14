@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.analysis

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.extractor.*
import androidx.media3.test.utils.FakeExtractorInput
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureSink
import com.amwolfstein.sistrum.player.decoders.*
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

/** Drives the actual offline extractor/capture path on the host; ExoPlayer scheduling is a device check. */
class OfflineDecoderKotlinFormatsTest {
    private val entries = mutableListOf<DecodeFailureEntry>()
    private val providers = defaultDecoderProviders(CodecProbe { false })
    private val decoder = OfflineDecoder(ContextWrapper(null), providers, decodeFailures = DecodeFailureSink { entries += it })
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").isFile }

    @Test fun newlyCoveredExtractorsFeedCaptureWithPinnedPcmAndFrameCounts() {
        val lines = File(root, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }
        val columns = lines.first().split('\t')
        val rows = lines.drop(1).map { columns.zip(it.split('\t')).toMap() }
        val corpus = System.getProperty("waxflow.corpus") ?: System.getenv("WAXFLOW_CORPUS")
            ?: "${System.getenv("SISTRUM_ORACLE_DIR") ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle"}/corpus"
        for (extension in listOf("wv", "ape", "mpc", "wma", "aiff", "wav")) {
            val row = rows.first {
                it.getValue("file").endsWith(".$extension") && it["status"] == "ok" &&
                    (extension != "aiff" || it["file"] == "waxflow-testdata/sine-f32.aiff")
            }
            val bytes = File(corpus, row.getValue("file")).readBytes()
            assertEquals(row["file_sha256"], hash(bytes))
            val factory = decoder.extractorsFactory(ProviderRecorder(), ExtractorsFactory { emptyArray() })
            val extractor = factory.createExtractors().first { candidate ->
                try { candidate.sniff(input(bytes)) } finally { candidate.release() }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var captured = 0L
            val capture = CaptureAudioSink(PcmConsumer { _, frames, rate, channels ->
                captured += frames
                assertEquals(row.getValue("rate").toInt(), rate)
                assertEquals(row.getValue("channels").toInt(), channels)
            })
            val track = object : TrackOutput {
                override fun format(format: Format) {
                    capture.configure(AudioSink.AudioSinkConfig.Builder(format).build())
                }
                override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                    digest.update(data.data, data.position, length)
                    assertTrue(capture.handleBuffer(ByteBuffer.wrap(data.data, data.position, length), 0L, 1))
                    data.skipBytes(length)
                }
                override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
                    throw AssertionError("Expected decoded PCM")
                override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) = Unit
            }
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput { assertEquals(C.TRACK_TYPE_AUDIO, type); return track }
                override fun endTracks() = Unit
                override fun seekMap(seekMap: SeekMap) = Unit
            })
            try {
                val source = input(bytes)
                val position = PositionHolder()
                while (extractor.read(source, position) != Extractor.RESULT_END_OF_INPUT) {
                    assertEquals(0L, position.position)
                }
                capture.playToEndOfStream()
                assertEquals(row["pcm_sha256"], digest.digest().hex())
                assertEquals(row.getValue("frames").toLong(), captured)
            } finally { extractor.release() }
        }
        assertTrue(entries.isEmpty())
    }

    @Test fun offlineFactoryOffersDsdProvidersAndRegistryUsesTheInjectedProviders() {
        for (magic in listOf("DSD ", "FRM8")) {
            val extractors = decoder.extractorsFactory(ProviderRecorder(), ExtractorsFactory { emptyArray() }).createExtractors()
            try {
                val matches = extractors.filter { it.sniff(input(magic.toByteArray(Charsets.US_ASCII))) }
                assertEquals("$magic selects exactly the DSD extractor", 1, matches.size)
            } finally { extractors.forEach { it.release() } }
            val empty = OfflineDecoder(ContextWrapper(null), emptyList(), decodeFailures = DecodeFailureSink {})
                .extractorsFactory(ProviderRecorder(), ExtractorsFactory { emptyArray() }).createExtractors()
            assertTrue(empty.isEmpty())
        }
    }

    @Test fun decodeRecordsItsProviderFailureEvenWhenTheSinkThrows() {
        val failure = DecodeOutcome.Failed("kotlin-wma", "unsupported channel layout")
        for (throwing in listOf(false, true)) {
            val decoder = object : OfflineDecoder(ContextWrapper(null), providers, decodeFailures = DecodeFailureSink {
                entries += it
                if (throwing) throw java.io.IOException("log unavailable")
            }) {
                override fun decodeFile(path: String, key: FormatKey?, consumer: PcmConsumer,
                    isCancelled: () -> Boolean, startUs: Long): DecodeOutcome = failure
            }
            assertSame(failure, decoder.decode("/private/refused.wma", FormatKey("wma", "wma"), PcmConsumer { _, _, _, _ -> }))
            assertEquals("kotlin-wma", entries.last().provider)
            assertEquals("unsupported channel layout", entries.last().error)
            assertEquals("refused.wma", entries.last().fileName)
        }
        assertEquals(2, entries.size)
    }

    @Test fun failureSinkCancellationPropagates() {
        val cancellation = CancellationException("cancel recording")
        val decoder = OfflineDecoder(ContextWrapper(null), providers, decodeFailures = DecodeFailureSink { throw cancellation })
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            decoder.recordOutcome("/private/refused", null, DecodeOutcome.Failed("kotlin-wma", "refused"))
        })
    }

    @Test fun nonzeroInitialPositionAndInterruptedLoadResumeWithExactOraclePcm() {
        val bytes = File(System.getProperty("waxflow.corpus") ?: System.getenv("WAXFLOW_CORPUS")
            ?: "${System.getenv("SISTRUM_ORACLE_DIR") ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle"}/corpus",
            "waxflow-testdata/sine-f32.aiff").readBytes()
        assertEquals("43ea21f0c2434da0709f629eac4ca5fb24d7df73573d6926b761a6ed92b234d8", hash(bytes))
        val extractor = com.amwolfstein.sistrum.player.decoders.aiff.KotlinAiffProvider().extractors().single().invoke()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int) = object : TrackOutput {
                override fun format(format: Format) = Unit
                override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                    digest.update(data.data, data.position, length); size += length; data.skipBytes(length)
                }
                override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
                    throw AssertionError("expected PCM")
                override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) = Unit
            }
            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) = Unit
        })
        val holder = PositionHolder()
        try {
            val nonzero = input(bytes).also { it.skipFully(10) }
            assertEquals(Extractor.RESULT_SEEK, extractor.read(nonzero, holder))
            assertEquals(0L, holder.position)
            val delegate = input(bytes)
            var cancel = false
            var interruptedReads = 0
            val interrupted = object : ExtractorInput by delegate {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (cancel) { Thread.currentThread().interrupt(); interruptedReads++ }
                    return delegate.read(buffer, offset, length)
                }
            }
            assertEquals(Extractor.RESULT_CONTINUE, extractor.read(interrupted, holder))
            cancel = true
            try { assertEquals(Extractor.RESULT_CONTINUE, extractor.read(interrupted, holder)) }
            finally { Thread.interrupted() }
            assertTrue("interruption reached cache I/O", interruptedReads > 0)
            digest.reset(); size = 0
            extractor.seek(0, 0)
            val reopened = input(bytes)
            while (extractor.read(reopened, holder) != Extractor.RESULT_END_OF_INPUT) {
                assertEquals(0L, holder.position)
            }
            assertEquals(2400 * 2 * 4, size)
            assertEquals("9d10a64422894b335514a8fc20e83869a4e5d8502a4b8558d415d1d8d097276a", digest.digest().hex())
        } finally { Thread.interrupted(); extractor.release() }
    }

    @Test fun providerRenderersReceiveTheCaptureSinkAndEvents() {
        val capture = CaptureAudioSink(PcmConsumer { _, _, _, _ -> })
        val handler = Handler()
        val listener = object : AudioRendererEventListener {}
        var calls = 0
        val provider = object : DecoderProvider {
            override val id = "test-renderer"
            override fun isAvailable(key: FormatKey) = true
            override fun extractors(): List<() -> Extractor> = emptyList()
            override fun audioRenderers(context: Context): List<Renderer> = throw AssertionError("AudioTrack path")
            override fun audioRenderers(context: Context, audioSink: AudioSink, eventHandler: Handler, eventListener: AudioRendererEventListener): List<Renderer> {
                assertSame(capture, audioSink); assertSame(handler, eventHandler); assertSame(listener, eventListener)
                calls++
                return emptyList()
            }
        }
        OfflineDecoder(ContextWrapper(null), listOf(provider), decodeFailures = DecodeFailureSink {}).audioRenderers(capture, handler, listener)
        assertEquals(1, calls)
        assertEquals("kotlin-alac", decoder.audioRenderers(capture, handler, listener).single().name)
    }

    @Test fun wrappedRefusalIsLoggedWithProviderAndFileNameOnly() {
        val refusal = IllegalArgumentException("WMA Pro: unsupported channel layout")
        val error = PlaybackException("Source error", java.io.IOException("demux failed", refusal), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        val outcome = decoder.failure(error, "kotlin-wma")
        assertEquals(DecodeOutcome.Failed("kotlin-wma", refusal.message!!), outcome)
        assertSame(outcome, decoder.recordOutcome("/private/library/refused.wma", FormatKey("wma", "wma"), outcome))
        val entry = entries.single()
        assertEquals("refused.wma", entry.fileName)
        assertEquals("wma", entry.format); assertEquals("wma", entry.codec)
        assertEquals("kotlin-wma", entry.provider); assertEquals(refusal.message, entry.error)
        assertTrue(entry.timeMs > 0)
    }

    @Test fun cancellationSuccessAndRegistrySkipsDoNotLogDecodeFailures() {
        assertEquals(DecodeOutcome.Cancelled, decoder.failure(CancellationException(), "kotlin-ape"))
        assertEquals(DecodeOutcome.Cancelled, decoder.failure(java.io.IOException(CancellationException()), "kotlin-ape"))
        for (result in listOf(DecodeOutcome.Cancelled, DecodeOutcome.Unsupported("unsupported format (TTA)"),
            DecodeOutcome.Completed(44100, 2, 0, "kotlin-ape"))) {
            assertSame(result, decoder.recordOutcome("/private/test", null, result))
        }
        assertTrue(entries.isEmpty())
        decoder.recordOutcome("/private/broken", null, decoder.failure(java.io.EOFException(), null))
        assertEquals("unknown", entries.single().provider)
        assertEquals("EOFException", entries.single().error)
    }

    private fun input(bytes: ByteArray) = FakeExtractorInput.Builder().setData(bytes).build()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    companion object {
        @JvmStatic @BeforeClass fun parserChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true) }
        @JvmStatic @AfterClass fun restoreParserChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null) }
    }
}
