package me.misa198.airmedy.player.media3

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.primitives.ImmutableIntArray
import me.misa198.airmedy.player.dsp.BiquadDesign
import me.misa198.airmedy.player.dsp.EqualizerProcessor
import me.misa198.airmedy.player.dsp.GainProcessor
import me.misa198.airmedy.player.dsp.PreampProcessor
import me.misa198.airmedy.player.dsp.StereoWidthProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * T048d: renders audio through [FloatChainAudioSink] with the real DSP chain while EQ, preamp and
 * stereo-width settings change between buffers like a user dragging sliders, and asserts the output
 * has no click (discontinuity) above a stated threshold. It replaces an owner listening check, so it
 * must be able to fail: [metricCatchesAnUnrampedStep] is a negative control.
 *
 * ## Signal
 * Stereo, smooth low-frequency input so a click stands out:
 * `L = 0.12*sin(2pi*60t) + 0.08*sin(2pi*250t) + 0.05*sin(2pi*1000t)`, and `R` is the same with the
 * three partials phase-shifted by +0.7, +1.3 and +2.1 rad so a width change has an effect. Length
 * 8 s (scenarios 5 and 6 use 6 s per the brief). Fed in buffers of 1024 frames (2048 at 96 kHz)
 * through [FloatChainAudioSink.handleBuffer] with an unthrottled fake delegate.
 *
 * ## Discontinuity metric
 * Per channel, the second difference `d2[n] = y[n] - 2*y[n-1] + y[n-2]`; the click of a render is
 * `click(y) = max |d2|` over the analysed region, skipping the first 50 ms of the render.
 *
 * ## Threshold
 * For a drag render,
 * `click(drag) <= 1.5 * max(click(static_s) for s in the drag's start, end and most extreme settings) + 1e-5`
 * where `static_s` is the same input rendered with setting `s` applied before `configure` (a fresh
 * sink/chain per render). Ties to the chain `GainProcessor(), StereoWidthProcessor(),
 * EqualizerProcessor(), PreampProcessor()`.
 */
@OptIn(UnstableApi::class)
class DspSliderDragTest {

    private val bandCount = BiquadDesign.FrequenciesHz.size
    private val clickFactor = 1.5f
    private val clickFloor = 1e-5f

    private val staticClickCache = HashMap<String, Float>()

    @Test
    fun eqAllBandsDragged() {
        val plan = Plan(48000, C.ENCODING_PCM_16BIT, 1024, 8 * 48000)
        val signal = Signal(plan)
        val flat = FloatArray(bandCount)
        val settings = ArrayList<Settings>()
        settings += Settings(flat.copyOf(), 0f, 1f)
        for (k in 1..24) settings += Settings(flat.map { k * 0.5f }.toFloatArray(), 0f, 1f)
        for (k in 1..48) settings += Settings(flat.map { 12f - k * 0.5f }.toFloatArray(), 0f, 1f)
        for (k in 1..24) settings += Settings(flat.map { -12f + k * 0.5f }.toFloatArray(), 0f, 1f)
        padToBuffers(settings, plan, Settings(FloatArray(bandCount), 0f, 1f))

        val extremes = listOf(
            Settings(FloatArray(bandCount) { 12f }, 0f, 1f),
            Settings(FloatArray(bandCount) { -12f }, 0f, 1f),
        )
        runScenario("eqAllBandsDragged", plan, signal, settings, extremes)
    }

    @Test
    fun eqSingleBandFastDrag() {
        val plan = Plan(48000, C.ENCODING_PCM_16BIT, 1024, 8 * 48000)
        val signal = Signal(plan)
        val bufferCount = bufferCount(plan)
        val twoSecondBuffers = 2 * plan.sampleRate / plan.bufferFrames
        val settings = ArrayList<Settings>(bufferCount)
        settings += Settings(FloatArray(bandCount), 0f, 1f)
        for (i in 1 until bufferCount) {
            val eq = FloatArray(bandCount)
            if (i <= twoSecondBuffers) eq[3] = if (i % 2 == 1) 12f else -12f
            settings += Settings(eq, 0f, 1f)
        }

        val high = FloatArray(bandCount).also { it[3] = 12f }
        val low = FloatArray(bandCount).also { it[3] = -12f }
        val extremes = listOf(
            Settings(high, 0f, 1f),
            Settings(low, 0f, 1f),
        )
        runScenario("eqSingleBandFastDrag", plan, signal, settings, extremes)
    }

