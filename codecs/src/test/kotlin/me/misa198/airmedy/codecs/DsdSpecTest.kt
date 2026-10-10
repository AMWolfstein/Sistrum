// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.codecs

import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import me.misa198.airmedy.codecs.codec.dsd.*
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsd.Dsd

/** Generated signals and independent frequency-domain checks; no new audio golden files. */
class DsdSpecTest {
    companion object {
        private val rates = intArrayOf(2822400, 5644800, 11289600)
        private val report = File("build/reports/dsd-spec.tsv")
        private val comparisonReport = File("build/reports/dsd-comparison.tsv")
        @JvmStatic @BeforeClass fun startReport() {
            report.parentFile.mkdirs()
            report.writeText("dsd_rate\tmetric\tvalue_db\n")
            comparisonReport.writeText("dsd_rate\ttone2_hz\tband_hz\tdelay_us\tgain\tpolarity\tdifference_db\tours_vs_flat_db\tflick_vs_flat_db\tflick_vs_own_filter_db\tflick_1k_gain_db\tflick_tone2_gain_db\n")
        }
        private fun measured(rate: Int, metric: String, value: Double) {
            report.appendText("$rate\t$metric\t$value\n")
            println("DSD spec: $rate $metric = $value dB")
        }
    }

    @Test fun compositeResponseMeetsPassbandAndStopband() {
        for (rate in rates) {
            val intermediate = 352800
            val finalRate = Dsd.bestTargetRate(rate)
            val first = dsdByteCoefficients(rate, intermediate)
            val half = lowPass(65, 0.25)
            val last = dsdFinalCoefficients(intermediate, finalRate)
            val n = 131072
            val a = spectrum(first, n)
            val b = spectrum(half, n)
            val c = spectrum(last, n)
            var ripple = 0.0
            var stop = 0.0
            for (i in 0..n / 2) {
                val frequency = i.toDouble() * rate / n
                val gain = a[i] * b[(i * (rate / intermediate)) % n] * c[(i * (rate / (intermediate / 2))) % n]
                if (frequency in 20.0..20000.0) ripple = max(ripple, abs(db(gain)))
                if (frequency >= finalRate / 2.0) stop = max(stop, gain)
            }
            // Also check exact band endpoints, not just FFT bins.
            for (f in doubleArrayOf(20.0, 20000.0)) {
                ripple = max(ripple, abs(db(response(first, f / rate) * response(half, f / intermediate) * response(last, f / (intermediate / 2)))))
            }
            measured(rate, "passband_max_abs", ripple)
            measured(rate, "stopband_max", db(stop))
            var tableError = 0.0
            for (tap in 0 until first.size / 8) {
                var rowError = 0.0
                for (byte in 0..255) {
                    var exact = 0.0
                    for (bit in 0..7) exact += first[tap * 8 + bit] * if (byte and (1 shl bit) != 0) 1.0 else -1.0
                    rowError = max(rowError, abs(exact - exact.toFloat().toDouble()))
                }
                tableError += rowError
            }
            // Tables are nonlinear after float rounding. Bound their worst error for
            // every possible byte sequence, then propagate through both FIR L1 norms.
            val realizedStopBound = stop + tableError * half.sumOf { abs(it) } * last.sumOf { abs(it) }
            measured(rate, "stopband_with_table_rounding_bound", db(realizedStopBound))
            assertTrue("$rate rounded-table stop=${db(realizedStopBound)}", db(realizedStopBound) <= -100)
            assertTrue("$rate ripple=$ripple", ripple <= 0.1)
            assertTrue("$rate stop=${db(stop)}", db(stop) <= -100)
        }
    }

