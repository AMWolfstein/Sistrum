package com.amwolfstein.sistrum.player

import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.player.dsp.EqualizerProcessor
import com.amwolfstein.sistrum.player.dsp.GainProcessor
import com.amwolfstein.sistrum.player.dsp.PcmToFloatProcessor
import com.amwolfstein.sistrum.player.dsp.PreampProcessor
import com.amwolfstein.sistrum.player.dsp.StereoWidthProcessor
import com.amwolfstein.sistrum.player.media3.FloatChainAudioSink
import com.amwolfstein.sistrum.player.media3.Media3PlayerFactory
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** T020 / SC-008. Captures bytes accepted by a real Android float AudioSink, after Night mode.
 * The tap precedes Android's mixer/session effect: it cannot prove the hardware limiter ceiling.
 * T021 runs these tests; no microphone, playback-capture permission or external corpus is required.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class NightModeDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 60_000)
    fun offIsBitIdenticalToExplicit001Pipeline() {
        val input = FloatArray(RATE * 2) { floatArrayOf(-0.0f, 0f, 0.125f, -0.5f, 0.75f, -0.25f)[it % 6] }
        for (colored in listOf(false, true)) {
            val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(
                listOf(PcmToFloatProcessor(), ToFloatPcmAudioProcessor()) + chain(colored)))
            val expected = ByteArrayOutputStream()
            try {
                pipeline.configure(AudioProcessor.AudioFormat(RATE, 2, C.ENCODING_PCM_FLOAT))
                pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
                for (offset in input.indices step 514) {
                    pipeline.queueInput(buffer(input.copyOfRange(offset, minOf(input.size, offset + 514))))
                    val output = pipeline.getOutput()
                    val bytes = ByteArray(output.remaining())
                    output.get(bytes)
                    expected.write(bytes)
                }
                assertArrayEquals("001 raw float bits, colored=$colored", expected.toByteArray(), render(input, false, colored))
            } finally { pipeline.reset() }
        }
        report("SC-008 OFF matches explicit 001 conversion/DSP bytes (neutral and colored)")
    }

    @Test(timeout = 90_000)
    fun onReducesSettledPlateauRangeByAtLeastThirtyPercent() {
        // Same externally specified plateaus as T014: 3s quiet/loud/quiet, stereo anti-phase.
        val input = FloatArray(RATE * 9 * 2) { index ->
            val amplitude = if (index / 2 / (RATE * 3) == 1) 0.8f else 0.06f
            if (index % 2 == 0) amplitude else -amplitude
        }
        val off = floats(render(input, false))
        val on = floats(render(input, true))
        val offRange = range(off)
        val onRange = range(on)
        assertEquals("fixture dB range", 20 * log10(0.8 / 0.06), offRange, 1e-5)
        assertTrue("SC-008 OFF=$offRange ON=$onRange dB", onRange <= offRange * 0.70)
        report("SC-008 offRangeDb=$offRange onRangeDb=$onRange reduction=${1 - onRange / offRange}; pre-session tap")
    }

    private fun render(input: FloatArray, enabled: Boolean, colored: Boolean = false): ByteArray {
        check(context.packageName.endsWith(".qa")) { "T020 requires the .qa target" }
        val factory = Media3PlayerFactory(context)
        val received = ByteArrayOutputStream()
        val sink = factory.call {
            val inner = DefaultAudioSink.Builder(context).setEnableFloatOutput(true).build()
            val capture = object : ForwardingAudioSink(inner) {
                override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
                    val copy = buffer.duplicate()
                    val before = buffer.position()
                    val accepted = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                    // Record only consumed bytes; a partial AudioTrack write must not duplicate samples.
                    val bytes = ByteArray(buffer.position() - before)
                    copy.get(bytes)
                    received.write(bytes)
                    return accepted
                }
            }
            FloatChainAudioSink(capture, chain(colored), nightMode = { enabled })
        }
        try {
            factory.call {
                sink.configure(AudioSink.AudioSinkConfig.Builder(Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(RATE).setChannelCount(2)
                    .setPcmEncoding(C.ENCODING_PCM_FLOAT).build()).build())
                sink.setVolume(0f)
                sink.play()
            }
            // Keep AudioTrack backpressure waits on the test thread. Each looper call is short,
            // so the nine-second SC-008 signal cannot exceed factory.call's five-second bound.
            val deadline = SystemClock.elapsedRealtime() + 30_000L
            for (offset in input.indices step 514) {
                val data = buffer(input.copyOfRange(offset, minOf(input.size, offset + 514)))
                while (!factory.call { sink.handleBuffer(data, offset.toLong() / 2 * 1_000_000L / RATE, 1) }) {
                    assertTrue("AudioTrack backpressure timed out", SystemClock.elapsedRealtime() < deadline)
                    SystemClock.sleep(2L)
                }
                assertFalse("input left unconsumed", data.hasRemaining())
            }
            return factory.call {
                sink.playToEndOfStream()
                received.toByteArray().also { assertEquals("no lost or added float samples", input.size * 4, it.size) }
            }
        } finally { factory.call { sink.reset(); sink.release() } }
    }

    private fun chain(colored: Boolean): List<AudioProcessor> = listOf(
        GainProcessor(),
        StereoWidthProcessor().apply { if (colored) setWidth(0.6f) },
        EqualizerProcessor().apply { if (colored) setGains(FloatArray(10) { if (it == 3) 3f else 0f }) },
        PreampProcessor().apply { if (colored) setPreampDb(-3f) },
    )

    private fun range(samples: FloatArray): Double {
        val levels = listOf(2, 5, 8).map { second ->
            var energy = 0.0
            for (i in second * RATE * 2 until (second + 1) * RATE * 2) energy += samples[i].toDouble() * samples[i]
            20 * log10(sqrt(energy / (RATE * 2)))
        }
        assertTrue("finite audible windows", levels.all { it.isFinite() })
        assertTrue("finite captured samples", samples.all { it.isFinite() })
        return levels.max() - levels.min()
    }

    private fun buffer(samples: FloatArray): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 4)
        .order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(samples) }
    private fun floats(bytes: ByteArray) = FloatArray(bytes.size / 4).also {
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(it)
    }
    private fun report(line: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("T020-Night", line) })
    }
    private companion object { const val RATE = 48_000 }
}
