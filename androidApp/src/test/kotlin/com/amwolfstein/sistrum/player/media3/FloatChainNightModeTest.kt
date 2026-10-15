package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import com.amwolfstein.sistrum.dsp.gain.Compressor
import com.amwolfstein.sistrum.dsp.gain.Preset
import com.amwolfstein.sistrum.player.dsp.EqualizerProcessor
import com.amwolfstein.sistrum.player.dsp.GainProcessor
import com.amwolfstein.sistrum.player.dsp.PcmToFloatProcessor
import com.amwolfstein.sistrum.player.dsp.PreampProcessor
import com.amwolfstein.sistrum.player.dsp.StereoWidthProcessor
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

/**
 * T014 / sink-chain.md / SC-008. Tests only; T015 must pass these unchanged.
 * The fourth sink constructor argument is a () -> Boolean reading the one session setting (D-34).
 * Reflection keeps this compiling before that argument exists; absence is an assertion failure.
 *
 * The legacy oracle is the explicit 001 conversion + DSP pipeline, without this sink wrapper.
 * Voice samples come from the imported :dsp kernel, independently of its Media3 adapter.
 * The fake session limiter is only a boundary model: Android mixer/effect PCM is a T020/T021 device check.
 */
@OptIn(UnstableApi::class)
class FloatChainNightModeTest {
    @Test
    fun defaultOffIsBitIdenticalTo001() {
        checkOffIdentity(explicitSetting = false)
    }

    @Test
    fun explicitOffIsBitIdenticalTo001() {
        checkOffIdentity(explicitSetting = true)
    }

    @Test
    fun voiceReducesLoudnessRangeByAtLeastThirtyPercent() {
        for ((rate, channels) in listOf(48_000 to 1, 48_000 to 2, 96_000 to 3)) {
            // Three 3 s plateaus, quiet/loud/quiet. Measure the final second of each;
            // the 250 ms release has settled, and a fixed gain cannot reduce this dB range.
            val input = FloatArray(rate * 9 * channels) { index ->
                val amplitude = if (index / channels / (rate * 3) == 1) 0.8f else 0.06f
                if (index % channels == 1) -amplitude else amplitude
            }
            val off = render(input, rate, channels, false)
            val on = render(input, rate, channels, true)
            val offRange = plateauRange(off, rate, channels)
            val onRange = plateauRange(on, rate, channels)
            assertEquals("fixture range in dB", 20 * log10(0.8 / 0.06), offRange, 1e-5)
            assertTrue("$rate Hz/$channels ch: OFF=$offRange dB, ON=$onRange dB",
                onRange <= offRange * 0.70)
        }
    }

    @Test
    fun voiceFollowsStreamGainAndPrecedesTheSessionLimiter() {
        val rate = 48_000
        val channels = 2
        val input = FloatArray(rate * 2 * channels) { if (it % 2 == 0) 4f else -0.4f }
        val normalized = legacy(input, rate, channels, chain(gainDb = -6f))
        val expected = voice(normalized, rate, channels)
        val delegate = CaptureSink(limit = true)
        val sink = nightSink(delegate, chain(gainDb = -6f)) { true }
        configure(sink, rate, channels)
        feed(sink, input, channels, 257)
        sink.playToEndOfStream()

        val beforeLimiter = delegate.samples()
        assertEquals(input.size, beforeLimiter.size)
        // Ignore only the specified 50 ms bypass-to-compressed blend, not compressor attack.
        for (i in rate / 20 * channels until input.size) {
            assertEquals("voice preset after normalization, sample $i", expected[i], beforeLimiter[i], 2e-6f)
        }
        assertEquals("001 session threshold remains -1 dBFS", -1f,
            LimiterConfigs.active(40f).limiter.thresholdDb, 0f)
        assertTrue("overload fixture must exercise the downstream limiter", delegate.limitedSamples > 0)
        val output = delegate.limited.toFloatArray()
        assertEquals(input.size, output.size)
        assertTrue("all output samples finite and within -1 dBFS", output.all {
            it.isFinite() && abs(it) <= CEILING
        })
    }