    @Test fun generatedSinesMeasureGainDistortionAndUltrasonicNoise() {
        for (rate in rates) {
            val target = Dsd.bestTargetRate(rate)
            val amplitude = 10.0.pow(-6.0 / 20)
            val bytes = generate(rate, 0.25) { t -> amplitude * sin(2 * PI * 1000 * t) }
            val pcm = decode(rate, target, bytes).drop(target / 20).take(target / 10).map { it.toDouble() }.toDoubleArray()
            val fit = fitTone(pcm, target, 1000.0)
            val gain = db(fit.first / amplitude)
            val residual = fit.second
            val fullNoise = db(rms(residual) / (fit.first / sqrt(2.0)))
            val ultrasonic = bandRms(residual, target, 30000.0, target / 2.0)
            val audioNoise = bandRms(residual, target, 20.0, 20000.0)
            measured(rate, "1k_gain", gain)
            measured(rate, "1k_thdn_full_output_band", fullNoise)
            measured(rate, "1k_thdn_20Hz_20kHz", db(audioNoise / (fit.first / sqrt(2.0))))
            measured(rate, "ultrasonic_sine_dbfs", db(ultrasonic))
            assertTrue(abs(gain) < 0.1)
            assertTrue("$rate THD+N=$fullNoise", fullNoise < -100)
            assertTrue(db(ultrasonic) <= -60)
            for (hz in doubleArrayOf(20.0, 10000.0, 20000.0)) {
                val duration = if (hz == 20.0) 0.4 else 0.2
                val toneAmplitude = 0.25
                val signal = generate(rate, duration) { t -> toneAmplitude * sin(2 * PI * hz * t) }
                val decoded = decode(rate, target, signal)
                val length = if (hz == 20.0) target / 4 else target / 10
                val samples = DoubleArray(length) { decoded[target / 20 + it].toDouble() }
                val level = db(fitTone(samples, target, hz).first / toneAmplitude)
                measured(rate, "generated_${hz.toInt()}Hz_gain", level)
                assertTrue("$rate $hz gain=$level", abs(level) <= 0.1)
            }
        }
    }

    @Test fun generatedSilenceAndFullScalePreserveFlickLevel() {
        for (rate in rates) {
            val target = Dsd.bestTargetRate(rate)
            // All-one/all-zero bits are +/-1 DSD, not PCM silence.
            for (value in intArrayOf(0, 255)) {
                val bytes = ByteArray(rate / 8 / 10) { value.toByte() }
                val pcm = decode(rate, target, bytes)
                val reference = decode(rate, target, bytes, flick = true)
                val level = if (value == 255) 1f else -1f
                for (i in target / 20 until pcm.size) {
                    assertEquals(level, pcm[i], 1e-7f)
                    assertEquals(reference[i], pcm[i], 1e-7f)
                }
                measured(rate, if (value == 255) "positive_fullscale_gain" else "negative_fullscale_gain", db(abs(pcm.last().toDouble())))
            }
            val silence = generate(rate, 0.2) { 0.0 }
            val samples = decode(rate, target, silence).drop(target / 20).map { it.toDouble() }.toDoubleArray()
            measured(rate, "silence_rms_dbfs", db(rms(samples)))
            measured(rate, "ultrasonic_silence_dbfs", db(bandRms(samples, target, 30000.0, target / 2.0)))
            assertTrue(db(rms(samples)) < -100)
            assertTrue(db(bandRms(samples, target, 30000.0, target / 2.0)) < -60)
        }
    }

    @Test fun ultrasonicFullScaleDsdPatternsAreRejected() {
        for (rate in rates) {
            val target = Dsd.bestTargetRate(rate)
            for (hz in intArrayOf(35280, target / 2)) {
                // Exact periodic balanced +/-1 DSD square waves; all their input
                // harmonics are ultrasonic. No modulator noise can mask attenuation.
                val period = rate / hz
                val bytes = ByteArray(rate / 8 / 5)
                for (i in 0 until bytes.size * 8) if (i % period < period / 2) {
                    bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
                }
                val pcm = decode(rate, target, bytes).drop(target / 20).map { it.toDouble() }.toDoubleArray()
                val level = db(rms(pcm))
                measured(rate, "fullscale_${hz}Hz_square_output_dbfs", level)
                assertTrue("$rate $hz ultrasonic output=$level", level < if (hz == 35280) -60 else -100)
            }
        }
    }