    @Test
    fun preampDragged() {
        val plan = Plan(48000, C.ENCODING_PCM_16BIT, 1024, 8 * 48000)
        val signal = Signal(plan)
        val flat = FloatArray(bandCount)
        val settings = ArrayList<Settings>()
        settings += Settings(flat.copyOf(), -12f, 1f)
        for (k in 1..96) settings += Settings(flat.copyOf(), -12f + k * 0.25f, 1f)
        for (k in 1..96) settings += Settings(flat.copyOf(), 12f - k * 0.25f, 1f)
        padToBuffers(settings, plan, Settings(flat.copyOf(), -12f, 1f))

        val extremes = listOf(
            Settings(flat.copyOf(), 12f, 1f),
            Settings(flat.copyOf(), -12f, 1f),
        )
        runScenario("preampDragged", plan, signal, settings, extremes)
    }

    @Test
    fun widthDragged() {
        val plan = Plan(48000, C.ENCODING_PCM_16BIT, 1024, 8 * 48000)
        val signal = Signal(plan)
        val flat = FloatArray(bandCount)
        val settings = ArrayList<Settings>()
        settings += Settings(flat.copyOf(), 0f, 1f)
        for (k in 1..50) settings += Settings(flat.copyOf(), 0f, 1f - k * 0.02f)
        for (k in 1..100) settings += Settings(flat.copyOf(), 0f, k * 0.02f)
        for (k in 1..50) settings += Settings(flat.copyOf(), 0f, 2f - k * 0.02f)
        padToBuffers(settings, plan, Settings(flat.copyOf(), 0f, 1f))

        val extremes = listOf(
            Settings(flat.copyOf(), 0f, 0f),
            Settings(flat.copyOf(), 0f, 2f),
        )
        runScenario("widthDragged", plan, signal, settings, extremes)
    }

    @Test
    fun allThreeAtOnce() {
        val plan = Plan(48000, C.ENCODING_PCM_16BIT, 1024, 6 * 48000)
        val signal = Signal(plan)
        val (settings, extreme) = randomWalk(plan)
        runScenario("allThreeAtOnce", plan, signal, settings, listOf(extreme))
    }

    @Test
    fun hiRes96kAllThreeAtOnce() {
        val plan = Plan(96000, C.ENCODING_PCM_24BIT, 2048, 6 * 96000)
        val signal = Signal(plan)
        val (settings, extreme) = randomWalk(plan)
        runScenario("hiRes96kAllThreeAtOnce", plan, signal, settings, listOf(extreme))
    }

    @Test
    fun metricCatchesAnUnrampedStep() {
        val plan = Plan(48000, C.ENCODING_PCM_16BIT, 1024, 8 * 48000)
        val signal = Signal(plan)
        val neutral = Settings(FloatArray(bandCount), 0f, 1f)
        val static = render(plan, signal) { neutral }

        val channels = 2
        val skipFrames = (0.05 * plan.sampleRate).toInt()
        val totalFrames = static.size / channels
        var stepFrame = skipFrames + 2
        var best = -1f
        for (f in skipFrames + 2 until totalFrames) {
            if (f % plan.bufferFrames == 0) continue
            val magnitude = abs(static[2 * f]) + abs(static[2 * f + 1])
            if (magnitude > best) {
                best = magnitude
                stepFrame = f
            }
        }

        val unmodifiedClick = click(static, channels, plan.sampleRate)
        val threshold = clickFactor * unmodifiedClick + clickFloor

        val stepped = static.copyOf()
        val gain = 10f.pow(1f / 20f)
        for (i in stepFrame * channels until stepped.size) stepped[i] *= gain
        val steppedClick = click(stepped, channels, plan.sampleRate)

        println(
            "[metricCatchesAnUnrampedStep] stepFrame=$stepFrame unmodified=$unmodifiedClick " +
                "stepped=$steppedClick threshold=$threshold",
        )
        assertTrue(
            "negative control must exceed the threshold: stepped=$steppedClick threshold=$threshold",
            steppedClick > threshold,
        )
    }

    private fun runScenario(
        name: String,
        plan: Plan,
        signal: Signal,
        settings: List<Settings>,
        extremes: List<Settings>,
    ) {
        assertEquals("scenario $name must supply one setting per buffer", bufferCount(plan), settings.size)

        val dragOut = render(plan, signal) { i -> settings[i] }
        assertWellFormed(name, dragOut, plan.frameCount)

        val statics = (listOf(settings.first(), settings.last()) + extremes).distinctBy { it.key() }
        val staticClicks = statics.associateWith { staticClick(plan, signal, it) }
        val maxStatic = staticClicks.values.maxOrNull() ?: 0f
        val threshold = clickFactor * maxStatic + clickFloor
        val (dragClick, frame, channel) = clickLocation(dragOut, 2, plan.sampleRate)

        println(
            "[$name] drag=$dragClick threshold=$threshold maxStatic=$maxStatic " +
                "statics=${staticClicks.values} worstFrame=$frame worstChannel=$channel",
        )
        assertTrue(
            "$name: drag click $dragClick exceeds threshold $threshold (static clicks ${staticClicks.values})",
            dragClick <= threshold,
        )
    }

