package me.misa198.airmedy.analysis.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Shared helpers for the loudness test set (T050a). All helpers are top-level
// `internal` so TruePeakTest / LoudnessHistogramTest in this package can use
// them without a second helper file.
// ---------------------------------------------------------------------------

/** amp*sin(2*pi*freq*i/rate + phase), one channel, `frames` samples. */
internal fun sine(rate: Int, frames: Int, freq: Double, amp: Double, phase: Double = 0.0): FloatArray {
    val out = FloatArray(frames)
    val w = 2.0 * PI * freq / rate
    for (i in 0 until frames) {
        out[i] = (amp * sin(w * i + phase)).toFloat()
    }
    return out
}

internal fun silence(frames: Int): FloatArray = FloatArray(frames)

/** Interleaves equal-length channels into one frame-major FloatArray. */
internal fun interleave(vararg channels: FloatArray): FloatArray {
    require(channels.isNotEmpty()) { "interleave needs at least one channel" }
    val frames = channels[0].size
    for (c in channels.indices) {
        require(channels[c].size == frames) { "channel $c has ${channels[c].size} frames, want $frames" }
    }
    val out = FloatArray(frames * channels.size)
    for (f in 0 until frames) {
        val base = f * channels.size
        for (c in channels.indices) {
            out[base + c] = channels[c][f]
        }
    }
    return out
}

/** Concatenates mono channel segments in order. */
internal fun concat(parts: List<FloatArray>): FloatArray {
    val out = FloatArray(parts.sumOf { it.size })
    var at = 0
    for (part in parts) {
        part.copyInto(out, at)
        at += part.size
    }
    return out
}

/** Concatenates power arrays in order (album/group oracle semantics). */
internal fun concatPowers(parts: List<DoubleArray>): DoubleArray {
    val out = DoubleArray(parts.sumOf { it.size })
    var at = 0
    for (part in parts) {
        part.copyInto(out, at)
        at += part.size
    }
    return out
}

/**
 * Feeds [signal] (interleaved, [channels] channels) to a fresh [LoudnessMeter] in [chunk]-frame
 * process calls, then flushes. The last chunk may be shorter than [chunk].
 */
internal fun measure(rate: Int, channels: Int, chunk: Int, signal: FloatArray): LoudnessMeter {
    require(chunk >= 1) { "chunk must be at least 1 frame" }
    val meter = LoudnessMeter(rate, channels)
    val frames = signal.size / channels
    var offset = 0
    while (offset < frames) {
        val take = minOf(chunk, frames - offset)
        meter.process(signal, offset, take)
        offset += take
    }
    meter.flush()
    return meter
}

/** Asserts every result the meter exposes is either absent or finite (never ±Inf/NaN). */
internal fun assertNoNonFinite(meter: LoudnessMeter) {
    meter.integratedLufs()?.let { assertTrue("integrated must be finite, was $it", it.isFinite()) }
    meter.truePeakDbtp()?.let { assertTrue("true peak must be finite, was $it", it.isFinite()) }
    meter.samplePeakDbfs()?.let { assertTrue("sample peak must be finite, was $it", it.isFinite()) }
    for (p in meter.blockPowers()) {
        assertTrue("block power must be finite, was $p", p.isFinite())
    }
    meter.histogram().integratedLufs()?.let { assertTrue("histogram integrated must be finite, was $it", it.isFinite()) }
}

class LoudnessMeterTest {
    @Test
    fun integratedAnchorsMatchBs1770AtAllRates() {
        val amp18 = 10.0.pow(-18.0 / 20.0)
        val rates = intArrayOf(44100, 48000, 96000)
        val single = DoubleArray(rates.size)
        val dual = DoubleArray(rates.size)
        rates.forEachIndexed { i, rate ->
            val secs = if (rate == 48000) 20 else 10
            val n = secs * rate

            val singleMeter = measure(rate, 2, 4096, interleave(sine(rate, n, 997.0, 1.0), silence(n)))
            val singleValue = singleMeter.integratedLufs()
            assertNotNull("rate $rate single-channel integrated is null", singleValue)
            single[i] = singleValue!!
            assertEquals("rate $rate single-channel 0 dBFS 997 Hz", -3.0103, single[i], 0.05)

            val dualMeter = measure(
                rate,
                2,
                4096,
                interleave(sine(rate, n, 997.0, amp18), sine(rate, n, 997.0, amp18)),
            )
            val dualValue = dualMeter.integratedLufs()
            assertNotNull("rate $rate dual-channel integrated is null", dualValue)
            dual[i] = dualValue!!
            assertEquals("rate $rate dual-channel -18 dBFS 997 Hz", -18.0, dual[i], 0.05)
        }
        for (i in 1 until rates.size) {
            assertEquals("single anchor ${rates[i]} vs ${rates[0]}", single[0], single[i], 0.05)
            assertEquals("dual anchor ${rates[i]} vs ${rates[0]}", dual[0], dual[i], 0.05)
        }
    }