    @Test fun generatedSweepHasExactChunkResetAndStereoContinuity() {
        for (rate in rates) {
            val target = Dsd.bestTargetRate(rate)
            val sweep = generate(rate, 0.12) { t -> 0.25 * sin(2 * PI * (20 * t + (30000 - 20) * t * t / (2 * 0.12))) }
            val whole = decode(rate, target, sweep)
            val tiny = decode(rate, target, sweep, 7)
            assertArrayEquals(whole, tiny, 0f)
            assertTrue(whole.all { it.isFinite() && abs(it) <= 0.51 })
            val decoder = DsdDecimationPipeline(rate, target, 2)
            val planar = sweep + sweep
            val stereo = FloatArray(whole.size * 2 + 2)
            val frames = decoder.processBytes(planar, intArrayOf(0, sweep.size), sweep.size, stereo)
            assertEquals(whole.size, frames)
            for (i in whole.indices) { assertEquals(whole[i], stereo[2 * i], 0f); assertEquals(whole[i], stereo[2 * i + 1], 0f) }
            decoder.reset()
            val again = FloatArray(stereo.size)
            assertEquals(frames, decoder.processBytes(planar, intArrayOf(0, sweep.size), sweep.size, again))
            assertArrayEquals(stereo, again, 0f)
        }
    }

    @Test fun productionCorpusSeeksShortReadsAndDefaults() {
        for (f in DsdCorpus.rows) {
            if (f.status != "ok") {
                assertEquals(f.status.removePrefix("refused: "), assertThrows(java.io.IOException::class.java) { f.open() }.message)
                continue
            }
            val s = f.open()
            assertEquals(Dsd.bestTargetRate(s.info.dsdSampleRate), s.info.sampleRate)
            assertTrue(s.info.sampleRate <= 176400)
            val all = samples(s)
            val dop = s.decodeDopBlock()!!
            val firstWords = ByteBuffer.allocate(8 * dop.channels * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 8 * dop.channels) firstWords.putInt(dop.samples[i])
            assertEquals(f.value("dop_first8_le32"), firstWords.array().hex())
            assertEquals(s.info.totalSamples * s.info.channels, all.size.toLong())
            for (position in longArrayOf(0, 1, 63, 2047, 4096, s.info.totalSamples / 2, s.info.totalSamples - 1).filter { it in 0 until s.info.totalSamples }) {
                s.seekSample(position)
                val expected = all.copyOfRange((position * s.info.channels).toInt(), all.size)
                // Equality to the uninterrupted tail means no extra discontinuity/click is introduced by replay.
                assertArrayEquals(expected, samples(s, position, true), 0f)
            }
            s.seekSample(s.info.totalSamples); assertNull(s.decodeBlock())
            assertThrows(java.io.IOException::class.java) { s.seekSample(-1) }
            assertThrows(java.io.IOException::class.java) { s.seekSample(s.info.totalSamples + 1) }
            val source = f.source.readBytes()
            val short = object : RandomAccessSource {
                override val length = source.size.toLong()
                override fun read(position: Long, buffer: ByteBuffer): Int {
                    if (position >= length) return -1
                    val n = minOf(7, buffer.remaining(), (length - position).toInt())
                    buffer.put(source, position.toInt(), n); return n
                }
            }
            val direct = ByteBuffer.allocateDirect(source.size + 5); direct.position(5); direct.put(source); direct.flip(); direct.position(5)
            for (stream in listOf(Dsd.open(short), Dsd.open(direct))) assertArrayEquals(all, samples(stream), 0f)
            assertEquals(5, direct.position())
            // Existing explicit targets are still admitted, including rates above the default cap.
            val explicit = if (s.info.dsdSampleRate == 2822400) 176400 else 352800
            assertEquals(explicit, Dsd.open(ByteBuffer.wrap(source), explicit).info.sampleRate)
        }
    }

