// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.codecs.codec.dsd

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Byte-table low-pass decimation, then an output-decimated FIR (polyphase schedule).
 * MSB-first DSD bits map to +/-1: a constant +1 stream produces PCM +1 (0 dBFS),
 * as in Flick. Tables/kernel are shared; all channel history and output are retained.
 */
class DsdDecimationPipeline(val dsdRate: Int, val targetPcmRate: Int, val channels: Int) {
    private val intermediate = maxOf(352800, targetPcmRate * 2)
    private val byteDecimation = dsdRate / intermediate / 8
    private val kernel = kernels.computeIfAbsent(dsdRate to intermediate) { makeKernel(it.first, it.second) }
    private val byteTaps = kernel.size / 256
    private val finalCoefficients = finals.computeIfAbsent(intermediate to targetPcmRate) {
        dsdFinalCoefficients(it.first, it.second)
    }
    private val byteHistory = Array(channels) { ByteArray(byteTaps * 2) }
    private val byteHead = IntArray(channels)
    private val validBytes = IntArray(channels)
    private val bytePhase = IntArray(channels)
    private val halfBands = Array(channels) { FirDecimator(halfBandCoefficients, 2, true) }
    private val finalStages = Array(channels) { FirDecimator(finalCoefficients, intermediate / 2 / targetPcmRate) }

    init {
        require(channels > 0 && byteDecimation > 0 && dsdRate % (intermediate * 8) == 0)
        require(intermediate % targetPcmRate == 0)
    }

    fun processBytes(bytes: ByteArray, offsets: IntArray, bytesPerChannel: Int, output: FloatArray): Int {
        var frames = 0
        for (ch in 0 until channels) {
            val raw = byteHistory[ch]
            val half = halfBands[ch]
            val final = finalStages[ch]
            var bh = byteHead[ch]
            var valid = validBytes[ch]
            var bp = bytePhase[ch]
            var written = 0
            for (i in 0 until bytesPerChannel) {
                bh = (bh - 1) and (byteTaps - 1)
                val value = bytes[offsets[ch] + i]
                raw[bh] = value
                raw[bh + byteTaps] = value
                valid = minOf(valid + 1, byteTaps)
                if (++bp != byteDecimation) continue
                bp = 0
                var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0
                var tap = 0
                while (tap + 3 < valid) {
                    s0 += kernel[tap * 256 + (raw[bh + tap].toInt() and 255)]
                    s1 += kernel[(tap + 1) * 256 + (raw[bh + tap + 1].toInt() and 255)]
                    s2 += kernel[(tap + 2) * 256 + (raw[bh + tap + 2].toInt() and 255)]
                    s3 += kernel[(tap + 3) * 256 + (raw[bh + tap + 3].toInt() and 255)]
                    tap += 4
                }
                while (tap < valid) { s0 += kernel[tap * 256 + (raw[bh + tap].toInt() and 255)]; tap++ }
                val sample = (s0 + s1) + (s2 + s3)
                if (!half.push(sample) || !final.push(half.output)) continue
                output[written * channels + ch] = final.output.toFloat()
                written++
            }
            byteHead[ch] = bh
            validBytes[ch] = valid
            bytePhase[ch] = bp
            frames = written
        }
        return frames
    }

    fun reset() {
        for (s in byteHistory) s.fill(0)
        for (s in halfBands) s.reset()
        for (s in finalStages) s.reset()
        byteHead.fill(0); validBytes.fill(0); bytePhase.fill(0)
    }

    companion object {
        private val halfBandCoefficients = lowPass(65, 0.25)
        private val kernels = ConcurrentHashMap<Pair<Int, Int>, FloatArray>()
        private val finals = ConcurrentHashMap<Pair<Int, Int>, DoubleArray>()
        private fun makeKernel(rate: Int, intermediate: Int): FloatArray {
            val coefficients = dsdByteCoefficients(rate, intermediate)
            return FloatArray(coefficients.size / 8 * 256) { index ->
                val tap = index / 256
                val byte = index and 255
                var sum = 0.0
                // Within the newest byte, bit zero is the newest DSD sample.
                for (bit in 0..7) sum += coefficients[tap * 8 + bit] * if (byte and (1 shl bit) != 0) 1.0 else -1.0
                sum.toFloat()
            }
        }
    }
}

internal fun dsdByteCoefficients(rate: Int, intermediate: Int) =
    lowPass(128 * (rate / intermediate / 8), intermediate / 2.0 / rate)

internal fun dsdFinalCoefficients(intermediate: Int, target: Int): DoubleArray {
    val rate = intermediate / 2
    val taps = if (target == 44100) 2048 else 192 * (intermediate / 352800)
    return lowPass(taps, (if (target == 44100) 21025.0 else 25000.0) / rate)
}

/** Symmetric FIR: evaluate only the output phase; no boxing or temporary arrays. */
private class FirDecimator(private val coefficients: DoubleArray, private val decimation: Int, private val halfBand: Boolean = false) {
    private val size = Integer.highestOneBit(coefficients.size - 1) * 2
    private val history = DoubleArray(size * 2)
    private var head = 0
    private var phase = 0
    var output = 0.0
        private set

    fun push(value: Double): Boolean {
        head = (head - 1) and (size - 1)
        history[head] = value
        history[head + size] = value
        if (++phase != decimation) return false
        phase = 0
        val last = head + coefficients.size - 1
        var sum = 0.0
        if (halfBand) {
            // A 65-tap half-band has zero even taps except its center.
            var tap = 1
            while (tap < 32) { sum += coefficients[tap] * (history[head + tap] + history[last - tap]); tap += 2 }
            sum += coefficients[32] * history[head + 32]
        } else {
            var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0
            var tap = 0
            while (tap < coefficients.size / 2) {
                s0 += coefficients[tap] * (history[head + tap] + history[last - tap])
                s1 += coefficients[tap + 1] * (history[head + tap + 1] + history[last - tap - 1])
                s2 += coefficients[tap + 2] * (history[head + tap + 2] + history[last - tap - 2])
                s3 += coefficients[tap + 3] * (history[head + tap + 3] + history[last - tap - 3])
                tap += 4
            }
            sum = (s0 + s1) + (s2 + s3)
        }
        output = sum
        return true
    }
    fun reset() { history.fill(0.0); head = 0; phase = 0; output = 0.0 }
}

/** Unity-DC, symmetric Kaiser FIR; beta 14 gives margin over the 100 dB stopband. */
internal fun lowPass(taps: Int, cutoff: Double): DoubleArray {
    val center = (taps - 1) / 2.0
    val denominator = besselI0(14.0)
    val h = DoubleArray(taps) { i ->
        val x = i - center
        val sinc = if (abs(x) < 1e-10) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
        val w = x / center
        sinc * besselI0(14.0 * sqrt(maxOf(0.0, 1 - w * w))) / denominator
    }
    val sum = h.sum()
    for (i in h.indices) h[i] /= sum
    return h
}

private fun besselI0(x: Double): Double {
    var sum = 1.0
    var term = 1.0
    for (k in 1..40) {
        val v = x / (2 * k)
        term *= v * v
        sum += term
        if (term < 1e-15) break
    }
    return sum
}