    private fun randomWalk(plan: Plan): Pair<List<Settings>, Settings> {
        val random = Random(20261007L)
        var eq = FloatArray(bandCount)
        var preamp = 0f
        var width = 1f
        val settings = ArrayList<Settings>(bufferCount(plan))
        var extreme = Settings(eq.copyOf(), preamp, width)
        var extremeScore = -1.0
        for (i in 0 until bufferCount(plan)) {
            val current = Settings(eq.copyOf(), preamp, width)
            settings += current
            var score = 0.0
            for (b in 0 until bandCount) {
                val x = eq[b] / 12.0
                score += x * x
            }
            val p = preamp / 12.0
            val w = width - 1.0
            score += p * p + w * w
            if (score > extremeScore) {
                extremeScore = score
                extreme = current
            }
            for (b in 0 until bandCount) {
                eq[b] = (eq[b] + (random.nextFloat() - 0.5f)).coerceIn(-12f, 12f)
            }
            preamp = (preamp + (random.nextFloat() * 0.5f - 0.25f)).coerceIn(-12f, 12f)
            width = (width + (random.nextFloat() * 0.04f - 0.02f)).coerceIn(0f, 2f)
        }
        return settings to extreme
    }

    private fun padToBuffers(settings: ArrayList<Settings>, plan: Plan, filler: Settings) {
        while (settings.size < bufferCount(plan)) settings += filler.copy()
        while (settings.size > bufferCount(plan)) settings.removeAt(settings.size - 1)
    }

    private fun bufferCount(plan: Plan): Int =
        (plan.frameCount + plan.bufferFrames - 1) / plan.bufferFrames

    private fun render(plan: Plan, signal: Signal, settingsAt: (Int) -> Settings): FloatArray {
        val chain = Chain()
        chain.apply(settingsAt(0))
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, chain.processors())
        sink.configure(sinkConfig(pcmFormat(plan.sampleRate, 2, plan.encoding)))