    @Test fun flickComparisonBelow15kIsExplainedByAnalyticResponse() {
        for (rate in rates) {
            val target = Dsd.bestTargetRate(rate)
            val newGroupDelay = (dsdByteCoefficients(rate, 352800).size - 1) / 2.0 / rate +
                32.0 / 352800 + (dsdFinalCoefficients(352800, target).size - 1) / 2.0 / 176400
            val oldGroupDelay = (3 * (4 - 1) / 2.0 + 4 * (512 - 1) / 2.0) / rate
            // Output n is emitted at raw DSD sample (n+1)*decimation-1.
            val endpointOffset = 1.0 / target - 1.0 / rate
            for (upper in doubleArrayOf(10000.0, 19500.0)) {
                val band = if (upper == 10000.0) 15000.0 else 20000.0
                val tones = doubleArrayOf(1000.0, upper)
                val input = generate(rate, 0.2) { t -> 0.25 * tones.sumOf { sin(2 * PI * it * t) } }
                val start = target / 20
                val length = target / 10 // 100 ms: both tones lie on exact 10 Hz DFT bins.
                val new = decode(rate, target, input).drop(start).take(length).map { it.toDouble() }.toDoubleArray()
                val old = decode(rate, target, input, flick = true).drop(start).take(length).map { it.toDouble() }.toDoubleArray()
                val flat = DoubleArray(length) { i -> 0.25 * tones.sumOf { sin(2 * PI * it * (start + i) / target) } }
                val oldGains = tones.map { flickMagnitude(rate, it) }
                val oldPrediction = DoubleArray(length) { i ->
                    val t = (start + i).toDouble() / target + endpointOffset - oldGroupDelay
                    0.25 * tones.indices.sumOf { oldGains[it] * sin(2 * PI * tones[it] * t) }
                }
                val ns = coherentSpectrum(new)
                val rs = coherentSpectrum(old)
                val analytic = coherentSpectrum(flat)
                val predicted = coherentSpectrum(oldPrediction)
                val alignment = alignBand(ns, rs, target, band, tones, newGroupDelay - oldGroupDelay)
                val ours = alignBand(ns, analytic, target, band, tones, newGroupDelay - endpointOffset)
                val flick = alignBand(rs, analytic, target, band, tones, oldGroupDelay - endpointOffset)
                val oldFilterError = bandDifference(rs, predicted, target, band, 0.0, 1.0)
                val measuredGains = tones.map { fitTone(old, target, it).first / 0.25 }
                val line = listOf(rate, upper.toInt(), band.toInt(), alignment.delay * 1e6,
                    alignment.gain, alignment.polarity, alignment.differenceDb, ours.differenceDb,
                    flick.differenceDb, oldFilterError, db(measuredGains[0]), db(measuredGains[1])).joinToString("\t")
                comparisonReport.appendText(line + "\n")
                println("DSD comparison: $line")
                assertEquals("Flick polarity", 1, alignment.polarity)
                assertEquals("Measured fractional delay vs composite FIR/CIC delay", newGroupDelay - oldGroupDelay, alignment.delay, 2e-8)
                assertTrue("Flick must match its own analytic filter, not a flat target: $oldFilterError", oldFilterError < -90)
                // Check the expected attenuation independently of the alignment/gain fit.
                for (i in tones.indices) assertEquals("Flick analytic tone response", db(oldGains[i]), db(measuredGains[i]), 0.001)
                if (upper == 10000.0) {
                    measured(rate, "flick_0_15k_aligned_difference", alignment.differenceDb)
                    measured(rate, "ours_0_15k_vs_analytic", ours.differenceDb)
                    measured(rate, "flick_0_15k_vs_analytic", flick.differenceDb)
                    measured(rate, "flick_0_15k_vs_own_filter", oldFilterError)
                    assertTrue("Our 0-15 kHz output must reproduce the analytic generated signal: ${ours.differenceDb}", ours.differenceDb < -90)
                }
            }
        }
    }

    @Test fun coherentDftMatchesDirectTransform() {
        // Independently validate arbitrary-length Bluestein and the band/alignment math.
        val values = DoubleArray(15) { sin(it.toDouble()) + 0.3 * cos(2.7 * it) }
        val transformed = coherentSpectrum(values)
        for (k in values.indices) {
            val re = values.indices.sumOf { values[it] * cos(2 * PI * k * it / values.size) }
            val im = values.indices.sumOf { -values[it] * sin(2 * PI * k * it / values.size) }
            assertEquals(re, transformed.real[k], 1e-12)
            assertEquals(im, transformed.imaginary[k], 1e-12)
        }
        val rate = 1000
        val tones = doubleArrayOf(50.0, 120.0)
        val delay = 0.0023
        val gain = 1.2
        val reference = coherentSpectrum(DoubleArray(rate) { i -> tones.sumOf { sin(2 * PI * it * i / rate) } })
        val shifted = coherentSpectrum(DoubleArray(rate) { i -> -gain * tones.sumOf { sin(2 * PI * it * (i.toDouble() / rate - delay)) } })
        val found = alignBand(shifted, reference, rate, 150.0, tones, delay)
        assertEquals(-1, found.polarity)
        assertEquals(delay, found.delay, 1e-12)
        assertEquals(gain, found.gain, 1e-12)
        assertTrue(found.differenceDb < -200)
    }

