// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow dsp/internal/firwin/firwin.go, fork github.com/AMWolfstein/WaxFlow at 446ca3124d890fd08caecdcbf8931a493d4ebd04,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).

package me.misa198.airmedy.analysis.loudness

import kotlin.math.PI
import kotlin.math.sin

/** Normalized sinc, sin(pi x)/(pi x). */
internal fun sinc(x: Double): Double =
    if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

/**
 * Zeroth-order modified Bessel function of the first kind, by its power
 * series: the Kaiser window's kernel.
 */
internal fun besselI0(x: Double): Double {
    var sum = 1.0
    var term = 1.0
    val half = x / 2.0
    var k = 1
    while (true) {
        val kd = k.toDouble()
        term *= (half / kd) * (half / kd)
        sum += term
        if (term < sum * 1e-17) return sum
        k++
    }
}
