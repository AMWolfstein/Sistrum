package me.misa198.airmedy.analysis.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * True-peak tests over [LoudnessMeter]. They pin the interpolated peak paths adapted from WaxFlow's
 * `peak_test.go` (the full meter shares the peak meter's interpolator).
 */
class TruePeakTest {
    @Test
    fun truePeakOf997HzSineReadsItsAmplitude() {
        val rate = 44100
        val amp = 0.5
        val meter = measure(rate, 1, 4096, sine(rate, 2 * rate, 997.0, amp))
        val truePeak = meter.truePeakDbtp()
        assertNotNull("true peak is null", truePeak)
        assertEquals("997 Hz true peak", 20.0 * log10(amp), truePeak!!, 0.1)
    }

    @Test
    fun fsOver4PhasePiOver4ReadsTrueCrest() {
        val rate = 48000
        val amp = 0.5
        val meter = measure(rate, 1, 4096, sine(rate, rate, rate / 4.0, amp, PI / 4.0))
        val samplePeak = meter.samplePeakDbfs()
        val truePeak = meter.truePeakDbtp()
        assertNotNull("sample peak is null", samplePeak)
        assertNotNull("true peak is null", truePeak)
        assertEquals("sample peak of fs/4 pi/4 sine", 20.0 * log10(amp / sqrt(2.0)), samplePeak!!, 0.05)
        assertEquals("true peak of fs/4 pi/4 sine", 20.0 * log10(amp), truePeak!!, 0.2)
    }

    @Test
    fun flushCatchesTruePeakTail() {
        val rate = 48000
        val frames = 48
        val signal = FloatArray(frames)
        for (i in 44 until frames) {
            signal[i] = (0.8485 * sin(PI / 2.0 * i + PI / 4.0)).toFloat()
        }
        val meter = LoudnessMeter(rate, 1)
        meter.process(signal, 0, frames)

        val beforeDb = meter.truePeakDbtp()
        assertNotNull("pre-flush true peak is null", beforeDb)
        val before = 10.0.pow(beforeDb!! / 20.0)
        assertTrue("pre-flush true peak $before must stay below 0.7; the tail leaked early", before < 0.7)

        meter.flush()
        val afterDb = meter.truePeakDbtp()
        assertNotNull("post-flush true peak is null", afterDb)
        val after = 10.0.pow(afterDb!! / 20.0)
        assertTrue("post-flush true peak $after must reach at least 0.8", after >= 0.8)
    }

    @Test
    fun meterResumesAfterFlush() {
        val rate = 48000
        val quiet = FloatArray(480) { (0.3 * sin(PI / 2.0 * it + PI / 4.0)).toFloat() }
        val loud = FloatArray(480) { (0.9 * sin(PI / 2.0 * it + PI / 4.0)).toFloat() }

        val meter = LoudnessMeter(rate, 1)
        meter.process(quiet, 0, quiet.size)
        meter.flush()
        val firstDb = meter.truePeakDbtp()
        assertNotNull("first segment true peak is null", firstDb)
        val first = 10.0.pow(firstDb!! / 20.0)

        meter.process(loud, 0, loud.size)
        meter.flush()
        val secondDb = meter.truePeakDbtp()
        assertNotNull("second segment true peak is null", secondDb)
        val second = 10.0.pow(secondDb!! / 20.0)
        assertTrue("resumed true peak $second must exceed the first segment's $first", second > first)
        assertTrue("resumed true peak $second must reach 0.88", second >= 0.88)
    }

    @Test
    fun twoTimesOversamplingUpTo192k() {
        for (rate in intArrayOf(96000, 192000)) {
            val amp = 1.2
            val meter = measure(rate, 1, 4096, sine(rate, rate, rate / 4.0, amp, PI / 4.0))
            val truePeak = meter.truePeakDbtp()
            assertNotNull("rate $rate true peak is null", truePeak)
            assertEquals("rate $rate fs/4 crest within 0.5 dB", 20.0 * log10(amp), truePeak!!, 0.5)
        }
    }

    @Test
    fun above192kUsesSampleGridExactly() {
        val rate = 200000
        val amp = 1.2
        val signal = sine(rate, 2000, rate / 4.0, amp, PI / 4.0)
        var maxAbs = 0.0
        for (s in signal) {
            val a = abs(s.toDouble())
            if (a > maxAbs) maxAbs = a
        }
        val meter = measure(rate, 1, 2000, signal)
        val samplePeak = meter.samplePeakDbfs()
        val truePeak = meter.truePeakDbtp()
        assertNotNull("sample peak is null", samplePeak)
        assertNotNull("true peak is null", truePeak)
        assertEquals("above 192 kHz the true peak must equal the sample peak", samplePeak!!, truePeak!!, 0.0)
        assertEquals("sample peak value", 20.0 * log10(maxAbs), samplePeak, 1e-12)
    }
}