    @Test
    fun onAndOffCrossfadeForFiftyMillisecondsAcrossBufferBoundaries() {
        for ((rate, channels, chunk) in listOf(Triple(44_100, 1, 17), Triple(48_000, 2, 257), Triple(96_000, 3, 1024))) {
            val setting = MutableStateFlow(false)
            val delegate = CaptureSink()
            val sink = nightSink(delegate, chain()) { setting.value }
            configure(sink, rate, channels)
            feed(sink, FloatArray(rate / 5 * channels) { DRY }, channels, chunk)
            for (enabled in listOf(true, false)) {
                val start = delegate.received.size() / 4
                setting.value = enabled
                val frames = rate / 10
                // An empty buffer must not advance the ramp or produce a sample.
                feed(sink, floatArrayOf(), channels, chunk)
                feed(sink, FloatArray(frames * channels) { DRY }, channels, chunk)
                val output = delegate.samples().copyOfRange(start, start + frames * channels)
                assertRamp(output, rate, channels, enabled)
            }
        }
    }

    @Test
    fun bothPlayersAndAReplacementReadOneSettingWithoutReconfigure() {
        val rate = 48_000
        val setting = MutableStateFlow(false)
        val a = CaptureSink()
        val b = CaptureSink()
        val sinkA = nightSink(a, chain()) { setting.value }
        val sinkB = nightSink(b, chain()) { setting.value }
        configure(sinkA, rate, 2)
        configure(sinkB, rate, 2)
        for (enabled in listOf(false, true, false)) {
            setting.value = enabled
            feed(sinkA, FloatArray(rate / 5 * 2) { DRY }, 2, 257)
            feed(sinkB, FloatArray(rate / 5 * 2) { DRY / 2 }, 2, 1024)
            val factor = if (enabled) MAKEUP else 1f
            assertEquals("A follows shared setting=$enabled", DRY * factor, a.samples().last(), 1e-7f)
            assertEquals("B follows shared setting=$enabled", DRY / 2 * factor, b.samples().last(), 1e-7f)
        }
        setting.value = true
        val replacement = CaptureSink()
        val sinkC = nightSink(replacement, chain()) { setting.value }
        configure(sinkC, rate, 2)
        feed(sinkC, FloatArray(rate / 5 * 2) { DRY }, 2, 17)
        assertEquals("new player inherits ON", DRY * MAKEUP, replacement.samples().last(), 1e-7f)
        setting.value = false
        feed(sinkC, FloatArray(rate / 5 * 2) { DRY }, 2, 17)
        assertEquals("replacement observes OFF", DRY, replacement.samples().last(), 0f)
    }

    @Test
    fun discontinuityMetricRejectsAnInstantSwitchAndWrongDurations() {
        // Negative controls use the contract's two endpoints, not the implementation under test.
        for (durationMs in listOf(0, 25, 100)) {
            val rate = 48_000
            val rampFrames = rate * durationMs / 1000
            val synthetic = FloatArray(rate / 10) { frame ->
                val mix = if (rampFrames == 0) 1f else (frame.toFloat() / rampFrames).coerceAtMost(1f)
                DRY + (DRY * MAKEUP - DRY) * mix
            }
            val failure = try {
                assertRamp(synthetic, rate, 1, true)
                null
            } catch (error: AssertionError) {
                error
            }
            assertNotNull("metric must reject a $durationMs ms transition", failure)
        }
        val correct = FloatArray(4800) { frame ->
            DRY + (DRY * MAKEUP - DRY) * (frame / 2400f).coerceAtMost(1f)
        }
        assertRamp(correct, 48_000, 1, true)
    }

    @Test
    fun rangeMetricAcceptsVoiceOracleAndRejectsConstantGain() {
        val rate = 48_000
        val input = FloatArray(rate * 9) { if (it / (rate * 3) == 1) 0.8f else 0.06f }
        val dryRange = plateauRange(input, rate, 1)
        val voiceRange = plateauRange(voice(input, rate, 1), rate, 1)
        val fixedGainRange = plateauRange(input.map { it * 0.5f }.toFloatArray(), rate, 1)
        assertTrue("imported voice meets SC-008 on this fixture", voiceRange <= dryRange * 0.70)
        assertTrue("turning everything down cannot satisfy SC-008", fixedGainRange > dryRange * 0.70)
    }

