package me.misa198.airmedy.analysis.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Album/group histogram tests. The histogram is Sistrum's own (not ported): it bins the gated
 * 400 ms block powers WaxFlow's Meter exposes and must track the meter's own integrated value,
 * and must reproduce WaxFlow's Group gating (the union of members' blocks), not a mean of members.
 */
class LoudnessHistogramTest {
    private fun histogramOf(powers: DoubleArray): LoudnessHistogram {
        val histogram = LoudnessHistogram()
        for (power in powers) histogram.addBlockPower(power)
        return histogram
    }

    private fun steady(rate: Int, seconds: Int, freq: Double, amp: Double): FloatArray {
        val tone = sine(rate, seconds * rate, freq, amp)
        return interleave(tone, tone)
    }

    private fun techCase5(rate: Int): FloatArray {
        val segments = listOf(-26.0 to 20.0, -20.0 to 20.1, -26.0 to 20.0)
        val channels = segments.map { (dbfs, seconds) ->
            sine(rate, (seconds * rate).toInt(), 1000.0, 10.0.pow(dbfs / 20.0))
        }
        return interleave(concat(channels), concat(channels))
    }

    private fun decaying(rate: Int, seconds: Int): FloatArray {
        val n = seconds * rate
        val left = FloatArray(n)
        val right = FloatArray(n)
        val w = 2.0 * PI * 440.0 / rate
        var phase = 0.0
        for (i in 0 until n) {
            val envelope = 1.0 - i.toDouble() / n
            val value = (0.9 * envelope * sin(phase)).toFloat()
            left[i] = value
            right[i] = (value * 0.8f).toFloat()
            phase += w
        }
        return interleave(left, right)
    }

    /** Fixed-seed noise whose gain steps by 20 dB every two seconds. */
    private fun dynamicNoise(rate: Int, seconds: Int, seed: Long): FloatArray {
        val n = seconds * rate
        val random = Random(seed)
        val step = rate * 2
        val left = FloatArray(n)
        val right = FloatArray(n)
        for (i in 0 until n) {
            val gain = if ((i / step) % 2 == 0) 0.1 else 1.0
            left[i] = ((random.nextDouble() * 2.0 - 1.0) * gain * 0.8).toFloat()
            right[i] = ((random.nextDouble() * 2.0 - 1.0) * gain * 0.8).toFloat()
        }
        return interleave(left, right)
    }

    /** Slow-amplitude chord, stereo, for a "music-like" long programme. */
    private fun musicLike(rate: Int, seconds: Int): FloatArray {
        val n = seconds * rate
        val left = FloatArray(n)
        val right = FloatArray(n)
        val w1 = 2.0 * PI * 220.0 / rate
        val w2 = 2.0 * PI * 277.2 / rate
        val w3 = 2.0 * PI * 329.6 / rate
        val w4 = 2.0 * PI * 440.0 / rate
        var p1 = 0.0
        var p2 = 0.0
        var p3 = 0.0
        var p4 = 0.0
        for (i in 0 until n) {
            val envelope = 0.55 + 0.35 * sin(2.0 * PI * 0.37 * i / rate)
            left[i] = (envelope * (0.25 * sin(p1) + 0.18 * sin(p2) + 0.12 * sin(p3))).toFloat()
            right[i] = (envelope * (0.22 * sin(p2) + 0.16 * sin(p3) + 0.14 * sin(p4))).toFloat()
            p1 += w1
            p2 += w2
            p3 += w3
            p4 += w4
        }
        return interleave(left, right)
    }

    /** Fixed-seed noise under a slow envelope swinging about 30 dB, so blocks land in many bins. */
    private fun sweepingNoise(rate: Int, seconds: Int, seed: Long): FloatArray {
        val n = seconds * rate
        val random = Random(seed)
        val left = FloatArray(n)
        val right = FloatArray(n)
        val period = 120.0 * rate
        for (i in 0 until n) {
            val envelopeDb = -15.0 + 15.0 * sin(2.0 * PI * i / period)
            val gain = 10.0.pow(envelopeDb / 20.0)
            left[i] = ((random.nextDouble() * 2.0 - 1.0) * gain).toFloat()
            right[i] = ((random.nextDouble() * 2.0 - 1.0) * gain).toFloat()
        }
        return interleave(left, right)
    }

    @Test
    fun histogramTracksIntegratedForSixProgrammes() {
        val programmes: List<Triple<String, Int, () -> FloatArray>> = listOf(
            Triple("steady tone", 48000, { steady(48000, 30, 997.0, 10.0.pow(-12.37 / 20.0)) }),
            Triple("Tech 3341 case 5", 48000, { techCase5(48000) }),
            Triple("decaying envelope", 48000, { decaying(48000, 20) }),
            Triple("20 dB dynamic noise", 48000, { dynamicNoise(48000, 20, 4242L) }),
            Triple("music-like 44.1 kHz", 44100, { musicLike(44100, 180) }),
            Triple("music-like 48 kHz", 48000, { musicLike(48000, 180) }),
        )

        for ((name, rate, build) in programmes) {
            val meter = measure(rate, 2, 4096, build())
            val blockPowers = meter.blockPowers()
            assertTrue("$name: meter produced no gated blocks", blockPowers.isNotEmpty())

            val measured = meter.integratedLufs()
            val fromHistogram = histogramOf(blockPowers).integratedLufs()
            assertNotNull("$name: meter integrated is null", measured)
            assertNotNull("$name: histogram integrated is null", fromHistogram)
            println("histogram tracking: $name meter=${"%.4f".format(measured!!)} histogram=${"%.4f".format(fromHistogram!!)}")
            assertEquals("$name: histogram must track the meter", measured!!, fromHistogram!!, 0.05)
        }
    }

