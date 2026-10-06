package me.misa198.airmedy.player.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class DspProcessorRampTest {

    private val blockFrames = 480

    @Test
    fun widthChangeRampsWithoutAStep() {
        val rate = 48000
        val changeFrame = rate / 2
        val totalFrames = changeFrame + rate / 10
        val processor = StereoWidthProcessor()
        var changed = false
        val out = run(
            processor, rate, 2, C.ENCODING_PCM_FLOAT, totalFrames,
            sample = { _, c -> if (c == 0) 0.5f else -0.5f },
            onBlock = { blockIndex ->
                if (!changed && blockIndex * blockFrames >= changeFrame) {
                    changed = true
                    processor.setWidth(0f)
                }
            },
        )

        for (f in 0 until changeFrame) {
            assertEquals("L at frame $f must be 0.5 before the width change", 0.5f, out[f * 2], 0f)
        }

        var maxStep = 0f
        for (f in 1 until totalFrames) {
            val d = abs(out[f * 2] - out[(f - 1) * 2])
            if (d > maxStep) maxStep = d
        }
        assertTrue("max L step $maxStep exceeds 0.01", maxStep <= 0.01f)

        val settleFrame = changeFrame + rate * 30 / 1000
        for (f in settleFrame until totalFrames) {
            assertTrue("L at frame $f is ${out[f * 2]} (expected ~0)", abs(out[f * 2]) <= 1e-6f)
        }
    }

    @Test
    fun preampChangeRampsWithoutAStep() {
        val rate = 48000
        val changeFrame = rate / 2
        val totalFrames = changeFrame + rate / 10
        val processor = PreampProcessor()
        var changed = false
        val out = run(
            processor, rate, 1, C.ENCODING_PCM_FLOAT, totalFrames,
            sample = { _, _ -> 0.25f },
            onBlock = { blockIndex ->
                if (!changed && blockIndex * blockFrames >= changeFrame) {
                    changed = true
                    processor.setPreampDb(12f)
                }
            },
        )

        for (f in 0 until changeFrame) {
            assertEquals("sample at frame $f must be 0.25 before the preamp change", 0.25f, out[f], 0f)
        }

        var maxStep = 0f
        for (f in 1 until totalFrames) {
            val d = abs(out[f] - out[f - 1])
            if (d > maxStep) maxStep = d
        }
        assertTrue("max step $maxStep exceeds 0.01", maxStep <= 0.01f)

        val settleFrame = changeFrame + rate * 30 / 1000
        val target = 0.25 * 10.0.pow(12.0 / 20.0)
        for (f in settleFrame until totalFrames) {
            assertTrue("out at frame $f is ${out[f]} (expected ~$target)", abs(out[f] - target) <= 1e-5 * target)
        }
    }

    @Test
    fun eqProcessorChangeKeepsStateAndReachesTarget() {
        val rate = 48000
        val frames = rate * 2
        val processor = EqualizerProcessor()
        processor.setGains(FloatArray(10).also { it[1] = 6f })
        val out = run(
            processor, rate, 1, C.ENCODING_PCM_FLOAT, frames,
            sample = { f, _ -> (0.25 * sin(2.0 * PI * 64.0 * f / rate)).toFloat() },
            onBlock = { blockIndex ->
                if (blockIndex == 100) processor.setGains(FloatArray(10).also { it[1] = 9f })
            },
        )

        val stepEarly = maxStep(out, rate, rate + rate / 5)
        val stepLate = maxStep(out, (1.8 * rate).toInt(), frames)
        assertTrue("gain change must not reset state: step $stepEarly vs steady $stepLate", stepEarly <= 1.5f * stepLate)

        val target = 0.25 * 10.0.pow(9.0 / 20.0)
        val settled = peak(out, (1.75 * rate).toInt(), frames).toDouble()
        assertTrue("settled peak must be non-zero", settled > 0.0)
        val peakErrorDb = abs(20.0 * log10(settled / target))
        assertTrue("settled peak $settled within 0.2 dB of $target (was $peakErrorDb dB)", peakErrorDb <= 0.2)
    }

    @Test
    fun neutralProcessorsPassThroughExactly() {
        val rate = 48000
        val channels = 2
        val random = Random(2024L)
        val floatSamples = FloatArray(blockFrames * channels) { (random.nextDouble() * 2.0 - 1.0).toFloat() }
        val shortSamples = ShortArray(blockFrames * channels) { (random.nextInt(65536) - 32768).toShort() }

        val makers: List<() -> BaseAudioProcessor> = listOf(
            { EqualizerProcessor() },
            { StereoWidthProcessor() },
            { PreampProcessor() },
        )
        for (make in makers) {
            val floatProcessor = make()
            val floatFormat = floatProcessor.configure(
                AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT),
            )
            assertEquals(C.ENCODING_PCM_FLOAT, floatFormat.encoding)
            assertTrue("processor must be active after configure", floatProcessor.isActive)
            floatProcessor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            floatProcessor.queueInput(floatInput(floatSamples))
            assertArrayEquals("neutral float input must pass through bit-identically", floatSamples, drainFloat(floatProcessor.getOutput()), 0f)

            val shortProcessor = make()
            val shortFormat = shortProcessor.configure(
                AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT),
            )
            assertEquals(C.ENCODING_PCM_FLOAT, shortFormat.encoding)
            assertTrue("processor must be active after configure", shortProcessor.isActive)
            shortProcessor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            shortProcessor.queueInput(shortInput(shortSamples))
            val shortOut = drainFloat(shortProcessor.getOutput())
            for (i in shortSamples.indices) {
                assertEquals("16-bit sample $i must equal s / 32768f", shortSamples[i] / 32768f, shortOut[i], 0f)
            }
        }
    }

    @Test
    fun unsupportedEncodingIsRejected() {
        val processors = listOf(EqualizerProcessor(), StereoWidthProcessor(), PreampProcessor())
        for (p in processors) {
            try {
                p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_24BIT))
                fail("expected UnhandledAudioFormatException for ${p.javaClass.simpleName}")
            } catch (e: AudioProcessor.UnhandledAudioFormatException) {
                // expected
            }
        }
    }

    @Test
    fun settersFromAnotherThreadAreAppliedAtTheNextBuffer() {
        val rate = 48000

        val eq = EqualizerProcessor()
        configure(eq, rate, 1, C.ENCODING_PCM_FLOAT)
        val t1 = Thread { eq.setGains(FloatArray(10).also { it[1] = 6f }) }
        t1.start()
        t1.join()
        val sineIn = FloatArray(blockFrames) { f -> (0.25 * sin(2.0 * PI * 64.0 * f / rate)).toFloat() }
        val eqOut = runSingleBlock(eq, sineIn)
        assertTrue("EQ gains from another thread were not applied", maxAbsDiff(eqOut, sineIn) > 1e-3f)

        val width = StereoWidthProcessor()
        configure(width, rate, 2, C.ENCODING_PCM_FLOAT)
        val t2 = Thread { width.setWidth(0f) }
        t2.start()
        t2.join()
        val dcStereo = FloatArray(blockFrames * 2) { i -> if (i % 2 == 0) 0.5f else -0.5f }
        val widthOut = runSingleBlock(width, dcStereo)
        for (f in blockFrames - 100 until blockFrames) {
            assertTrue("width change not in effect at frame $f", abs(widthOut[f * 2] - 0.5f) > 1e-3f)
        }

        val preamp = PreampProcessor()
        configure(preamp, rate, 1, C.ENCODING_PCM_FLOAT)
        val t3 = Thread { preamp.setPreampDb(12f) }
        t3.start()
        t3.join()
        val dcMono = FloatArray(blockFrames) { 0.25f }
        val preampOut = runSingleBlock(preamp, dcMono)
        for (f in blockFrames - 100 until blockFrames) {
            assertTrue("preamp change not in effect at frame $f", abs(preampOut[f] - 0.25f) > 1e-3f)
        }
    }

    @Test
    fun reconfigureDoesNotDisturbTheDrainingStream() {
        val stereoDc: (Int, Int) -> Float = { _, c -> if (c == 0) 0.5f else -0.5f }

        // Preamp: +6 dB, stereo DC.
        val preamp = PreampProcessor()
        preamp.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
        preamp.flush(AudioProcessor.StreamMetadata.DEFAULT)
        preamp.setPreampDb(6f)
        feed(preamp, 24000, 2, stereoDc)
        val preampNewFormat = preamp.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT))
        val preampDrained = feed(preamp, 4800, 2, stereoDc)
        val preampGain = Preamp.linearGain(6f)
        for (i in preampDrained.indices) {
            val expected = (if (i % 2 == 0) 0.5f else -0.5f) * preampGain
            assertEquals("preamp drained sample $i", expected, preampDrained[i], 1e-6f)
        }
        preamp.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val preampAfter = feed(preamp, 4410, 1) { _, _ -> 0.5f }
        for (i in preampAfter.indices) {
            assertEquals("preamp mono sample $i", 0.5f * preampGain, preampAfter[i], 1e-6f)
        }
        assertFormat(preampNewFormat, 44100, 1)

        // Stereo width: width 0 collapses to mono.
        val width = StereoWidthProcessor()
        width.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
        width.flush(AudioProcessor.StreamMetadata.DEFAULT)
        width.setWidth(0f)
        feed(width, 24000, 2, stereoDc)
        val widthNewFormat = width.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT))
        val widthDrained = feed(width, 4800, 2, stereoDc)
        for (i in widthDrained.indices) {
            assertEquals("width drained sample $i", 0f, widthDrained[i], 1e-6f)
        }
        width.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val widthAfter = feed(width, 4410, 1) { _, _ -> 0.5f }
        assertEquals("width mono must pass through", 0.5f, widthAfter[0], 0f)
        assertFormat(widthNewFormat, 44100, 1)

        // EQ: band 1 (+6 dB) on a 64 Hz stereo sine.
        val eq = EqualizerProcessor()
        eq.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
        eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
        eq.setGains(FloatArray(10).also { it[1] = 6f })
        val eqBefore = feed(eq, 24000, 2) { f, _ -> (0.25 * sin(2.0 * PI * 64.0 * f / 48000.0)).toFloat() }
        val eqNewFormat = eq.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT))
        val eqDrained = feed(eq, 4800, 2) { f, _ -> (0.25 * sin(2.0 * PI * 64.0 * (f + 24000) / 48000.0)).toFloat() }
        for (ch in 0 until 2) {
            val stepDrained = maxStepChannel(eqDrained, 2, ch, 0, 2400)
            val stepBefore = maxStepChannel(eqBefore, 2, ch, 21600, 24000)
            assertTrue("EQ channel $ch drained step $stepDrained vs before $stepBefore", stepDrained <= 1.5f * stepBefore)
            val boundaryStep = abs(eqDrained[ch] - eqBefore[(24000 - 1) * 2 + ch])
            assertTrue("EQ channel $ch boundary step $boundaryStep vs before $stepBefore", boundaryStep <= 1.5f * stepBefore)
        }
        eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val eqAfter = feed(eq, 4410, 1) { f, _ -> (0.25 * sin(2.0 * PI * 64.0 * f / 44100.0)).toFloat() }
        val eqTarget = 0.25 * 10.0.pow(6.0 / 20.0)
        val eqPeak = peak(eqAfter, 2205, 4410).toDouble()
        assertTrue("EQ peak must be non-zero", eqPeak > 0.0)
        val eqErrorDb = abs(20.0 * log10(eqPeak / eqTarget))
        assertTrue("EQ peak $eqPeak within 0.2 dB of $eqTarget (was $eqErrorDb dB)", eqErrorDb <= 0.2)
        assertFormat(eqNewFormat, 44100, 1)
    }

    private fun configure(processor: BaseAudioProcessor, rate: Int, channels: Int, encoding: Int) {
        processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    private fun run(
        processor: BaseAudioProcessor,
        rate: Int,
        channels: Int,
        encoding: Int,
        totalFrames: Int,
        sample: (frame: Int, channel: Int) -> Float,
        onBlock: (blockIndex: Int) -> Unit = {},
    ): FloatArray {
        processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val out = FloatArray(totalFrames * channels)
        var frame = 0
        var blockIndex = 0
        while (frame < totalFrames) {
            onBlock(blockIndex)
            val frames = minOf(blockFrames, totalFrames - frame)
            val samples = FloatArray(frames * channels)
            for (f in 0 until frames) {
                for (c in 0 until channels) {
                    samples[f * channels + c] = sample(frame + f, c)
                }
            }
            processor.queueInput(floatInput(samples))
            val produced = drainFloat(processor.getOutput())
            System.arraycopy(produced, 0, out, frame * channels, produced.size)
            frame += frames
            blockIndex++
        }
        return out
    }

    private fun runSingleBlock(processor: BaseAudioProcessor, samples: FloatArray): FloatArray {
        processor.queueInput(floatInput(samples))
        return drainFloat(processor.getOutput())
    }

    private fun feed(
        processor: BaseAudioProcessor,
        frames: Int,
        channels: Int,
        sample: (frame: Int, channel: Int) -> Float,
    ): FloatArray {
        val out = FloatArray(frames * channels)
        var done = 0
        while (done < frames) {
            val count = minOf(blockFrames, frames - done)
            val samples = FloatArray(count * channels)
            for (f in 0 until count) {
                for (c in 0 until channels) {
                    samples[f * channels + c] = sample(done + f, c)
                }
            }
            processor.queueInput(floatInput(samples))
            val produced = drainFloat(processor.getOutput())
            System.arraycopy(produced, 0, out, done * channels, produced.size)
            done += count
        }
        return out
    }

    private fun maxStepChannel(buffer: FloatArray, channels: Int, channel: Int, from: Int, until: Int): Float {
        var m = 0f
        for (f in from + 1 until until) {
            val d = abs(buffer[f * channels + channel] - buffer[(f - 1) * channels + channel])
            if (d > m) m = d
        }
        return m
    }

    private fun assertFormat(format: AudioProcessor.AudioFormat, sampleRate: Int, channelCount: Int) {
        assertEquals(sampleRate, format.sampleRate)
        assertEquals(channelCount, format.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, format.encoding)
    }

    private fun floatInput(samples: FloatArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(samples)
        buffer.limit(samples.size * 4)
        buffer.position(0)
        return buffer
    }

    private fun shortInput(samples: ShortArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        buffer.asShortBuffer().put(samples)
        buffer.limit(samples.size * 2)
        buffer.position(0)
        return buffer
    }

    private fun drainFloat(buffer: ByteBuffer): FloatArray {
        val view = buffer.asFloatBuffer()
        val out = FloatArray(view.remaining())
        view.get(out)
        return out
    }
}