        var start = 0
        var index = 0
        while (start < plan.frameCount) {
            if (index > 0) chain.apply(settingsAt(index))
            val frames = minOf(plan.bufferFrames, plan.frameCount - start)
            val buffer = directBuffer(signal.bufferBytes(start, frames))
            val presentationTimeUs = start.toLong() * 1_000_000L / plan.sampleRate
            drive(sink, buffer, presentationTimeUs)
            start += frames
            index++
        }
        return asFloatArray(fake.received.toByteArray())
    }

    private fun staticClick(plan: Plan, signal: Signal, settings: Settings): Float {
        val key = plan.toString() + "#" + settings.key()
        return staticClickCache.getOrPut(key) {
            click(render(plan, signal) { settings }, 2, plan.sampleRate)
        }
    }

    private fun click(samples: FloatArray, channels: Int, sampleRate: Int): Float =
        clickLocation(samples, channels, sampleRate).first

    private fun clickLocation(samples: FloatArray, channels: Int, sampleRate: Int): Triple<Float, Int, Int> {
        val skipFrames = (0.05 * sampleRate).toInt()
        val totalFrames = samples.size / channels
        var maximum = 0f
        var worstFrame = skipFrames + 2
        var worstChannel = 0
        for (f in skipFrames + 2 until totalFrames) {
            val base = f * channels
            for (c in 0 until channels) {
                val d2 = samples[base + c] - 2f * samples[base - channels + c] + samples[base - 2 * channels + c]
                val magnitude = abs(d2)
                if (magnitude > maximum) {
                    maximum = magnitude
                    worstFrame = f
                    worstChannel = c
                }
            }
        }
        return Triple(maximum, worstFrame, worstChannel)
    }

    private fun assertWellFormed(name: String, samples: FloatArray, expectedFrames: Int) {
        assertEquals("$name: output frame count", expectedFrames, samples.size / 2)
        for (s in samples) assertTrue("$name: produced a non-finite sample", s.isFinite())
    }

    private fun drive(sink: FloatChainAudioSink, buffer: ByteBuffer, presentationTimeUs: Long) {
        var iterations = 0
        while (!sink.handleBuffer(buffer, presentationTimeUs, 1)) {
            if (++iterations > 100_000) error("handleBuffer never completed")
        }
    }

    private fun pcmFormat(sampleRate: Int, channelCount: Int, encoding: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setSampleRate(sampleRate)
        .setChannelCount(channelCount)
        .setPcmEncoding(encoding)
        .build()

    private fun sinkConfig(format: Format): AudioSink.AudioSinkConfig = AudioSink.AudioSinkConfig.Builder(format)
        .setPreferredBufferSizeOverride(0)
        .setOutputChannelMapping(ImmutableIntArray.of())
        .setTimeline(Timeline.EMPTY)
        .build()

    private fun directBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            position(0)
        }

    private fun asFloatArray(buffer: ByteBuffer): FloatArray {
        val copy = buffer.duplicate().order(ByteOrder.nativeOrder())
        val out = FloatArray(copy.remaining() / 4)
        copy.asFloatBuffer().get(out)
        return out
    }

    private fun asFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val out = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(out)
        return out
    }

    private class Settings(val eq: FloatArray, val preampDb: Float, val width: Float) {
        fun copy(): Settings = Settings(eq.copyOf(), preampDb, width)

        fun key(): String = eq.joinToString(",") + "|" + preampDb + "|" + width
    }

    private class Chain {
        private val gain = GainProcessor()
        private val width = StereoWidthProcessor()
        private val equalizer = EqualizerProcessor()
        private val preamp = PreampProcessor()

        fun processors(): List<AudioProcessor> = listOf(gain, width, equalizer, preamp)

        fun apply(settings: Settings) {
            equalizer.setGains(settings.eq)
            width.setWidth(settings.width)
            preamp.setPreampDb(settings.preampDb)
        }
    }

    private data class Plan(
        val sampleRate: Int,
        val encoding: Int,
        val bufferFrames: Int,
        val frameCount: Int,
    )

    private class Signal(private val plan: Plan) {

        val stereo = FloatArray(plan.frameCount * 2)

        init {
            val om = 2.0 * PI
            for (f in 0 until plan.frameCount) {
                val t = f.toDouble() / plan.sampleRate
                stereo[2 * f] = (
                    0.12 * sin(om * 60.0 * t) +
                        0.08 * sin(om * 250.0 * t) +
                        0.05 * sin(om * 1000.0 * t)
                    ).toFloat()
                stereo[2 * f + 1] = (
                    0.12 * sin(om * 60.0 * t + 0.7) +
                        0.08 * sin(om * 250.0 * t + 1.3) +
                        0.05 * sin(om * 1000.0 * t + 2.1)
                    ).toFloat()
            }
        }

        fun bufferBytes(startFrame: Int, frames: Int): ByteArray {
            val bytesPerSample = when (plan.encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                else -> error("unsupported encoding ${plan.encoding}")
            }
            val out = ByteArray(frames * 2 * bytesPerSample)
            for (s in 0 until frames * 2) {
                val value = stereo[startFrame * 2 + s]
                val base = s * bytesPerSample
                if (plan.encoding == C.ENCODING_PCM_16BIT) {
                    val pcm = (value * 32767f).roundToInt().coerceIn(-32768, 32767)
                    out[base] = (pcm and 0xFF).toByte()
                    out[base + 1] = ((pcm shr 8) and 0xFF).toByte()
                } else {
                    val pcm = (value * 8388607f).roundToInt().coerceIn(-8388608, 8388607)
                    out[base] = (pcm and 0xFF).toByte()
                    out[base + 1] = ((pcm shr 8) and 0xFF).toByte()
                    out[base + 2] = ((pcm shr 16) and 0xFF).toByte()
                }
            }
            return out
        }
    }

    @OptIn(UnstableApi::class)
    private class FakeAudioSink(
        private val supportedEncodings: Set<Int> = setOf(C.ENCODING_PCM_FLOAT),
    ) : AudioSink {

        val configs = mutableListOf<AudioSink.AudioSinkConfig>()
        val received = ByteArrayOutputStream()
        var maxBytesPerCall: Int = Int.MAX_VALUE

        override fun configure(config: AudioSink.AudioSinkConfig) {
            configs += config
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            if (maxBytesPerCall <= 0) return false
            val take = minOf(buffer.remaining(), maxBytesPerCall)
            if (take > 0) {
                val copy = ByteArray(take)
                buffer.get(copy)
                received.write(copy)
            }
            return !buffer.hasRemaining()
        }

        override fun getFormatSupport(format: Format): Int =
            if (format.pcmEncoding in supportedEncodings) {
                AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            } else {
                AudioSink.SINK_FORMAT_UNSUPPORTED
            }

        override fun supportsFormat(format: Format): Boolean =
            getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

        override fun playToEndOfStream() = Unit

        override fun flush() = Unit

        override fun reset() = Unit

        override fun hasPendingData(): Boolean = false

        override fun isEnded(): Boolean = false

        override fun setListener(listener: AudioSink.Listener) = Unit

        override fun getCurrentPositionUs(initialize: Boolean): Long = 0L

        override fun play() = Unit

        override fun handleDiscontinuity() = Unit

        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit

        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit

        override fun getSkipSilenceEnabled(): Boolean = false

        override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit

        override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT

        override fun setAudioSessionId(audioSessionId: Int) = Unit

        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit

        override fun getAudioTrackBufferSizeUs(): Long = 0L

        override fun enableTunnelingV21() = Unit

        override fun disableTunneling() = Unit

        override fun setVolume(volume: Float) = Unit

        override fun pause() = Unit
    }
}