    private fun checkOffIdentity(explicitSetting: Boolean) {
        for ((rate, channels) in listOf(44_100 to 1, 48_000 to 2, 96_000 to 3)) {
            // Binary fractions including signed zero and values above full scale; raw bytes must agree.
            val input = FloatArray(4096 * channels) { floatArrayOf(-0.0f, 0f, 0.125f, -0.5f, 1.25f, -1.5f)[it % 6] }
            for (colored in listOf(false, true)) {
                val delegate = CaptureSink()
                val processors = chain(colored = colored)
                val sink = if (explicitSetting) nightSink(delegate, processors) { false }
                    else FloatChainAudioSink(delegate, processors)
                configure(sink, rate, channels)
                feed(sink, input, channels, 257)
                sink.playToEndOfStream()
                val expected = legacy(input, rate, channels, chain(colored = colored))
                assertArrayEquals("001 bits, $rate/$channels, colored=$colored", bytes(expected), delegate.received.toByteArray())
            }
        }
    }

    private fun assertRamp(output: FloatArray, rate: Int, channels: Int, enabled: Boolean) {
        val frames = rate * 50 / 1000
        val from = if (enabled) DRY else DRY * MAKEUP
        val to = if (enabled) DRY * MAKEUP else DRY
        // Linear crossfade; one frame of endpoint convention plus float roundoff is allowed.
        val stepBound = abs(to - from) / frames + 2e-7f
        val endpointTolerance = abs(to - from) * 2 / frames + 2e-7f
        assertEquals("100 ms of output", rate / 10 * channels, output.size)
        for (channel in 0 until channels) {
            var previous = from
            for (frame in 0 until output.size / channels) {
                val sample = output[frame * channels + channel]
                assertTrue("toggle step at frame $frame ch $channel exceeds $stepBound",
                    sample.isFinite() && abs(sample - previous) <= stepBound)
                previous = sample
            }
            for (millis in listOf(0, 10, 25, 40, 50, 75, 99)) {
                val frame = rate * millis / 1000
                val expected = from + (to - from) * (frame.toFloat() / frames).coerceAtMost(1f)
                assertEquals("$millis ms, enabled=$enabled, channel=$channel", expected,
                    output[frame * channels + channel], endpointTolerance)
            }
            assertEquals("OFF eventually returns exact bypass bits", to.toRawBits(),
                output[output.size - channels + channel].toRawBits())
        }
    }

    private fun chain(gainDb: Float = 0f, colored: Boolean = false): List<AudioProcessor> = listOf(
        GainProcessor().apply { setTargetGainDb(gainDb) },
        StereoWidthProcessor().apply { if (colored) setWidth(0.6f) },
        EqualizerProcessor().apply { if (colored) setGains(FloatArray(10) { if (it == 3) 3f else 0f }) },
        PreampProcessor().apply { if (colored) setPreampDb(-3f) },
    )

