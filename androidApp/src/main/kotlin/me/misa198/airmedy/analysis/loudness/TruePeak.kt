// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow dsp/loudness/truepeak.go, fork github.com/AMWolfstein/WaxFlow at 446ca3124d890fd08caecdcbf8931a493d4ebd04,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).

package me.misa198.airmedy.analysis.loudness

import kotlin.math.abs
import kotlin.math.sqrt

/** Tap count of each fractional interpolation phase (48 taps total at 4x). */
internal const val TP_TAPS = 12

/**
 * Polyphase windowed-sinc interpolation per BS.1770-4 Annex 2: factor 4 below
 * 96 kHz, factor 2 up to 192 kHz, null above (the sample grid is dense
 * enough). Phase 0 is the input sample itself, tracked as the sample peak, so
 * only the fractional phases are filtered.
 */
internal class TruePeak private constructor(
    val phases: Array<DoubleArray>,
    val hist: Array<DoubleArray>,
    val pos: IntArray,
) {
    /**
     * Feeds one sample of channel [c] and returns the largest magnitude among
     * the sample itself and the fractional-phase values computable with it.
     */
    fun push(c: Int, x: Double): Double {
        val buf = hist[c]
        var p = pos[c]
        buf[p] = x
        buf[p + TP_TAPS] = x
        if (++p == TP_TAPS) p = 0
        pos[c] = p
        var peak = abs(x)
        for (ph in phases) {
            var acc = 0.0
            for (t in 0 until TP_TAPS) {
                acc += ph[t] * buf[p + t]
            }
            val a = abs(acc)
            if (a > peak) peak = a
        }
        return peak
    }

    /** Flushes by pushing a full window of silence per channel. */
    fun drain(): Double {
        var peak = 0.0
        for (c in hist.indices) {
            for (i in 0 until TP_TAPS) {
                val p = push(c, 0.0)
                if (p > peak) peak = p
            }
        }
        return peak
    }

    companion object {
        /** Builds the interpolator, or returns null above 192 kHz. */
        fun newTruePeak(rate: Int, channels: Int): TruePeak? {
            var factor = 4
            when {
                rate > 192000 -> return null
                rate >= 96000 -> factor = 2
            }
            val phases = Array(factor - 1) { DoubleArray(TP_TAPS) }
            val hist = Array(channels) { DoubleArray(2 * TP_TAPS) }
            val pos = IntArray(channels)

            val beta = 6.0
            val half = TP_TAPS / 2
            val i0 = besselI0(beta)
            for (p in 0 until factor - 1) {
                val frac = (p + 1).toDouble() / factor.toDouble()
                val ph = phases[p]
                var sum = 0.0
                for (t in 0 until TP_TAPS) {
                    val x = frac - (t - half + 1).toDouble()
                    val u = x / half.toDouble()
                    val w = besselI0(beta * sqrt(1.0 - u * u)) / i0
                    ph[t] = sinc(x) * w
                    sum += ph[t]
                }
                for (t in ph.indices) ph[t] /= sum
            }
            return TruePeak(phases, hist, pos)
        }
    }
}