    private fun samples(stream: Dsd, position: Long = 0, afterSeek: Boolean = false): FloatArray {
        val all = FloatArray((stream.info.totalSamples * stream.info.channels).toInt())
        var n = 0
        var expectedPosition = position
        var previous: Any? = null
        while (true) {
            val block = stream.decodeBlock() ?: break
            if (previous != null) assertSame(previous, block)
            else if (afterSeek) assertTrue(block.discontinuity)
            assertEquals(expectedPosition, block.position)
            expectedPosition += block.frames
            previous = block
            block.samples.copyInto(all, n, 0, block.frames * block.channels)
            n += block.frames * block.channels
        }
        return all.copyOf(n)
    }
}

/**
 * Original deterministic test modulator: ninth-order error-feedback quantization.
 * NTF is a Butterworth high-pass at 60.6816 kHz, normalized to leading
 * coefficient one (peak NTF gain 1.476 at DSD64, lower at higher rates). Factored sections avoid polynomial cancellation.
 * Signals are generated here, not downloaded or committed as audio fixtures.
 */
private fun generate(rate: Int, seconds: Double, input: (Double) -> Double): ByteArray {
    // Keep the NTF corner at 60.6816 kHz across DSD rates. This also reduces
    // peak NTF gain at higher rates and avoids overload on slow -6 dB tones.
    val corner = tan(PI * 60681.6 / rate)
    val a1 = DoubleArray(5)
    val a2 = DoubleArray(5)
    a1[0] = -(1 - corner) / (1 + corner)
    for (j in 1..4) {
        val angle = PI / 2 + PI * (2 * (4 - j) + 1) / 18
        val sr = corner * cos(angle)
        val si = -corner * sin(angle)
        val denominator = (1 - sr).pow(2) + si * si
        val real = (1 - sr * sr - si * si) / denominator
        val imaginary = 2 * si / denominator
        a1[j] = -2 * real
        a2[j] = real * real + imaginary * imaginary
    }
    val z1 = DoubleArray(5); val z2 = DoubleArray(5)
    val bytes = ByteArray((rate * seconds / 8).toInt())
    for (i in 0 until bytes.size * 8) {
        var feedback = 0.0
        for (j in 0..4) feedback += z1[j]
        val u = input(i.toDouble() / rate) + feedback
        val bit = if (u >= 0) 1.0 else -1.0
        var error = bit - u
        for (j in 0..4) {
            val value = error + z1[j]
            z1[j] = (if (j == 0) -1.0 else -2.0) * error - a1[j] * value + z2[j]
            z2[j] = (if (j == 0) 0.0 else 1.0) * error - a2[j] * value
            error = value
        }
        check(u.isFinite() && abs(u) < 20) { "Test modulator overload" }
        if (bit > 0) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
    }
    return bytes
}

private fun decode(rate: Int, target: Int, bytes: ByteArray, chunk: Int = 4096, flick: Boolean = false): FloatArray {
    val decoder = DsdDecimationPipeline(rate, target, 1)
    val reference = if (flick) FlickExactDecimationPipeline(rate, target, 1) else null
    val result = FloatArray(bytes.size * 8 / (rate / target) + 1)
    val block = FloatArray(chunk * 8 / (rate / target) + 1)
    val offsets = intArrayOf(0)
    var frames = 0
    var position = 0
    while (position < bytes.size) {
        offsets[0] = position
        val count = minOf(chunk, bytes.size - position)
        val n = reference?.processBytes(bytes, offsets, count, block) ?: decoder.processBytes(bytes, offsets, count, block)
        block.copyInto(result, frames, 0, n); frames += n; position += count
    }
    return result.copyOf(frames)
}

