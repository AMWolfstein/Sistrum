package me.misa198.airmedy.player.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class DspLinearityTest {
    @Test
    fun eqOfSumEqualsSumOfEq() {
        val rate = 48000
        val frames = rate
        val block = 480
        val random = Random(20240607L)
        val a = randomStereo(frames, random, 0.25)
        val b = randomStereo(frames, random, 0.25)
        val gain = FloatArray(frames) { i -> (1.0 - 0.8 * (i.toDouble() / (frames - 1))).toFloat() }

        val ga = FloatArray(a.size) { i -> a[i] * gain[i / 2] }
        val gb = FloatArray(b.size) { i -> b[i] * gain[i / 2] }
        val reference = FloatArray(a.size) { i -> (a[i] + b[i]) * gain[i / 2] }
        val gab = reference.copyOf()

        val gainsA = floatArrayOf(12f, -12f, 6f, -6f, 3f, -3f, 0f, 9f, -9f, 1.5f)
        val gainsB = floatArrayOf(-6f, 6f, 0f, 3f, -3f, 12f, 0f, 0f, 6f, -12f)

        val eqA = BiquadEqualizer(2, rate).also { it.setGains(gainsA) }
        val eqB = BiquadEqualizer(2, rate).also { it.setGains(gainsA) }
        val eqAB = BiquadEqualizer(2, rate).also { it.setGains(gainsA) }

        var frame = 0
        var blockIndex = 0
        while (frame < frames) {
            if (blockIndex == 50) {
                eqA.setGains(gainsB)
                eqB.setGains(gainsB)
                eqAB.setGains(gainsB)
            }
            val count = minOf(block, frames - frame)
            val offset = frame * 2
            eqA.process(ga, offset, count)
            eqB.process(gb, offset, count)
            eqAB.process(gab, offset, count)
            frame += count
            blockIndex++
        }

        var maxError = 0f
        for (i in gab.indices) {
            val error = abs((ga[i] + gb[i]) - gab[i])
            if (error > maxError) maxError = error
        }
        val peakAB = peak(gab)
        // Float biquad rounding noise alone reaches ~2.5e-4 x peak here (C float simulation of the native filters).
        assertTrue("EQ must be linear: max |yA + yB - yAB| = $maxError, peak = $peakAB", maxError <= 1e-3f * peakAB)

        val changed = maxAbsDiff(gab, reference)
        val peakReference = peak(reference)
        assertTrue("EQ must alter the signal: diff = $changed, peak = $peakReference", changed > 0.05f * peakReference)
    }

    @Test
    fun widthAndPreampAreLinear() {
        val frames = 4096
        val random = Random(987654321L)
        val a = randomStereo(frames, random, 1.0)
        val b = randomStereo(frames, random, 1.0)
        val sum = FloatArray(a.size) { a[it] + b[it] }
        val gain = Preamp.linearGain(6f)

        val outA = a.copyOf().also { StereoWidth.apply(it, 0, frames, 1.7f); scaleInPlace(it, gain) }
        val outB = b.copyOf().also { StereoWidth.apply(it, 0, frames, 1.7f); scaleInPlace(it, gain) }
        val outAB = sum.copyOf().also { StereoWidth.apply(it, 0, frames, 1.7f); scaleInPlace(it, gain) }

        val peakReference = peak(sum)
        var maxError = 0f
        for (i in outAB.indices) {
            val error = abs(outA[i] + outB[i] - outAB[i])
            if (error > maxError) maxError = error
        }
        assertTrue("width+preamp must be linear: max error = $maxError, peak = $peakReference", maxError <= 1e-5f * peakReference)

        val changed = maxAbsDiff(outAB, sum)
        assertTrue("width+preamp must alter the signal: diff = $changed, peak = $peakReference", changed > 0.05f * peakReference)
    }
}

private fun scaleInPlace(buffer: FloatArray, gain: Float) {
    for (i in buffer.indices) {
        buffer[i] *= gain
    }
}
