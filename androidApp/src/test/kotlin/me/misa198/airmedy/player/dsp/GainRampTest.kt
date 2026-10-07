package me.misa198.airmedy.player.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

/**
 * T050: pins the [GainProcessor] ramps before T052 implements `setTargetGainDb`. Gain is measured
 * from a constant 0.25 input as `output / 0.25`; the ramp is never faster than a 100 ms linear
 * ramp (see [epsilon]).
 *
 * Against the T050 stub (no-op setter, unity gain) the tests that expect a gain change fail on
 * assertions; [defaultIsBitExactPassThrough] and [twoProcessorsWithTheSameSetterCallsStayIdentical]
 * pass (guards for the implementation).
 */
class GainRampTest {

    private val blockFrames = 480

    // ---------------------------------------------------------------------------------------------
    // Rule 1: default is bit-exact pass-through
    // ---------------------------------------------------------------------------------------------

    @Test
    fun defaultIsBitExactPassThrough() {
        val rate = 48000
        val channels = 2
        val random = Random(2024L)
        val floatSamples = FloatArray(blockFrames * channels) { (random.nextDouble() * 2.0 - 1.0).toFloat() }
        val shortSamples = ShortArray(blockFrames * channels) { (random.nextInt(65536) - 32768).toShort() }

        val floatProcessor = GainProcessor()
        val floatFormat = floatProcessor.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT))
        assertEquals(C.ENCODING_PCM_FLOAT, floatFormat.encoding)
        assertTrue("processor must be active after configure", floatProcessor.isActive)
        floatProcessor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        floatProcessor.queueInput(floatInput(floatSamples))
        assertArrayEquals(
            "neutral float input must pass through bit-identically",
            floatSamples,
            drainFloat(floatProcessor.getOutput()),
            0f,
        )

        val shortProcessor = GainProcessor()
        shortProcessor.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        shortProcessor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        shortProcessor.queueInput(shortInput(shortSamples))
        val shortOut = drainFloat(shortProcessor.getOutput())
        for (i in shortSamples.indices) {
            assertEquals("16-bit sample $i must equal s / 32768f", shortSamples[i] / 32768f, shortOut[i], 0f)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 2: +6 dB ramp reaches the target inside a 100..300 ms window
    // ---------------------------------------------------------------------------------------------

    @Test
    fun targetRampReachesSixDbWithinTheRampWindow() {
        val rate = 48000
        val target = 10.0.pow(6.0 / 20.0).toFloat()
        val processor = GainProcessor()
        val out = run(
            processor, rate, 2, C.ENCODING_PCM_FLOAT, rate,
            sample = { _, _ -> 0.25f },
            onBlock = { block -> if (block == 0) processor.setTargetGainDb(6f) },
        )

        val gains = FloatArray(rate) { f -> out[f * 2] / 0.25f }
        val first = gains.indexOfFirst { abs(it - target) <= 1e-4f }
        assertTrue("ramp never reached $target (first frame within 1e-4 was $first)", first >= 0)
        assertTrue("ramp faster than 100 ms: first frame within 1e-4 at $first (<4800)", first >= 4800)
        assertTrue("ramp slower than 300 ms: first frame within 1e-4 at $first (>14400)", first <= 14400)
        assertTrue("settled gain ${gains.last()} must equal $target", abs(gains.last() - target) <= 1e-5f)
    }

    @Test
    fun targetRampReachesSixDbForSixteenBitInput() {
        val rate = 48000
        val target = 10.0.pow(6.0 / 20.0).toFloat()
        val processor = GainProcessor()
        val out = run(
            processor, rate, 2, C.ENCODING_PCM_16BIT, rate,
            sample = { _, _ -> 0.25f },
            onBlock = { block -> if (block == 0) processor.setTargetGainDb(6f) },
        )

        val settled = out.last() / 0.25f
        assertTrue("settled 16-bit gain $settled must equal $target", abs(settled - target) <= 1e-5f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 3: a retarget starts from the gain actually applied
    // ---------------------------------------------------------------------------------------------

    @Test
    fun rampStartsFromTheGainActuallyApplied() {
        val rate = 48000
        val retargetFrame = 4800
        val processor = GainProcessor()
        var retargeted = false
        val out = run(
            processor, rate, 1, C.ENCODING_PCM_FLOAT, rate,
            sample = { _, _ -> 0.25f },
            onBlock = { block ->
                if (block == 0) processor.setTargetGainDb(12f)
                if (!retargeted && block * blockFrames >= retargetFrame) {
                    retargeted = true
                    processor.setTargetGainDb(0f)
                }
            },
        )

        val gainBefore = out[retargetFrame - 1] / 0.25f
        assertTrue("+12 dB ramp did not start: gain before retarget was $gainBefore", gainBefore > 1.2f)

        val epsilon = epsilon(rate, floatArrayOf(0f, 12f, 0f))
        val step = abs(out[retargetFrame] - out[retargetFrame - 1]) / 0.25f
        assertTrue("discontinuity at retarget: gain step $step exceeds epsilon $epsilon", step <= epsilon)

        val gainOneBufferLater = out[retargetFrame + blockFrames] / 0.25f
        assertTrue(
            "retarget must take effect at the next buffer: gain $gainOneBufferLater one buffer later is not below $gainBefore",
            gainOneBufferLater < gainBefore,
        )

        val finalGain = out.last() / 0.25f
        assertTrue("gain must move back toward 1.0, was $finalGain", abs(finalGain - 1f) <= 1e-4f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 4: slider drag
    // ---------------------------------------------------------------------------------------------

    @Test
    fun sliderDragHasNoStepsAndSettlesOnTheLastTarget() {
        val rate = 48000
        val dragBlock = 256
        val targetsDb = floatArrayOf(-6f, 3f, -1f, 9f)
        val retargets = 40
        val total = dragBlock * retargets + rate / 2
        val processor = GainProcessor()
        val out = run(
            processor, rate, 1, C.ENCODING_PCM_FLOAT, total, blockFrames = dragBlock,
            sample = { _, _ -> 0.25f },
            onBlock = { block ->
                if (block < retargets) processor.setTargetGainDb(targetsDb[block % targetsDb.size])
            },
        )

        val sequence = ArrayList<Float>(retargets + 1)
        sequence.add(0f)
        for (i in 0 until retargets) sequence.add(targetsDb[i % targetsDb.size])
        val epsilon = epsilon(rate, sequence.toFloatArray())

        var maxStep = 0f
        for (i in 1 until total) {
            val step = abs(out[i] - out[i - 1]) / 0.25f
            if (step > maxStep) maxStep = step
        }
        assertTrue("per-sample gain step $maxStep exceeds epsilon $epsilon", maxStep <= epsilon)

        val finalTargetDb = targetsDb[(retargets - 1) % targetsDb.size]
        val expected = 10.0.pow(finalTargetDb / 20.0).toFloat()
        val finalGain = out.last() / 0.25f
        assertTrue(
            "did not settle on $finalTargetDb dB: gain $finalGain, expected $expected",
            abs(finalGain - expected) <= 1e-4f,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 6: two processors with the same setter calls stay identical
    // ---------------------------------------------------------------------------------------------

    @Test
    fun twoProcessorsWithTheSameSetterCallsStayIdentical() {
        val rate = 48000
        val total = rate
        fun schedule(processor: GainProcessor): (Int) -> Unit = { block ->
            when (block) {
                0 -> processor.setTargetGainDb(6f)
                20 -> processor.setTargetGainDb(-3f)
                40 -> processor.setTargetGainDb(0f)
            }
        }
        val a = GainProcessor()
        val b = GainProcessor()
        val outA = run(a, rate, 2, C.ENCODING_PCM_FLOAT, total, sample = { _, _ -> 0.25f }, onBlock = schedule(a))
        val outB = run(b, rate, 2, C.ENCODING_PCM_FLOAT, total, sample = { _, _ -> 0.25f }, onBlock = schedule(b))

        assertArrayEquals("processors with identical setter calls must be bit-identical", outA, outB, 0f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 7: a setter from another thread is applied at the next buffer
    // ---------------------------------------------------------------------------------------------

    @Test
    fun setterFromAnotherThreadIsAppliedAtTheNextBuffer() {
        val rate = 48000
        val processor = GainProcessor()
        configure(processor, rate, 1, C.ENCODING_PCM_FLOAT)
        val thread = Thread { processor.setTargetGainDb(6f) }
        thread.start()
        thread.join()

        val out = runSingleBlock(processor, FloatArray(blockFrames) { 0.25f })
        for (f in blockFrames - 100 until blockFrames) {
            assertTrue("gain from another thread not in effect at frame $f", abs(out[f] - 0.25f) > 1e-3f)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 8: non-finite targets are ignored
    // ---------------------------------------------------------------------------------------------

    @Test
    fun nonFiniteTargetsAreIgnored() {
        val rate = 48000
        val processor = GainProcessor()
        val out = run(
            processor, rate, 1, C.ENCODING_PCM_FLOAT, rate,
            sample = { _, _ -> 0.25f },
            onBlock = { block ->
                when (block) {
                    0 -> processor.setTargetGainDb(6f)
                    40 -> processor.setTargetGainDb(Float.NaN)
                    45 -> processor.setTargetGainDb(Float.POSITIVE_INFINITY)
                    50 -> processor.setTargetGainDb(Float.NEGATIVE_INFINITY)
                }
            },
        )

        val expected = 10.0.pow(6.0 / 20.0).toFloat()
        val finalGain = out.last() / 0.25f
        assertTrue("non-finite target changed the gain: $finalGain, expected $expected", abs(finalGain - expected) <= 1e-5f)
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 9: flush jumps straight to the current target
    // ---------------------------------------------------------------------------------------------

    @Test
    fun flushJumpsStraightToTheCurrentTarget() {
        val rate = 48000
        val processor = GainProcessor()
        configure(processor, rate, 1, C.ENCODING_PCM_FLOAT)
        processor.setTargetGainDb(6f)
        feed(processor, 4800, 1) { _, _ -> 0.25f }
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        val out = feed(processor, blockFrames, 1) { _, _ -> 0.25f }
        val target = 10.0.pow(6.0 / 20.0).toFloat()
        assertEquals("first sample after flush must already be at the target", 0.25f * target, out[0], 1e-5f)
        for (f in 0 until blockFrames) {
            assertTrue("frame $f after flush is not at the target: ${out[f]}", abs(out[f] - 0.25f * target) <= 1e-5f)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Never faster than a 100 ms linear ramp: `max |delta linear| / (0.1 * rate) + 1e-6`. */
    private fun epsilon(rate: Int, targetsDb: FloatArray): Float {
        val linear = DoubleArray(targetsDb.size) { 10.0.pow(targetsDb[it] / 20.0) }
        var maxDelta = 0.0
        for (i in 1 until linear.size) maxDelta = maxOf(maxDelta, abs(linear[i] - linear[i - 1]))
        return (maxDelta / (0.1 * rate)).toFloat() + 1e-6f
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
        blockFrames: Int = this.blockFrames,
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
            processor.queueInput(inputFor(samples, encoding))
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

    private fun inputFor(samples: FloatArray, encoding: Int): ByteBuffer =
        if (encoding == C.ENCODING_PCM_FLOAT) {
            floatInput(samples)
        } else {
            shortInput(ShortArray(samples.size) { (samples[it] * 32768f).toInt().toShort() })
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
