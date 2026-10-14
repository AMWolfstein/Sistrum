@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.analysis.DecodeOutcome
import com.amwolfstein.sistrum.analysis.OfflineDecoder
import com.amwolfstein.sistrum.analysis.PcmConsumer
import com.amwolfstein.sistrum.codecs.container.dsd.Dsd
import com.amwolfstein.sistrum.codecs.codec.alac.Config
import com.amwolfstein.sistrum.player.decoders.kotlin.KotlinAlacDecoder
import com.amwolfstein.sistrum.player.decoders.CodecProbe
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.amwolfstein.sistrum.player.decoders.defaultDecoderProviders
import com.amwolfstein.sistrum.player.engine.EngineEvent
import com.amwolfstein.sistrum.player.engine.ItemGain
import com.amwolfstein.sistrum.player.media3.Media3Engine
import com.amwolfstein.sistrum.player.media3.Media3PlayerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** T012. Corpus setup: scripts/test-corpus/README.md. Missing fixtures FAIL, never skip.
 * Device execution belongs to T013, exclusively against the .qa target.
 */
@RunWith(AndroidJUnit4::class)
class KotlinFormatsPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    // Advertise fallback providers; the raw fragmented-ALAC check below forces Kotlin decoding
    // independently of any platform ALAC codec on the measurement phone.
    private val providers = defaultDecoderProviders(CodecProbe { false })
    private val fixtures by lazy {
        instrumentation.context.assets.open("kotlin-formats.tsv").bufferedReader().use { reader ->
            val lines = reader.readLines()
            val header = lines.first().split('\t')
            lines.drop(1).map { Fixture(header.zip(it.split('\t')).toMap()) }
        }
    }

    @Before fun qaOnlyAndCompleteCorpus() {
        check(context.packageName == "com.amwolfstein.sistrum.dev.qa") { "T012 requires the .qa target" }
        assertEquals("fixture inventory", 21, fixtures.size)
        fixtures.forEach { fixture ->
            assertTrue("Missing ${fixture.file}; stage and push the T012 corpus", fixture.file.isFile)
            assertEquals(fixture.id, fixture.value("file_sha256"), hash(fixture.file.readBytes()))
        }
    }

    @Test(timeout = 600_000) fun everyCoveredFormatPlaysOnMedia3() {
        for (fixture in fixtures) withRig { rig ->
            rig.prepare(fixture)
            rig.engine.play()
            rig.await { rig.events.any { it is EngineEvent.Ended } }
            assertEquals(fixture.id, 1, rig.events.count { it is EngineEvent.OutputStarted })
            assertTrue("${fixture.id}: no PCM rendered", rig.taps.sumOf { it.frames() } > 0L)
            assertEquals(fixture.id, fixture.value("provider"), rig.factory.providerRecorder.providerFor(fixture.uri))
        }
    }

    @Test(timeout = 600_000) fun offlineDeviceDecodeMatchesPinnedOracleHashes() {
        for (fixture in fixtures.filter { it.value("capture_sha256") != "production-D18" }) {
            decode(fixture) // Exact frame/rate/channel/provider and oracle hash assertions live here.
        }
    }

    @Test(timeout = 120_000) fun threeFragmentedAlacGoldensDemuxAndDecodeThroughMedia3() {
        val fragmented = fixtures.filter { it.id.startsWith("alac-frag") }
        assertEquals(3, fragmented.size)
        fragmented.forEach {
            fragmentedAlacRawPcm(it) // Full source-depth hash, including all 32 bits.
            decode(it) // The real offline player/renderer/capture integration as well.
        }
    }

    @Test(timeout = 120_000) fun dsdDeviceIntegrationMatchesApprovedProductionConverter() {
        // D-18: the retained Flick oracle hashes describe a DIFFERENT filter. Do not call
        // this oracle parity. Test the actual device demux/renderer/capture against the
        // approved production codec; its independent analytic tests remain host-side.
        for (fixture in fixtures.filter { it.value("capture_sha256") == "production-D18" }) {
            val codec = Dsd.open(ByteBuffer.wrap(fixture.file.readBytes()))
            val expected = ByteArrayOutputStream()
            while (true) {
                val block = codec.decodeBlock() ?: break
                expected.write(floatBytes(block.samples, block.frames * block.channels))
            }
            val actual = decode(fixture, expectedHash = hash(expected.toByteArray()))
            assertArrayEquals(fixture.id, expected.toByteArray(), floatBytes(actual, actual.size))
        }
    }

    @Test(timeout = 600_000) fun seekToDecodedOutputCompletesWithin300MsPerFormat() {
        // The three unindexed fragmented goldens have no seek map (D-20); ordinary ALAC
        // supplies the format's seek check. Every other fixture is exercised, including DSD.
        for (fixture in fixtures.filter { it.value("seek") == "yes" }) withRig { rig ->
            rig.prepare(fixture)
            val targetMs = fixture.value("frames").toLong() * 500 / fixture.value("rate").toInt()
            val advancingAt = AtomicLong(0)
            val soughtTo = AtomicLong(-1)
            rig.factory.call {
                rig.engine.playersForTest().single().addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) soughtTo.set(newPosition.positionMs)
                    }
                })
                rig.engine.playersForTest().single().addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                        advancingAt.compareAndSet(0, SystemClock.elapsedRealtime())
                    }
                })
            }
            val started = SystemClock.elapsedRealtime()
            rig.engine.seekTo(targetMs)
            rig.engine.play()
            rig.await(2_000) { advancingAt.get() != 0L }
            assertEquals("${fixture.id}: seek target", targetMs, soughtTo.get())
            val elapsed = advancingAt.get() - started
            assertTrue("${fixture.id}: seek to audible output took ${elapsed}ms", elapsed in 0..300)
            assertTrue("${fixture.id}: seek produced no PCM", rig.taps.sumOf { it.frames() } > 0)
            android.util.Log.i("KotlinFormatsPlaybackTest", "${fixture.id} seekMs=$elapsed targetMs=$targetMs")
        }
    }

    @Test(timeout = 300_000) fun wavpackApeAlacAndMusepackJoinWithinTwoFrames() {
        val gapless = fixtures.filter { it.value("gapless") == "yes" }
        assertEquals(setOf("wavpack", "ape", "alac", "musepack-sv7"), gapless.map { it.id }.toSet())
        for (fixture in gapless) {
            val reference = decode(fixture)
            val channels = fixture.value("channels").toInt()
            val frames = reference.size / channels
            withRig { rig ->
                rig.prepare(fixture)
                runBlocking { rig.engine.preloadNext(item(fixture, "incoming"), ItemGain.Unity) }
                rig.engine.play()
                rig.await(90_000) { rig.events.any { it is EngineEvent.Ended } }
                assertEquals(fixture.id, 1, rig.events.count { it is EngineEvent.GaplessAdvanced })
                val captured = rig.taps.single().samples()
                val actualFrames = captured.size / channels
                assertTrue("${fixture.id}: frame count $actualFrames vs ${2 * frames}", abs(actualFrames - 2 * frames) <= 2)
                // Match the outgoing PCM and the ENTIRE incoming PCM, allowing at most two
                // inserted/dropped frames at the join. Silence elsewhere in music is irrelevant.
                assertTrue("${fixture.id}: outgoing PCM changed before join",
                    samplesMatch(reference, captured, 0, frames - 2, channels))
                val aligned = (-2..2).any { offset ->
                    val start = frames + offset
                    val count = minOf(frames, actualFrames - start)
                    count >= frames - 2 && samplesMatch(reference, captured, start, count, channels)
                }
                assertTrue("${fixture.id}: incoming PCM not aligned within two frames of join", aligned)
                assertEquals("incoming identity", "incoming", rig.events.filterIsInstance<EngineEvent.GaplessAdvanced>().single().incoming.trackId)
            }
        }
    }

    @Test(timeout = 120_000) fun twoKotlinDecodedTracksCrossfadeWithoutUnderrun() = withRig { rig ->
        val outgoing = fixtures.single { it.id == "wavpack" }
        val incoming = fixtures.single { it.id == "ape" }
        rig.engine.setCrossfade(1_000L, true)
        rig.prepare(outgoing, startPositionMs = 24_000L)
        runBlocking { rig.engine.preloadNext(item(incoming, "fade-in"), ItemGain.Unity) }
        val underruns = AtomicInteger()
        val monitor = object : AnalyticsListener {
            override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                underruns.incrementAndGet()
            }
        }
        val outgoingPlayer = rig.factory.call { rig.engine.playersForTest().single().also { it.addAnalyticsListener(monitor) } }
        rig.engine.play()
        // Crossfade preloading is scheduled while playback advances, not inside preloadNext.
        rig.await { rig.factory.livePlayers == 2 }
        rig.factory.call {
            rig.engine.playersForTest().single { it !== outgoingPlayer }.addAnalyticsListener(monitor)
        }
        rig.await { rig.events.any { it is EngineEvent.TransitionStarted } }
        assertEquals("crossfade creates two independent PCM streams", 2, rig.taps.size)
        rig.await {
            rig.engine.isCrossfading() && rig.factory.call { rig.engine.playersForTest().size == 2 && rig.engine.playersForTest().all { it.isPlaying } }
        }
        rig.await { rig.events.any { it is EngineEvent.Ended } }
        assertEquals(1, rig.events.count { it is EngineEvent.TransitionStarted })
        val transition = rig.events.filterIsInstance<EngineEvent.TransitionStarted>().single()
        assertEquals("fade-in", transition.incoming.trackId)
        assertEquals(1_000L, transition.fadeMs)
        assertEquals(0, rig.events.count { it is EngineEvent.GaplessAdvanced })
        assertTrue("both streams rendered non-silent PCM", rig.taps.all { tap -> tap.samples().any { abs(it) > 1e-5f } })
        assertEquals("AudioTrack underruns during the two-decoder run", 0, underruns.get())
        for (fixture in listOf(outgoing, incoming)) {
            assertEquals(fixture.value("provider"), rig.factory.providerRecorder.providerFor(fixture.uri))
        }
    }

    private fun fragmentedAlacRawPcm(fixture: Fixture) {
        val source = FileDataSource()
        val extractor = FragmentedMp4Extractor(DefaultSubtitleParserFactory())
        var decoder: KotlinAlacDecoder? = null
        val digest = MessageDigest.getInstance("SHA-256")
        var frames = 0L
        var packets = 0
        val pending = ByteArrayOutputStream()
        val track = object : TrackOutput {
            override fun format(format: Format) {
                assertNull("single ALAC configuration", decoder)
                assertEquals("audio/alac", format.sampleMimeType)
                val config = Config(ByteBuffer.wrap(format.initializationData.single()))
                assertEquals(fixture.value("bits").toInt(), config.bitDepth)
                decoder = KotlinAlacDecoder(config)
            }
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
                assertNull("unencrypted ALAC", cryptoData)
                assertEquals(0, offset)
                assertEquals(size, pending.size())
                val codec = checkNotNull(decoder)
                val input = awaitBuffer { codec.dequeueInputBuffer() }
                input.ensureSpaceForWrite(size); input.data!!.put(pending.toByteArray())
                input.timeUs = timeUs; input.flip(); codec.queueInputBuffer(input)
                pending.reset()
                val output = awaitBuffer { codec.dequeueOutputBuffer() }
                try {
                    assertEquals(timeUs, output.timeUs)
                    val bytes = ByteArray(output.data!!.remaining()); output.data!!.get(bytes)
                    digest.update(bytes)
                    frames += bytes.size / (fixture.value("channels").toInt() * (fixture.value("bits").toInt() / 8))
                    packets++
                } finally { output.release() }
            }
        }
        try {
            val length = source.open(DataSpec(fixture.file.let { Uri.fromFile(it) }))
            val input = DefaultExtractorInput(source, 0L, length)
            assertTrue("Media3 fragmented MP4 sniff", extractor.sniff(input))
            input.resetPeekPosition()
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = if (type == C.TRACK_TYPE_AUDIO) track else DiscardingTrackOutput()
                override fun endTracks() = Unit
                override fun seekMap(seekMap: SeekMap) { assertFalse("unindexed fragments (D-20)", seekMap.isSeekable) }
            })
            val position = PositionHolder()
            while (true) {
                val result = extractor.read(input, position)
                assertNotEquals("fragmented stream must not require an external seek", Extractor.RESULT_SEEK, result)
                if (result == Extractor.RESULT_END_OF_INPUT) break
            }
            assertTrue("ALAC packets demuxed", packets > 0)
            assertEquals(0, pending.size())
            assertEquals(fixture.value("frames").toLong(), frames)
            assertEquals("${fixture.id}: full-depth oracle PCM", fixture.value("pcm_sha256"), digest.digest().joinToString("") { "%02x".format(it) })
        } finally { decoder?.release(); extractor.release(); source.close() }
    }

    private fun <T : Any> awaitBuffer(read: () -> T?): T {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        do { read()?.let { return it }; SystemClock.sleep(1) } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Kotlin ALAC decoder buffer timeout")
    }

    private fun decode(fixture: Fixture, expectedHash: String = fixture.value("capture_sha256")): FloatArray {
        val pcm = ByteArrayOutputStream()
        val outcome = OfflineDecoder(context, providers).decode(fixture.file.absolutePath,
            FormatKey(fixture.value("format"), fixture.value("codec")), PcmConsumer { samples, frames, rate, channels ->
                assertEquals(fixture.id, fixture.value("rate").toInt(), rate)
                assertEquals(fixture.id, fixture.value("channels").toInt(), channels)
                pcm.write(floatBytes(samples, frames * channels))
            })
        assertEquals(fixture.id, DecodeOutcome.Completed(fixture.value("rate").toInt(),
            fixture.value("channels").toInt(), fixture.value("frames").toLong(), fixture.value("provider")), outcome)
        val bytes = pcm.toByteArray()
        assertEquals("${fixture.id}: oracle float capture", expectedHash, hash(bytes))
        return FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
    }

    private inner class Rig : AutoCloseable {
        val taps = CopyOnWriteArrayList<PcmTap>()
        val factory = Media3PlayerFactory(context, providers = providers,
            audioProcessors = { PcmTap().also { taps += it }.let { arrayOf<AudioProcessor>(TeeAudioProcessor(it)) } })
        val engine = Media3Engine(factory)
        val events = mutableListOf<EngineEvent>()
        init { engine.setFocusGain(0f) }
        fun prepare(fixture: Fixture, startPositionMs: Long = 0L) {
            runBlocking { engine.prepare(item(fixture), ItemGain.Unity, startPositionMs, startPaused = true) }
        }
        fun await(timeoutMs: Long = 45_000, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            do {
                events += engine.pollEvents()
                assertTrue("Media3 errors: $events", events.none { it is EngineEvent.Error })
                if (predicate()) return
                SystemClock.sleep(2)
            } while (SystemClock.elapsedRealtime() < deadline)
            throw AssertionError("Media3 condition timed out: $events")
        }
        override fun close() { engine.close(); assertEquals("live players leaked", 0, factory.livePlayers) }
    }

    private fun withRig(body: (Rig) -> Unit) { val rig = Rig(); try { body(rig) } finally { rig.close() } }
    private fun item(fixture: Fixture, id: String = fixture.id) = PlaybackItem(id, id, "T012", fixture.file.absolutePath)

    private class Fixture(private val fields: Map<String, String>) {
        val id get() = value("id")
        fun value(key: String) = fields.getValue(key)
        val file: File get() {
            val extension = File(value("source")).extension.let { if (it == "m4b") "m4a" else it }
            return File("/sdcard/Music/SistrumTestCorpus", "t012-$id.$extension")
        }
        val uri get() = Uri.fromFile(file).toString()
    }

    private class PcmTap : TeeAudioProcessor.AudioBufferSink {
        private val pcm = ByteArrayOutputStream()
        private var channels = 0
        @Synchronized override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            assertEquals("float playback sink", C.ENCODING_PCM_FLOAT, encoding)
            if (channels != 0) assertEquals("stable channels", channels, channelCount)
            channels = channelCount
        }
        @Synchronized override fun handleBuffer(buffer: ByteBuffer) {
            val bytes = ByteArray(buffer.remaining()); buffer.duplicate().get(bytes); pcm.write(bytes)
        }
        @Synchronized fun frames(): Long = if (channels == 0) 0 else pcm.size().toLong() / (channels * 4)
        @Synchronized fun samples(): FloatArray {
            val bytes = pcm.toByteArray()
            return FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(it) }
        }
    }

    private fun samplesMatch(expected: FloatArray, actual: FloatArray, start: Int, count: Int, channels: Int): Boolean =
        (0 until count * channels).all { abs(expected[it] - actual[start * channels + it]) <= 1e-6f }

    private fun floatBytes(samples: FloatArray, count: Int): ByteArray =
        ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN).also { it.asFloatBuffer().put(samples, 0, count) }.array()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