    @Test
    fun mergedAlbumHistogramsMatchGroupGatingNotTheMean() {
        val rate = 48000
        val members = listOf(
            -14.37 to 60,
            -29.81 to 20,
            -20.23 to 40,
            -26.58 to 30,
            -17.12 to 50,
        )
        val meters = members.map { (dbfs, seconds) ->
            val tone = sine(rate, seconds * rate, 997.0, 10.0.pow(dbfs / 20.0))
            measure(rate, 2, 4096, interleave(tone, tone))
        }
        val individual = meters.map { it.integratedLufs() }
        individual.forEachIndexed { i, value -> assertNotNull("member $i integrated is null", value) }

        val album = LoudnessHistogram()
        for (meter in meters) album.merge(meter.histogram())
        val albumValue = album.integratedLufs()
        assertNotNull("album integrated is null", albumValue)

        val expected = LoudnessMeter.integratedOfBlockPowers(concatPowers(meters.map { it.blockPowers() }))
        assertNotNull("group-gated expectation is null", expected)

        val mean = individual.map { it!! }.average()
        println(
            "album: members=${individual.joinToString { "%.3f".format(it) }} " +
                "mean=%.3f group=%.3f album=%.3f".format(mean, expected!!, albumValue!!),
        )
        assertEquals("merged album histograms must match WaxFlow group gating", expected!!, albumValue!!, 0.05)
        assertTrue(
            "album $albumValue must not be the mean $mean of the member values",
            abs(albumValue!! - mean) > 0.3,
        )
    }

    @Test
    fun meterHistogramEqualsHistogramBuiltFromBlockPowers() {
        val rate = 48000
        val n = 30 * rate
        val meter = measure(rate, 2, 4096, interleave(sine(rate, n, 997.0, 0.3), sine(rate, n, 211.0, 0.2, 0.4)))
        val powers = meter.blockPowers()
        assertTrue("meter produced no gated blocks", powers.isNotEmpty())

        val built = histogramOf(powers)
        val fromMeter = meter.histogram()
        assertEquals("meter histogram blockCount", powers.size.toLong(), fromMeter.blockCount)
        assertEquals("built histogram blockCount", powers.size.toLong(), built.blockCount)

        val builtIntegrated = built.integratedLufs()
        val meterIntegrated = fromMeter.integratedLufs()
        assertNotNull("built histogram integrated is null", builtIntegrated)
        assertNotNull("meter histogram integrated is null", meterIntegrated)
        assertEquals("built vs meter histogram integrated", builtIntegrated!!, meterIntegrated!!, 1e-12)
        assertEquals("built vs meter histogram encoding", built.encode(), fromMeter.encode())
    }

    @Test
    fun histogramEncodeDecodeRoundTripsAndStaysSmall() {
        val rate = 48000
        val meter = measure(rate, 2, 4096, sweepingNoise(rate, 240, 90210L))
        val histogram = meter.histogram()
        val histogramIntegrated = histogram.integratedLufs()
        assertNotNull("4-minute programme histogram integrated is null", histogramIntegrated)
        assertTrue("4-minute programme must produce blocks", histogram.blockCount > 0)

        val text = histogram.encode()
        assertTrue("encoded histogram ${text.length} chars must be <= 2048", text.length <= 2048)

        val decoded = LoudnessHistogram.decode(text)
        assertEquals("encode/decode/encode must round-trip every bin", text, decoded.encode())
        assertEquals("decoded blockCount", histogram.blockCount, decoded.blockCount)
        val decodedIntegrated = decoded.integratedLufs()
        assertNotNull("decoded integrated is null", decodedIntegrated)
        assertEquals("decoded integrated", histogramIntegrated!!, decodedIntegrated!!, 0.0)

        // No bin accessor exists, so the multi-bin property is checked through the sparse encoding:
        // a histogram over one repeated power encodes as a single entry, the sweeping programme as many.
        val singleBin = LoudnessHistogram()
        val repeatedPower = meter.blockPowers().first()
        repeat(meter.blockPowers().size) { singleBin.addBlockPower(repeatedPower) }
        val singleBinText = singleBin.encode()
        println(
            "histogram encode: programme=${text.length} chars over ${histogram.blockCount} blocks, " +
                "single-bin=${singleBinText.length} chars",
        )
        assertTrue(
            "a programme spread over many bins must encode longer than a single-bin histogram " +
                "(programme ${text.length} chars, single-bin ${singleBinText.length} chars)",
            text.length > singleBinText.length,
        )
    }

    @Test
    fun emptyHistogramAndBlocksAtTheAbsoluteGateAreExcluded() {
        val empty = LoudnessHistogram()
        assertNull("empty histogram integrated", empty.integratedLufs())
        assertEquals("empty histogram blockCount", 0L, empty.blockCount)

        val absoluteGatePower = 10.0.pow((-70.0 + 0.691) / 10.0)
        val histogram = LoudnessHistogram()
        histogram.addBlockPower(absoluteGatePower)
        assertEquals("a block at exactly -70 LUFS must not count", 0L, histogram.blockCount)
        assertNull("a histogram of only gated blocks is empty", histogram.integratedLufs())

        histogram.addBlockPower(10.0.pow((-23.0 + 0.691) / 10.0))
        assertEquals("a block above the gate must count", 1L, histogram.blockCount)
        val integrated = histogram.integratedLufs()
        assertNotNull("histogram with one valid block integrated is null", integrated)
        assertEquals("one -23 LUFS block", -23.0, integrated!!, 0.1)
    }
}
