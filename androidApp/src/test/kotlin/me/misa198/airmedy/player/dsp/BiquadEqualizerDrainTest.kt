package me.misa198.airmedy.player.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * T048d round 3: proves that an EQ band dragged back to 0 dB always finishes draining and returns
 * to a bit-exact passthrough while playback continues.
 *
 * Drives [BiquadEqualizer] directly (48 kHz, stereo, 1024-frame buffers) with
 * `L = R = 0.45*sin(2*pi*60t) + 0.04*sin(2*pi*1000t)`. A band is "draining" after it has blended
 * to unity (`b == a`); from then on it must run an input-free state recurrence so its state decays
 * (with the live input, float rounding keeps low bands alive forever) and deactivate either below
 * [DRAIN_EPSILON] or after the 1 s backstop.
 */
class BiquadEqualizerDrainTest {

    private val rate = 48000
    private val block = 1024

    @Test
    fun lowBandReturnsToBitExactPassthroughWhilePlaying() {
        for (band in intArrayOf(0, 1, 2)) {
            val eq = BiquadEqualizer(2, rate)
            eq.setGains(FloatArray(10).also { it[band] = 12f })

            var frame = 0
            while (frame < rate) {
                frame = process(eq, frame, minOf(block, rate - frame))
            }

            eq.setGains(FloatArray(10))
            val drainStart = frame
            var deactivatedAt = -1
            val deadline = drainStart + (1.2 * rate).toInt()
            while (frame < deadline && deactivatedAt < 0) {
                val frames = minOf(block, deadline - frame)
                frame = process(eq, frame, frames)
                if (!eq.isActive) deactivatedAt = frame
            }

            assertTrue("band $band must deactivate within 1.2 s", deactivatedAt >= 0)
            // Well before the 1 s drain cap: proves the state actually decays rather than being cut off by the cap.
            assertTrue(
                "band $band must drain by decay, well before the 1 s cap (took ${deactivatedAt - drainStart} frames)",
                deactivatedAt - drainStart < (0.6 * rate).toInt(),
            )
            println(
                "lowBandReturnsToBitExactPassthroughWhilePlaying: band $band drain = " +
                    "${(deactivatedAt - drainStart).toDouble() / rate * 1000} ms",
            )

            val out = FloatArray(rate / 2 * 2)
            fillSignal(out, frame)
            val reference = out.copyOf()
            eq.process(out, 0, out.size / 2)
            assertArrayEquals("band $band output must be bit-exact after draining", reference, out, 0f)
        }
    }

    @Test
    fun drainingBandSurvivesOtherBandDrags() {
        val eq = BiquadEqualizer(2, rate)
        eq.setGains(FloatArray(10).also { it[1] = 12f })

        var frame = 0
        while (frame < rate) {
            frame = process(eq, frame, minOf(block, rate - frame))
        }

        eq.setGains(FloatArray(10))
        val drainStart = frame
        var band1DeactivatedAt = -1
        val dragEnd = frame + rate
        val steps = intArrayOf(6, 0, -6, 0)
        var step = 0
        while (frame < dragEnd) {
            val gains = FloatArray(10)
            gains[5] = steps[step % steps.size].toFloat()
            eq.setGains(gains)
            frame = process(eq, frame, minOf(block, dragEnd - frame))
            if (band1DeactivatedAt < 0 && !eq.isBandActiveForTest(1)) band1DeactivatedAt = frame
            step++
        }

        assertTrue("band 1 must deactivate while band 5 is dragged", band1DeactivatedAt >= 0)
        println(
            "drainingBandSurvivesOtherBandDrags: band 1 deactivated at " +
                "${(band1DeactivatedAt - drainStart).toDouble() / rate * 1000} ms",
        )

        eq.setGains(FloatArray(10))
        val deadline = frame + (1.2 * rate).toInt()
        while (frame < deadline && eq.isActive) {
            frame = process(eq, frame, minOf(block, deadline - frame))
        }
        assertFalse("all bands must drain to inactive", eq.isActive)

        val out = FloatArray(rate / 2 * 2)
        fillSignal(out, frame)
        val reference = out.copyOf()
        eq.process(out, 0, out.size / 2)
        assertArrayEquals("output must be bit-exact once drained", reference, out, 0f)
    }

    @Test
    fun noClickAtDrainEnd() {
        val eq = BiquadEqualizer(2, rate)
        eq.setGains(FloatArray(10).also { it[0] = 12f })

        var frame = 0
        while (frame < rate) {
            frame = process(eq, frame, minOf(block, rate - frame))
        }

        eq.setGains(FloatArray(10))
        val drainStart = frame
        val totalFrames = (1.2 * rate).toInt()
        val out = FloatArray(totalFrames * 2)
        var local = 0
        var deactivatedAt = -1
        while (local < totalFrames) {
            val frames = minOf(block, totalFrames - local)
            val buffer = FloatArray(frames * 2)
            fillSignal(buffer, drainStart + local)
            eq.process(buffer, 0, frames)
            System.arraycopy(buffer, 0, out, local * 2, frames * 2)
            if (deactivatedAt < 0 && !eq.isActive) deactivatedAt = local + frames
            local += frames
        }
        assertTrue("band 0 must deactivate", deactivatedAt >= 0)

        val from = maxOf(2, deactivatedAt - 2400)
        val until = minOf(totalFrames, deactivatedAt + 2400)
        val drainMetric = secondDifference(out, from, until)

        val flat = FloatArray(totalFrames * 2)
        fillSignal(flat, drainStart)
        val flatMetric = secondDifference(flat, from, until)
        val threshold = 1.5f * flatMetric + 1e-5f

        println(
            "noClickAtDrainEnd: deactivatedAt=$deactivatedAt drainMetric=$drainMetric " +
                "flatMetric=$flatMetric threshold=$threshold",
        )
        assertTrue(
            "drain end must not click: $drainMetric exceeds threshold $threshold",
            drainMetric <= threshold,
        )
    }

    private fun process(eq: BiquadEqualizer, startFrame: Int, frames: Int): Int {
        val buffer = FloatArray(frames * 2)
        fillSignal(buffer, startFrame)
        eq.process(buffer, 0, frames)
        return startFrame + frames
    }

    private fun fillSignal(buffer: FloatArray, startFrame: Int) {
        for (f in 0 until buffer.size / 2) {
            val t = (startFrame + f).toDouble() / rate
            val value = (0.45 * sin(2.0 * PI * 60.0 * t) + 0.04 * sin(2.0 * PI * 1000.0 * t)).toFloat()
            buffer[2 * f] = value
            buffer[2 * f + 1] = value
        }
    }

    private fun secondDifference(samples: FloatArray, fromFrame: Int, untilFrame: Int): Float {
        var maximum = 0f
        for (f in fromFrame until untilFrame) {
            val base = f * 2
            for (c in 0 until 2) {
                val d2 = samples[base + c] - 2f * samples[base - 2 + c] + samples[base - 4 + c]
                val magnitude = abs(d2)
                if (magnitude > maximum) maximum = magnitude
            }
        }
        return maximum
    }
}
