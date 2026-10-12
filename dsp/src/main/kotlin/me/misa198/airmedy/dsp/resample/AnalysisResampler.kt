// SPDX-License-Identifier: GPL-3.0-or-later
// Port of FFmpeg 8.1.2 libswresample/resample.c and resample_template.c (LGPL-2.1-or-later).
// Copyright (c) 2004-2012 Michael Niedermayer. Kaiser polyphase bank and initial reflection.
package me.misa198.airmedy.dsp.resample
import java.io.Serializable
import kotlin.math.*

/** Streaming libswresample default Kaiser resampling. No EOF drain, as in Airmedy. */
class AnalysisResampler(private val inputRate: Int, private val outputRate: Int,
                         private val channels: Int, private val floatPrecision: Boolean = false): Serializable {
    private val bypass = inputRate == outputRate
    private val factor = min(outputRate * .97 / inputRate, 1.0)
    private val length = ((ceil(32 / factor).toInt() + 1) / 2) * 2
    private val center = (length - 1) / 2
    private val phases = min(1024, outputRate / gcd(inputRate,outputRate))
    private val bank = DoubleArray((phases + 1) * length)
    private val ringSize = Integer.highestOneBit(length * 4 + 8 - 1) shl 1
    private val ringMask = ringSize - 1
    private val ring = DoubleArray(ringSize * channels)
    private var received = 0L
    private var outputIndex = 0L
    val output = DoubleArray(channels)
    init {
        val tab = DoubleArray(length)
        var norm = 0.0
        for (ph in 0 until phases) {
            for (i in 0 until length) {
                val distance = (i - center).toDouble() - ph.toDouble() / phases
                val x = PI * distance * factor
                val w = 2 * distance / length
                val value = (if (x == 0.0) 1.0 else sin(x)/x) * bessel(9 * sqrt(max(1-w*w,0.0)))
                tab[i] = value
                if (ph == 0) norm += value
            }
            for (i in 0 until length) bank[ph*length+i] = precision(tab[i]/norm)
        }
        bank[phases*length] = bank[length-1]
        for (i in 1 until length) bank[phases*length+i] = bank[i-1]
    }
    fun put(input: FloatArray, offset: Int) {
        var ch = 0
        while (ch < channels) { ring[(received.toInt() and ringMask)*channels+ch] = input[offset+ch].toDouble(); ch++ }
        received++
    }
    fun putMono(value: Float) { ring[(received.toInt() and ringMask)] = value.toDouble(); received++ }
    /** Returns one ready output frame in [output]. False when more input is needed. */
    fun next(): Boolean {
        if (bypass) {
            if (outputIndex >= received) return false
            var ch = 0
            while (ch < channels) { output[ch] = ring[(outputIndex.toInt() and ringMask)*channels+ch];ch++ }
            outputIndex++
            return true
        }
        if (received < length + 1) return false
        val numerator = outputIndex * inputRate
        val base = numerator / outputRate
        val rem = numerator % outputRate
        val position = rem * phases
        val phase = (position / outputRate).toInt()
        val frac = (position % outputRate).toDouble() / outputRate
        val start = base - center
        if (start + length > received) return false
        var ch = 0
        while (ch < channels) {
            var even = 0.0; var odd = 0.0; var next = 0.0; var i = 0
            while (i < length) {
                val at = abs(start + i)
                val value = ring[(at.toInt() and ringMask)*channels+ch]
                if (frac == 0.0) {
                    if (i and 1 == 0) even = precision(even + value * bank[phase*length+i])
                    else odd = precision(odd + value * bank[phase*length+i])
                } else {
                    even = precision(even + value * bank[phase*length+i])
                    next = precision(next + value * bank[(phase+1)*length+i])
                }
                i++
            }
            output[ch] = precision(if (frac == 0.0) even + odd else even + (next-even)*frac)
            ch++
        }
        outputIndex++
        return true
    }
    private fun precision(x: Double) = if (floatPrecision) x.toFloat().toDouble() else x
    companion object {
        private fun gcd(a: Int,b: Int): Int { var x=a; var y=b; while(y!=0) {val t=x%y;x=y;y=t};return x }
        private fun bessel(x: Double): Double {
            var sum = 1.0; var term = 1.0; var k = 1
            while (k < 100) { term *= x*x/(4.0*k*k);sum += term;if(term < sum*1e-17) break;k++ }
            return sum
        }
    }
}