private fun db(value: Double) = 20 * log10(maxOf(value, 1e-300))
private fun rms(values: DoubleArray) = sqrt(values.sumOf { it * it } / values.size)
private fun toneCoefficients(values: DoubleArray, rate: Int, hz: Double): DoubleArray {
    var s = 0.0; var c = 0.0
    for (i in values.indices) { val w = 2 * PI * hz * i / rate; s += values[i] * sin(w); c += values[i] * cos(w) }
    s *= 2.0 / values.size; c *= 2.0 / values.size
    return doubleArrayOf(s, c)
}
private fun fitTone(values: DoubleArray, rate: Int, hz: Double): Pair<Double, DoubleArray> {
    val (s, c) = toneCoefficients(values, rate, hz)
    return hypot(s, c) to DoubleArray(values.size) { i -> values[i] - s * sin(2 * PI * hz * i / rate) - c * cos(2 * PI * hz * i / rate) }
}
private fun response(h: DoubleArray, frequency: Double): Double {
    var re = 0.0; var im = 0.0
    for (i in h.indices) { re += h[i] * cos(2 * PI * frequency * i); im -= h[i] * sin(2 * PI * frequency * i) }
    return hypot(re, im)
}
private fun spectrum(h: DoubleArray, n: Int): DoubleArray {
    val re = h.copyOf(n); val im = DoubleArray(n); fft(re, im)
    return DoubleArray(n) { hypot(re[it], im[it]) }
}
private fun bandRms(values: DoubleArray, rate: Int, low: Double, high: Double): Double {
    val n = Integer.highestOneBit(values.size - 1) * 2
    val re = DoubleArray(n); val im = DoubleArray(n)
    var windowEnergy = 0.0
    for (i in values.indices) {
        val w = 0.5 - 0.5 * cos(2 * PI * i / values.size)
        re[i] = values[i] * w; windowEnergy += w * w
    }
    fft(re, im)
    var energy = 0.0
    for (i in 1..n / 2) if (i.toDouble() * rate / n in low..high) energy += (if (i == n / 2) 1 else 2) * (re[i] * re[i] + im[i] * im[i])
    return sqrt(energy / n / windowEnergy)
}
private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size; var j = 0
    for (i in 1 until n) {
        var bit = n / 2
        while (j and bit != 0) { j = j xor bit; bit /= 2 }; j = j xor bit
        if (i < j) { val r = re[i]; re[i] = re[j]; re[j] = r; val q = im[i]; im[i] = im[j]; im[j] = q }
    }
    var length = 2
    while (length <= n) {
        val wr = cos(-2 * PI / length); val wi = sin(-2 * PI / length)
        for (base in 0 until n step length) {
            var r = 1.0; var q = 0.0
            for (k in 0 until length / 2) {
                val a = base + k; val b = a + length / 2
                val tr = r * re[b] - q * im[b]; val ti = r * im[b] + q * re[b]
                re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti
                val next = r * wr - q * wi; q = r * wi + q * wr; r = next
            }
        }
        length *= 2
    }
}


private fun flickMagnitude(rate: Int, hz: Double): Double {
    val f = PI * hz / rate
    val cic = (sin(4 * f) / (4 * sin(f))).pow(3)
    return cic * response(generateFlickSincFilter(512, 18000.0 / (rate / 4)), hz / (rate / 4))
}

private data class ComplexSpectrum(val real: DoubleArray, val imaginary: DoubleArray)
private data class BandAlignment(val delay: Double, val gain: Double, val polarity: Int, val differenceDb: Double)

/** Arbitrary-length DFT: a coherent 100 ms window avoids window leakage and resampling error. */
private fun coherentSpectrum(values: DoubleArray): ComplexSpectrum {
    val n = values.size
    val m = Integer.highestOneBit(2 * n - 2) * 2
    val ar = DoubleArray(m); val ai = DoubleArray(m)
    val br = DoubleArray(m); val bi = DoubleArray(m)
    for (j in 0 until n) {
        val angle = PI * ((j.toLong() * j) % (2L * n)) / n
        val c = cos(angle); val s = sin(angle)
        ar[j] = values[j] * c; ai[j] = -values[j] * s
        br[j] = c; bi[j] = s
        if (j > 0) { br[m - j] = c; bi[m - j] = s }
    }
    fft(ar, ai); fft(br, bi)
    for (j in 0 until m) {
        val re = ar[j] * br[j] - ai[j] * bi[j]
        val im = ar[j] * bi[j] + ai[j] * br[j]
        ar[j] = re; ai[j] = -im
    }
    fft(ar, ai) // inverse via conjugation
    val re = DoubleArray(n); val im = DoubleArray(n)
    for (j in 0 until n) {
        val angle = PI * ((j.toLong() * j) % (2L * n)) / n
        val c = cos(angle); val s = sin(angle)
        re[j] = (ar[j] * c - ai[j] * s) / m
        im[j] = (-ai[j] * c - ar[j] * s) / m
    }
    return ComplexSpectrum(re, im)
}