    private fun nightSink(delegate: AudioSink, processors: List<AudioProcessor>, setting: () -> Boolean): FloatChainAudioSink {
        val constructor = FloatChainAudioSink::class.java.declaredConstructors.singleOrNull {
            it.parameterTypes.contentEquals(arrayOf(AudioSink::class.java, List::class.java,
                Function1::class.java, Function0::class.java))
        }
        assertNotNull("T015: FloatChainAudioSink(delegate, chainProcessors, onStreamStart, nightMode: () -> Boolean)", constructor)
        try {
            return constructor!!.apply { isAccessible = true }.newInstance(delegate, processors, null, setting) as FloatChainAudioSink
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun configure(sink: FloatChainAudioSink, rate: Int, channels: Int) {
        sink.configure(AudioSink.AudioSinkConfig.Builder(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(rate).setChannelCount(channels).setPcmEncoding(C.ENCODING_PCM_FLOAT).build()).build())
    }

    private fun feed(sink: FloatChainAudioSink, input: FloatArray, channels: Int, chunk: Int) {
        if (input.isEmpty()) {
            assertTrue(sink.handleBuffer(buffer(input), 0L, 1))
        }
        for (offset in input.indices step chunk * channels) {
            val end = minOf(input.size, offset + chunk * channels)
            val data = buffer(input.copyOfRange(offset, end))
            assertTrue("unthrottled sink consumes the buffer", sink.handleBuffer(data, 0L, 1))
            assertFalse("no input lost", data.hasRemaining())
        }
    }

    private fun render(input: FloatArray, rate: Int, channels: Int, enabled: Boolean): FloatArray {
        val delegate = CaptureSink()
        val sink = nightSink(delegate, chain()) { enabled }
        configure(sink, rate, channels)
        feed(sink, input, channels, 1024)
        sink.playToEndOfStream()
        return delegate.samples().also { assertEquals("no dropped/added samples", input.size, it.size) }
    }

    private fun legacy(input: FloatArray, rate: Int, channels: Int, processors: List<AudioProcessor>): FloatArray {
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(
            listOf(PcmToFloatProcessor(), ToFloatPcmAudioProcessor()) + processors))
        pipeline.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT))
        pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val result = ByteArrayOutputStream()
        for (offset in input.indices step 257 * channels) {
            pipeline.queueInput(buffer(input.copyOfRange(offset, minOf(input.size, offset + 257 * channels))))
            var output = pipeline.getOutput()
            while (output.hasRemaining()) {
                val data = ByteArray(output.remaining())
                output.get(data)
                result.write(data)
                output = pipeline.getOutput()
            }
        }
        return floats(result.toByteArray())
    }

    private fun voice(input: FloatArray, rate: Int, channels: Int): FloatArray {
        val planar = Array(channels) { channel -> FloatArray(input.size / channels) { input[it * channels + channel] } }
        Compressor(rate, channels, Preset.VOICE).process(planar, planar)
        return FloatArray(input.size) { planar[it % channels][it / channels] }
    }

    private fun plateauRange(samples: FloatArray, rate: Int, channels: Int): Double {
        val levels = listOf(2, 5, 8).map { second ->
            val first = second * rate * channels
            val end = first + rate * channels
            var squares = 0.0
            for (i in first until end) squares += samples[i].toDouble().pow(2)
            20 * log10(sqrt(squares / (end - first)))
        }
        assertTrue("finite, audible measurement windows", levels.all { it.isFinite() })
        return levels.max() - levels.min()
    }

    private fun bytes(samples: FloatArray): ByteArray = ByteBuffer.allocate(samples.size * 4)
        .order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(samples) }.array()

    private fun buffer(samples: FloatArray): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 4)
        .order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(samples) }

    private fun floats(bytes: ByteArray): FloatArray = FloatArray(bytes.size / 4).also {
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(it)
    }

    private class CaptureSink(private val limit: Boolean = false) : AudioSink {
        val received = ByteArrayOutputStream()
        val limited = ArrayList<Float>()
        var limitedSamples = 0
        fun samples(): FloatArray = FloatArray(received.size() / 4).also {
            ByteBuffer.wrap(received.toByteArray()).order(ByteOrder.nativeOrder()).asFloatBuffer().get(it)
        }
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            received.write(data)
            if (limit) {
                val samples = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).asFloatBuffer()
                while (samples.hasRemaining()) {
                    val sample = samples.get()
                    if (abs(sample) > CEILING) limitedSamples++
                    limited += sample.coerceIn(-CEILING, CEILING)
                }
            }
            return true
        }
        override fun configure(config: AudioSink.AudioSinkConfig) = Unit
        override fun getFormatSupport(format: Format) = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun supportsFormat(format: Format) = true
        override fun setListener(listener: AudioSink.Listener) = Unit
        override fun getCurrentPositionUs(initialize: Boolean) = 0L
        override fun play() = Unit
        override fun handleDiscontinuity() = Unit
        override fun playToEndOfStream() = Unit
        override fun isEnded() = true
        override fun hasPendingData() = false
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
        override fun getPlaybackParameters() = PlaybackParameters.DEFAULT
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
        override fun getSkipSilenceEnabled() = false
        override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit
        override fun getAudioAttributes() = AudioAttributes.DEFAULT
        override fun setAudioSessionId(audioSessionId: Int) = Unit
        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
        override fun getAudioTrackBufferSizeUs() = 0L
        override fun enableTunnelingV21() = Unit
        override fun disableTunneling() = Unit
        override fun setVolume(volume: Float) = Unit
        override fun pause() = Unit
        override fun flush() = Unit
        override fun reset() = Unit
    }

    private companion object {
        const val DRY = 0.04f // Below voice's -23 dBFS knee: exact, stationary +6 dB makeup.
        val MAKEUP = 10.0.pow(6.0 / 20).toFloat()
        val CEILING = 10.0.pow(-1.0 / 20).toFloat()
    }
}
