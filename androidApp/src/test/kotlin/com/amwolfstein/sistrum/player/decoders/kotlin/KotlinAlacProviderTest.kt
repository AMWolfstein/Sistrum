@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.DataReader
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.extractor.*
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.test.utils.FakeExtractorInput
import com.amwolfstein.sistrum.codecs.codec.alac.AlacException
import com.amwolfstein.sistrum.codecs.codec.alac.Config
import com.amwolfstein.sistrum.player.decoders.*
import com.amwolfstein.sistrum.player.media3.ProviderExtractorsFactory
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class KotlinAlacProviderTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "docs/waxflow/alac-packets-manifest.tsv").isFile }
    private val oracleDir = System.getenv("SISTRUM_ORACLE_DIR") ?: "${System.getProperty("user.home")}/.cache/sistrum-waxflow-oracle"
    private val packetsDir = System.getProperty("waxflow.alacPackets") ?: System.getenv("WAXFLOW_ALAC_PACKETS") ?: "$oracleDir/alac-packets"
    private val corpus = System.getProperty("waxflow.corpus") ?: System.getenv("WAXFLOW_CORPUS") ?: "$oracleDir/corpus"
    private val rows: List<Map<String, String>> by lazy {
        val data = File(root, "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv").readLines().filter { !it.startsWith('#') && it.isNotEmpty() }
        val columns = data.first().split('\t')
        data.drop(1).map { columns.zip(it.split('\t')).toMap() }.filter {
            it.getValue("status") == "ok" && (it.getValue("file").startsWith("waxflow-alac-tests/") || it.getValue("file") == "waxflow-testdata/chapters.m4b")
        }.also { assertEquals(8, it.size) }
    }
    private data class Packet(val timeUs: Long, val bytes: ByteArray)
    private data class Dump(val config: Config, val cookie: ByteArray, val packets: List<Packet>)
    private fun dump(row: Map<String, String>): Dump {
        val name = row.getValue("file") + ".packets"
        val bytes = File(packetsDir, name).readBytes()
        val manifest = File(root, "docs/waxflow/alac-packets-manifest.tsv").readLines().drop(1).associate { val c = it.split('\t'); c.first() to c.last() }
        assertEquals(manifest.getValue(name), hash(bytes))
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sourceHash = ByteArray(32); data.get(sourceHash)
        assertEquals(row.getValue("file_sha256"), sourceHash.hex())
        val cookie = ByteArray(data.int); data.get(cookie)
        val config = Config(ByteBuffer.wrap(cookie))
        val total = data.long
        var end = 0L
        val packets = mutableListOf<Packet>()
        while (data.hasRemaining()) {
            val sample = data.long; val duration = data.long
            end = maxOf(end, sample + duration)
            val packet = ByteArray(data.int); data.get(packet)
            packets += Packet(sample * 1_000_000 / config.sampleRate, packet)
        }
        assertEquals(row.getValue("frames").toLong(), if (total >= 0) total else end)
        return Dump(config, cookie, packets)
    }
    private fun format(dump: Dump): Format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_ALAC)
        .setSampleRate(dump.config.sampleRate).setChannelCount(dump.config.channels)
        .setInitializationData(listOf(dump.cookie)).build()

    @Test fun platformFirstRegistryAndRendererCapabilityAreConsistent() {
        for (available in listOf(false, true)) {
            val probe = CodecProbe { mime -> assertEquals(MimeTypes.AUDIO_ALAC, mime); available }
            val provider = KotlinAlacProvider(probe)
            for (container in listOf("m4a", "mp4")) {
                assertEquals(!available, provider.isAvailable(FormatKey(container, "alac")))
                assertEquals(if (available) "platform" else "kotlin-alac", defaultDecoderRegistry(CodecProbe { available }).resolve(FormatKey(container, "alac"))?.id)
            }
            assertFalse(provider.isAvailable(FormatKey("m4a", "aac")))
            assertFalse(provider.isAvailable(FormatKey("wav", "alac")))
            assertTrue(provider.extractors().isEmpty())
            val renderer = KotlinAlacAudioRenderer(probe, sink())
            assertEquals("kotlin-alac", renderer.name)
            val format = format(dump(rows.first()))
            assertEquals(if (available) C.FORMAT_UNSUPPORTED_TYPE else C.FORMAT_HANDLED, RendererCapabilities.getFormatSupport(renderer.supportsFormat(format)))
            assertEquals(C.FORMAT_UNSUPPORTED_TYPE, RendererCapabilities.getFormatSupport(renderer.supportsFormat(format.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AAC).build())))
        }
    }

    @Test fun allPinnedPacketsMatchOracleWithNativeDepthTimestampsEosAndReusedStorage() {
        for (row in rows) {
            val dump = dump(row)
            val decoder = KotlinAlacDecoder(dump.config)
            try {
                assertEquals(row.getValue("rate").toInt(), decoder.outputFormat.sampleRate)
                assertEquals(row.getValue("channels").toInt(), decoder.outputFormat.channelCount)
                assertEquals(when (row.getValue("bits")) { "16" -> C.ENCODING_PCM_16BIT; "24" -> C.ENCODING_PCM_24BIT; else -> C.ENCODING_PCM_32BIT }, decoder.outputFormat.pcmEncoding)
                val pcm = ByteArrayOutputStream()
                val storage = java.util.IdentityHashMap<SimpleDecoderOutputBuffer, ByteBuffer>()
                repeat(3) { pass ->
                    decoder.flush()
                    for (packet in dump.packets) {
                        queue(decoder, packet)
                        val out = output(decoder)
                        try {
                            assertEquals(packet.timeUs, out.timeUs)
                            val bytes = checkNotNull(out.data)
                            storage[out]?.let { assertSame(it, bytes) }; storage[out] = bytes
                            val copy = ByteArray(bytes.remaining()); bytes.get(copy)
                            if (pass == 0) pcm.write(copy)
                        } finally { out.release() }
                    }
                }
                assertTrue(storage.size <= 4)
                assertEquals(row.getValue("frames").toLong() * dump.config.channels * ((dump.config.bitDepth + 7) / 8), pcm.size().toLong())
                assertEquals(row.getValue("pcm_sha256"), hash(pcm.toByteArray()))
                val input = await { decoder.dequeueInputBuffer() }
                input.addFlag(C.BUFFER_FLAG_END_OF_STREAM); decoder.queueInputBuffer(input)
                val eos = output(decoder); assertTrue(eos.isEndOfStream); eos.release()
                assertNull(decoder.dequeueOutputBuffer())
            } finally { decoder.release() }
        }
    }

    @Test fun forwardAndBackwardFlushReplaysExactPacketPcmAndDropsQueuedOldOutput() {
        for (row in rows) {
            val dump = dump(row); val decoder = KotlinAlacDecoder(dump.config)
            try {
                val baseline = dump.packets.map { packet -> queue(decoder, packet); val out = output(decoder); try { ByteArray(out.data!!.remaining()).also { out.data!!.get(it) } } finally { out.release() } }
                for (index in listOf(dump.packets.lastIndex, 0, dump.packets.size / 2, 0)) {
                    queue(decoder, dump.packets.last())
                    decoder.flush()
                    queue(decoder, dump.packets[index])
                    val out = output(decoder)
                    try {
                        assertEquals(dump.packets[index].timeUs, out.timeUs)
                        val bytes = ByteArray(out.data!!.remaining()); out.data!!.get(bytes)
                        assertArrayEquals(baseline[index], bytes)
                    } finally { out.release() }
                }
            } finally { decoder.release() }
        }
    }

    @Test fun malformedPacketsKeepTypedCauseAndEncryptedOrInvalidFormatsAreRejected() {
        val dump = dump(rows.first()); val decoder = KotlinAlacDecoder(dump.config)
        try {
            queue(decoder, Packet(0, byteArrayOf(0)))
            val failure = try { output(decoder); throw AssertionError("corrupt packet accepted") } catch (e: DecoderException) { e }
            assertTrue(failure.cause is AlacException)
            assertEquals(AlacException.Reason.MALFORMED, (failure.cause as AlacException).reason)
        } finally { decoder.release() }
        val renderer = KotlinAlacAudioRenderer(CodecProbe { false }, sink())
        val format = format(dump)
        assertEquals(C.FORMAT_UNSUPPORTED_DRM, RendererCapabilities.getFormatSupport(renderer.supportsFormat(format.buildUpon().setCryptoType(C.CRYPTO_TYPE_FRAMEWORK).build())))
        for (invalid in listOf(
            format.buildUpon().setInitializationData(emptyList()).build(),
            format.buildUpon().setChannelCount(8).build(),
        )) {
            assertEquals(C.FORMAT_HANDLED, RendererCapabilities.getFormatSupport(renderer.supportsFormat(invalid)))
            val failure = try { renderer.createDecoder(invalid, null).release(); throw AssertionError("invalid cookie accepted") }
                catch (e: DecoderException) { e }
            assertTrue(failure.cause is IllegalArgumentException)
            assertTrue(failure.message!!.contains(failure.cause!!.message!!))
        }
        for (channels in listOf(4, 6, 8)) {
            val cookie = dump.cookie.copyOf(); cookie[9] = channels.toByte()
            val invalid = format.buildUpon().setChannelCount(channels).setInitializationData(listOf(cookie)).build()
            assertEquals(C.FORMAT_HANDLED, RendererCapabilities.getFormatSupport(renderer.supportsFormat(invalid)))
            val failure = try { renderer.createDecoder(invalid, null).release(); throw AssertionError("unsupported channels accepted") }
                catch (e: DecoderException) { e }
            assertEquals(AlacException.Reason.UNSUPPORTED, (failure.cause as AlacException).reason)
            assertEquals("alac: channel count $channels: only mono and stereo are supported", failure.cause!!.message)
            assertTrue(failure.message!!.contains(failure.cause!!.message!!))
        }
    }

    @Test fun media3Mp4DemuxFeedsTheSameCookiePacketsAndOraclePcmWithCorrectAttribution() {
        for (row in rows) for (available in listOf(false, true)) {
            val dump = dump(row)
            val data = File(corpus, row.getValue("file")).readBytes()
            assertEquals(row.getValue("file_sha256"), hash(data))
            val recorder = ProviderRecorder()
            // DefaultExtractorsFactory uses Android Uri.EMPTY, which is null on the plain host.
            // Keep real MP4 sniffing/demuxing through the existing injectable factory seam.
            val factory = ProviderExtractorsFactory(
                defaultDecoderProviders(CodecProbe { available }),
                base = ExtractorsFactory { arrayOf(
                    Mp4Extractor(DefaultSubtitleParserFactory()),
                    FragmentedMp4Extractor(DefaultSubtitleParserFactory()),
                ) },
                recorder = recorder,
            )
            val extractors = factory.createExtractors()
            val selected = extractors.first { it.sniff(FakeExtractorInput.Builder().setData(data).build()) }
            // The three imported golden files contain moof fragments without sidx/mfra.
            // R2 leaves container selection and seeking to Media3, including its unseekable
            // map for those unindexed fragments; ordinary MP4 must remain seekable.
            val fragmented = row.getValue("file").startsWith("waxflow-alac-tests/container/mp4/testdata/golden/")
            assertEquals(if (fragmented) FragmentedMp4Extractor::class.java else Mp4Extractor::class.java,
                selected.underlyingImplementation.javaClass)
            if (fragmented) {
                // FragmentedMp4Extractor builds its trex defaults with android.util.Pair, which the
                // plain host stub returns as null. Their packets and PCM are still checked against the
                // oracle by the packet-dump tests above; Media3 demux of these files runs on .qa (T012).
                extractors.forEach { it.release() }
                continue
            }
            val uri = "file:///" + row.getValue("file")
            recorder.record(uri, "platform")
            val extractor = factory.withAlacAttribution(selected, uri)
            val output = Mp4Output()
            val input = FakeExtractorInput.Builder().setData(data).build()
            extractor.init(output)
            try {
                val holder = PositionHolder()
                var steps = 0
                while (true) {
                    assertTrue(++steps < 10000)
                    when (extractor.read(input, holder)) {
                        Extractor.RESULT_END_OF_INPUT -> break
                        Extractor.RESULT_SEEK -> input.setPosition(holder.position.toInt())
                    }
                }
                assertEquals(if (available) "platform" else "kotlin-alac", recorder.providerFor(uri))
                assertEquals(MimeTypes.AUDIO_ALAC, output.format!!.sampleMimeType)
                assertArrayEquals(dump.cookie, output.format!!.initializationData.single())
                assertEquals(dump.packets.size, output.packets.size)
                val renderer = KotlinAlacAudioRenderer(CodecProbe { available }, sink())
                assertEquals(if (available) C.FORMAT_UNSUPPORTED_TYPE else C.FORMAT_HANDLED,
                    RendererCapabilities.getFormatSupport(renderer.supportsFormat(output.format!!)))
                val decoder = KotlinAlacDecoder(dump.config)
                try {
                    val pcm = ByteArrayOutputStream()
                    output.packets.forEachIndexed { index, packet ->
                        assertEquals(dump.packets[index].timeUs, packet.timeUs)
                        assertArrayEquals(dump.packets[index].bytes, packet.bytes)
                        queue(decoder, packet)
                        val out = output(decoder)
                        try { val bytes = ByteArray(out.data!!.remaining()); out.data!!.get(bytes); pcm.write(bytes) }
                        finally { out.release() }
                    }
                    assertEquals(row.getValue("pcm_sha256"), hash(pcm.toByteArray()))
                } finally { decoder.release() }
                assertEquals(!fragmented, output.map!!.isSeekable)
            } finally { extractors.forEach { it.release() } }
        }
    }

    private class Mp4Output : ExtractorOutput, TrackOutput {
        var format: Format? = null
        var map: SeekMap? = null
        val packets = mutableListOf<Packet>()
        private val pending = ByteArrayOutputStream()
        override fun track(id: Int, type: Int): TrackOutput = if (type == C.TRACK_TYPE_AUDIO) this else DiscardingTrackOutput()
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) { map = seekMap }
        override fun format(format: Format) { this.format = format }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val bytes = ByteArray(length)
            val count = input.read(bytes, 0, length)
            if (count > 0) pending.write(bytes, 0, count)
            return count
        }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            pending.write(data.data, data.position, length); data.skipBytes(length)
        }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            assertEquals(0, offset); assertEquals(size, pending.size())
            packets += Packet(timeUs, pending.toByteArray()); pending.reset()
        }
    }

    private fun queue(decoder: KotlinAlacDecoder, packet: Packet) {
        val input = await { decoder.dequeueInputBuffer() }
        input.ensureSpaceForWrite(packet.bytes.size); input.data!!.put(packet.bytes); input.timeUs = packet.timeUs; input.flip()
        decoder.queueInputBuffer(input)
    }
    private fun output(decoder: KotlinAlacDecoder) = await { decoder.dequeueOutputBuffer() }
    private fun <T : Any> await(action: () -> T?): T {
        val end = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < end) { action()?.let { return it }; Thread.yield() }
        throw AssertionError("ALAC decoder did not return a buffer")
    }
    private fun sink(): AudioSink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, _ ->
        when (method.name) { "supportsFormat" -> true; "getFormatSupport" -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY; else -> throw AssertionError(method.name) }
    } as AudioSink
    companion object {
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        @JvmStatic @BeforeClass fun parserChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true) }
        @JvmStatic @AfterClass fun restoreChecks() { ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null) }
    }
}