/** Fit a single fractional delay and signed scalar gain, then include EVERY bin in 0..band. */
private fun alignBand(signal: ComplexSpectrum, reference: ComplexSpectrum, rate: Int, band: Double,
    tones: DoubleArray, expectedDelay: Double): BandAlignment {
    val n = signal.real.size
    val bins = tones.map { (it * n / rate).roundToInt() }
    val a = DoubleArray(bins.size) { i -> val k = bins[i]; signal.real[k] * reference.real[k] + signal.imaginary[k] * reference.imaginary[k] }
    val b = DoubleArray(bins.size) { i -> val k = bins[i]; signal.imaginary[k] * reference.real[k] - signal.real[k] * reference.imaginary[k] }
    fun correlation(delay: Double): Double = tones.indices.sumOf { i ->
        val theta = 2 * PI * tones[i] * delay
        a[i] * cos(theta) - b[i] * sin(theta)
    }
    // The two-tone signal is periodic: choose the physical delay branch around
    // the filter prediction, while searching both polarities and all local peaks.
    val halfPeriod = 0.5 / tones.min()
    var delay = expectedDelay
    var best = -1.0
    for (step in 0..2000) {
        val candidate = expectedDelay - halfPeriod + step * halfPeriod / 1000
        val score = abs(correlation(candidate))
        if (score > best) { best = score; delay = candidate }
    }
    repeat(8) {
        var first = 0.0; var second = 0.0
        for (i in tones.indices) {
            val w = 2 * PI * tones[i]; val theta = w * delay
            first += -w * (a[i] * sin(theta) + b[i] * cos(theta))
            second += -w * w * (a[i] * cos(theta) - b[i] * sin(theta))
        }
        delay -= first / second
    }
    val last = floor(band * n / rate).toInt()
    var dot = 0.0; var referenceEnergy = 0.0
    for (k in 0..last) {
        val theta = 2 * PI * k * rate.toDouble() / n * delay
        val r = reference.real[k] * cos(theta) + reference.imaginary[k] * sin(theta)
        val i = reference.imaginary[k] * cos(theta) - reference.real[k] * sin(theta)
        val weight = if (k == 0 || k * 2 == n) 1.0 else 2.0
        dot += weight * (signal.real[k] * r + signal.imaginary[k] * i)
        referenceEnergy += weight * (r * r + i * i)
    }
    val signedGain = dot / referenceEnergy
    return BandAlignment(delay, abs(signedGain), if (signedGain >= 0) 1 else -1,
        bandDifference(signal, reference, rate, band, delay, signedGain))
}

private fun bandDifference(signal: ComplexSpectrum, reference: ComplexSpectrum, rate: Int, band: Double,
    delay: Double, gain: Double): Double {
    val n = signal.real.size
    var error = 0.0; var energy = 0.0
    for (k in 0..floor(band * n / rate).toInt()) {
        val theta = 2 * PI * k * rate.toDouble() / n * delay
        val r = gain * (reference.real[k] * cos(theta) + reference.imaginary[k] * sin(theta))
        val i = gain * (reference.imaginary[k] * cos(theta) - reference.real[k] * sin(theta))
        val weight = if (k == 0 || k * 2 == n) 1.0 else 2.0
        error += weight * ((signal.real[k] - r).pow(2) + (signal.imaginary[k] - i).pow(2))
        energy += weight * (signal.real[k] * signal.real[k] + signal.imaginary[k] * signal.imaginary[k])
    }
    return db(sqrt(error / energy))
}