    @Test
    fun monoReadsOneChannelNotDualMono() {
        val rate = 48000
        val amp = 10.0.pow(-23.0 / 20.0)
        val meter = measure(rate, 1, 4096, sine(rate, 20 * rate, 997.0, amp))
        val integrated = meter.integratedLufs()
        assertNotNull("mono integrated is null", integrated)
        assertEquals("mono -23 dBFS 997 Hz must read one channel, not dual mono", -26.01, integrated!!, 0.05)
    }

    @Test
    fun duplicatingIntoSecondChannelAdds3Lu() {
        val rate = 48000
        val n = 10 * rate
        val s = sine(rate, n, 997.0, 1.0)
        val one = measure(rate, 2, 4096, interleave(s, silence(n))).integratedLufs()
        val two = measure(rate, 2, 4096, interleave(s, s)).integratedLufs()
        assertNotNull("single-channel integrated is null", one)
        assertNotNull("dual-channel integrated is null", two)
        assertEquals("duplicating a signal into R must add 3.0103 LU", 3.0103, two!! - one!!, 0.02)
    }

    private data class Tech3341Segment(val dbfs: Double, val seconds: Double)

    private fun tech3341(segments: List<Tech3341Segment>): Double? {
        val rate = 48000
        val left = segments.map { sine(rate, (it.seconds * rate).toInt(), 1000.0, 10.0.pow(it.dbfs / 20.0)) }
        val right = segments.map { sine(rate, (it.seconds * rate).toInt(), 1000.0, 10.0.pow(it.dbfs / 20.0)) }
        return measure(rate, 2, 4096, interleave(concat(left), concat(right))).integratedLufs()
    }

    @Test
    fun ebuTech3341Vectors() {
        val cases = listOf(
            Triple("case 1", -23.0, listOf(Tech3341Segment(-23.0, 20.0))),
            Triple("case 2", -33.0, listOf(Tech3341Segment(-33.0, 20.0))),
            Triple(
                "case 3",
                -23.0,
                listOf(
                    Tech3341Segment(-36.0, 10.0),
                    Tech3341Segment(-23.0, 60.0),
                    Tech3341Segment(-36.0, 10.0),
                ),
            ),
            Triple(
                "case 4",
                -23.0,
                listOf(
                    Tech3341Segment(-72.0, 10.0),
                    Tech3341Segment(-36.0, 10.0),
                    Tech3341Segment(-23.0, 60.0),
                    Tech3341Segment(-36.0, 10.0),
                    Tech3341Segment(-72.0, 10.0),
                ),
            ),
            Triple(
                "case 5",
                -23.0,
                listOf(
                    Tech3341Segment(-26.0, 20.0),
                    Tech3341Segment(-20.0, 20.1),
                    Tech3341Segment(-26.0, 20.0),
                ),
            ),
        )
        for ((name, expected, segments) in cases) {
            val integrated = tech3341(segments)
            assertNotNull("Tech 3341 $name integrated is null", integrated)
            assertEquals("Tech 3341 $name", expected, integrated!!, 0.1)
        }
    }

    @Test
    fun silenceGapDoesNotMoveIntegrated() {
        val rate = 48000
        val amp = 10.0.pow(-23.0 / 20.0)
        fun tone(secs: Int) = sine(rate, secs * rate, 997.0, amp)

        val solid = measure(rate, 2, 4096, interleave(tone(18), tone(18))).integratedLufs()

        val gappedLeft = concat(listOf(tone(8), silence(2 * rate), tone(10)))
        val gappedRight = concat(listOf(tone(8), silence(2 * rate), tone(10)))
        val gapped = measure(rate, 2, 4096, interleave(gappedLeft, gappedRight)).integratedLufs()

        assertNotNull("solid integrated is null", solid)
        assertNotNull("gapped integrated is null", gapped)
        assertEquals("a 2 s silence gap must not move integrated by more than 0.1 LU", 0.0, abs(solid!! - gapped!!), 0.1)
    }

    @Test
    fun chunkInvariance() {
        val rate = 48000
        val n = rate + rate / 5
        val signal = interleave(sine(rate, n, 997.0, 0.3), sine(rate, n, 211.0, 0.2, 0.7))
        val reference = measure(rate, 2, n, signal)
        val refIntegrated = reference.integratedLufs()
        val refPeak = reference.truePeakDbtp()
        assertNotNull("reference integrated is null", refIntegrated)
        assertNotNull("reference true peak is null", refPeak)

        for (chunk in intArrayOf(1, 17, 480, 4801)) {
            val meter = measure(rate, 2, chunk, signal)
            val integrated = meter.integratedLufs()
            val peak = meter.truePeakDbtp()
            assertNotNull("chunk $chunk integrated is null", integrated)
            assertNotNull("chunk $chunk true peak is null", peak)
            assertEquals("chunk $chunk integrated", refIntegrated!!, integrated!!, 1e-9)
            assertEquals("chunk $chunk true peak", refPeak!!, peak!!, 1e-9)
        }
    }

