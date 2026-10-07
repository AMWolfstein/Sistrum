// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow dsp/loudness/kweight.go, fork github.com/AMWolfstein/WaxFlow at 446ca3124d890fd08caecdcbf8931a493d4ebd04,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).

package me.misa198.airmedy.analysis.loudness

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.tan

// The K-weighting pre-filter of BS.1770-4: a high-frequency shelf modelling
// the head's acoustic effect, then a high pass (the revised low-frequency B
// curve). The standard publishes coefficients only at 48 kHz; the meter
// derives them for any rate by bilinear transform from the analog parameters
// behind that table.
internal val SHELF_HZ = 1681.9744509742096
internal val SHELF_GAIN = 3.999843853973347 // dB
internal val SHELF_Q = 0.7071752369554193
internal val VB_EXP = 0.4996667741545416

internal val HIGHPASS_HZ = 38.13547087613982
internal val HIGHPASS_Q = 0.5003270373238773

/** One second-order section in normalized direct form (a0 = 1). */
internal class Biquad(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
)

/** One channel's filter memory, direct form II transposed per stage. */
internal class KState {
    var s1a = 0.0
    var s1b = 0.0
    var s2a = 0.0
    var s2b = 0.0
}

/**
 * Derives the two K-weighting stages for a sample rate. At 48000 Hz the
 * result reproduces the published BS.1770-4 table to about 1e-6.
 */
internal fun kWeighting(rate: Int): Pair<Biquad, Biquad> {
    val fs = rate.toDouble()

    var k = tan(PI * SHELF_HZ / fs)
    val vh = 10.0.pow(SHELF_GAIN / 20.0)
    val vb = vh.pow(VB_EXP)
    val a0 = 1.0 + k / SHELF_Q + k * k
    val shelf = Biquad(
        b0 = (vh + vb * k / SHELF_Q + k * k) / a0,
        b1 = 2.0 * (k * k - vh) / a0,
        b2 = (vh - vb * k / SHELF_Q + k * k) / a0,
        a1 = 2.0 * (k * k - 1.0) / a0,
        a2 = (1.0 - k / SHELF_Q + k * k) / a0,
    )

    k = tan(PI * HIGHPASS_HZ / fs)
    val a0hp = 1.0 + k / HIGHPASS_Q + k * k
    val highpass = Biquad(
        b0 = 1.0,
        b1 = -2.0,
        b2 = 1.0,
        a1 = 2.0 * (k * k - 1.0) / a0hp,
        a2 = (1.0 - k / HIGHPASS_Q + k * k) / a0hp,
    )
    return shelf to highpass
}