    @Test
    fun silenceAndShortInputsYieldNoMeasurementAndNeverNonFinite() {
        val silent = measure(48000, 2, 4096, interleave(silence(48000), silence(48000)))
        assertNull("silence integrated must be null", silent.integratedLufs())
        assertNull("silence true peak must be null", silent.truePeakDbtp())
        assertNull("silence sample peak must be null", silent.samplePeakDbfs())
        assertNoNonFinite(silent)

        val shortFrames = (48000 * 0.300).toInt()
        val short = measure(
            48000,
            2,
            4096,
            interleave(sine(48000, shortFrames, 997.0, 0.5), sine(48000, shortFrames, 997.0, 0.5)),
        )
        assertNull("a 300 ms tone has no 400 ms block, integrated must be null", short.integratedLufs())
        assertNoNonFinite(short)
    }

    @Test
    fun channelWeightsExcludeLfeAndRaiseSurround() {
        val rate = 48000
        val n = 5 * rate
        val tone = sine(rate, n, 997.0, 0.1)
        fun sixChannels(): Array<FloatArray> = Array(6) { silence(n) }

        val frontOnly = sixChannels().also { it[0] = tone }
        val withLfe = sixChannels().also {
            it[0] = tone
            it[3] = sine(rate, n, 60.0, 1.0)
        }
        val frontIntegrated = measure(rate, 6, 4096, interleave(*frontOnly)).integratedLufs()
        val lfeIntegrated = measure(rate, 6, 4096, interleave(*withLfe)).integratedLufs()
        assertNotNull("front-only 5.1 integrated is null", frontIntegrated)
        assertNotNull("with-LFE 5.1 integrated is null", lfeIntegrated)
        assertEquals("LFE content must not move the weighted reading", frontIntegrated!!, lfeIntegrated!!, 1e-9)

        val front = sixChannels().also {
            it[0] = tone
            it[1] = tone
        }
        val back = sixChannels().also {
            it[4] = tone
            it[5] = tone
        }
        val fi = measure(rate, 6, 4096, interleave(*front)).integratedLufs()
        val bi = measure(rate, 6, 4096, interleave(*back)).integratedLufs()
        assertNotNull("front-pair integrated is null", fi)
        assertNotNull("back-pair integrated is null", bi)
        assertEquals("back pair must read 10*log10(1.41) above front pair", 10.0 * log10(1.41), bi!! - fi!!, 0.02)
    }

    @Test
    fun kWeightingCoefficientsAt48kMatchBs1770() {
        val coefficientNames = arrayOf(
            "shelf b0", "shelf b1", "shelf b2", "shelf a1", "shelf a2",
            "high-pass b0", "high-pass b1", "high-pass b2", "high-pass a1", "high-pass a2",
        )
        val expected = doubleArrayOf(
            1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585,
            1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621,
        )
        val got = kWeightingCoefficients(48000)
        assertEquals("kWeightingCoefficients must return 10 values", 10, got.size)
        for (i in expected.indices) {
            assertEquals(coefficientNames[i], expected[i], got[i], 1e-6)
        }
    }

    @Test
    fun checkpointResumeMatchesUninterruptedMeasurement() {
        val rate = 48000
        val totalFrames = 60 * rate
        val stepFrame = 25 * rate
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            val gain = if (i < stepFrame) 1.0 else 0.5
            val sample = gain * (0.3 * sin(2.0 * PI * 997.0 * i / rate) + 0.1 * sin(2.0 * PI * 211.0 * i / rate))
            left[i] = sample.toFloat()
            right[i] = sample.toFloat()
        }
        val signal = interleave(left, right)

        val reference = measure(rate, 2, 4096, signal)
        val refIntegrated = reference.integratedLufs()
        val refPeak = reference.truePeakDbtp()
        assertNotNull("reference integrated is null", refIntegrated)
        assertNotNull("reference true peak is null", refPeak)

        val splitFrame = (31.37 * rate).toInt()
        val first = LoudnessMeter(rate, 2)
        first.process(signal, 0, splitFrame)
        val state = first.checkpoint()
        assertNotNull("checkpoint state is null", state)

        val resumed = LoudnessMeter.restore(state)
        resumed.process(signal, splitFrame, totalFrames - splitFrame)
        resumed.flush()
        val gotIntegrated = resumed.integratedLufs()
        val gotPeak = resumed.truePeakDbtp()
        assertNotNull("resumed integrated is null", gotIntegrated)
        assertNotNull("resumed true peak is null", gotPeak)
        assertEquals("checkpoint/resume integrated", refIntegrated!!, gotIntegrated!!, 0.02)
        assertEquals("checkpoint/resume true peak", refPeak!!, gotPeak!!, 0.1)
    }

    @Test
    fun invalidConstructionThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) { LoudnessMeter(0, 2) }
        assertThrows(IllegalArgumentException::class.java) { LoudnessMeter(-48000, 2) }
        assertThrows(IllegalArgumentException::class.java) { LoudnessMeter(48000, 0) }
        assertThrows(IllegalArgumentException::class.java) { LoudnessMeter(48000, 9) }
    }
}
