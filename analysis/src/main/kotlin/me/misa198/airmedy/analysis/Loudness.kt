// SPDX-License-Identifier: GPL-3.0-or-later
// Kotlin port of FFmpeg 8.1.2 libavfilter/f_ebur128.c (LGPL-2.1-or-later).
// Copyright (c) 2012 Clément Bœsch; see THIRD-PARTY-NOTICES and docs/analysis/ORACLE.md.
package me.misa198.airmedy.analysis
import java.io.Serializable
import kotlin.math.*

internal class Loudness(private val channels: Int, private val rate: Int): Serializable {
    private val pre = DoubleArray(5)
    private val rlb = DoubleArray(2)
    private val y = DoubleArray(channels * 2)
    private val z = DoubleArray(channels * 2)
    private val weights = DoubleArray(channels) { ch ->
        // av_channel_layout_default: 1/2/3/4/5/6/7/8 channels, in native order.
        when {
            channels == 9 -> 1.0 // FFmpeg has no default mask for nine channels.
            channels == 3 && ch == 2 || channels >= 6 && ch == 3 -> 0.0
            channels == 4 && ch == 3 || channels == 5 && ch >= 3 || channels >= 6 && ch >= 4 -> 1.41
            else -> 1.0
        }
    }
    private val n400 = rate * 2 / 5
    private val n3000 = rate * 3
    private val c400 = DoubleArray(n400 * channels)
    private val c3000 = DoubleArray(n3000 * channels)
    private val s400 = DoubleArray(channels)
    private val s3000 = DoubleArray(channels)
    private var pos400 = 0
    private var pos3000 = 0
    private var total = 0L
    private var refresh = 0
    private val h400 = Histogram(-10)
    private val h3000 = Histogram(-20)
    var integrated = -70.0; private set
    var range = 0.0; private set
    init {
        var k = tan(PI * 1681.974450955533 / rate)
        val q = .7071752369554196
        val vh = 10.0.pow(3.999843853973347 / 20)
        val vb = vh.pow(.4996667741545416)
        val a0 = 1 + k / q + k * k
        pre[0] = (vh + vb * k / q + k * k) / a0
        pre[1] = 2 * (k * k - vh) / a0
        pre[2] = (vh - vb * k / q + k * k) / a0
        pre[3] = 2 * (k * k - 1) / a0
        pre[4] = (1 - k / q + k * k) / a0
        k = tan(PI * 38.13547087602444 / rate)
        val denominator = 1 + k / .5003270373238773 + k * k
        rlb[0] = 2 * (k * k - 1) / denominator
        rlb[1] = (1 - k / .5003270373238773 + k * k) / denominator
    }
    fun frame(input: FloatArray, offset: Int) {
        var ch = 0
        while (ch < channels) {
            val x = input[offset + ch].toDouble(); val i = 2 * ch
            val a = pre[0] * x + y[i]
            y[i] = pre[1] * x + y[i+1] - pre[3] * a
            y[i+1] = pre[2] * x - pre[4] * a
            val b = a + z[i]
            z[i] = -2 * a + z[i+1] - rlb[0] * b
            z[i+1] = a - rlb[1] * b
            val bin = b * b
            val p = pos400 * channels + ch; val t = pos3000 * channels + ch
            s400[ch] += bin - c400[p]; s3000[ch] += bin - c3000[t]
            c400[p] = bin; c3000[t] = bin
            ch++
        }
        if (++pos400 == n400) pos400 = 0
        if (++pos3000 == n3000) pos3000 = 0
        total++
        if (++refresh == rate / 10) {
            refresh = 0
            var p400 = 1e-12; var p3000 = 1e-12
            if (total >= n400) {
                ch = 0; while (ch < channels) { p400 += weights[ch] * s400[ch]; ch++ }; p400 /= n400
            }
            if (total >= n3000) {
                ch = 0; while (ch < channels) { p3000 += weights[ch] * s3000[ch]; ch++ }; p3000 /= n3000
            }
            val l400 = lufs(p400); val l3000 = lufs(p3000)
            if (l400 >= -70) { h400.add(p400,l400); integrated = h400.integrated() }
            if (l3000 >= -70) { h3000.add(p3000,l3000); range = h3000.range() }
        }
    }
    private class Histogram(private val gate: Int): Serializable {
        private val counts = LongArray(8001)
        private val energies = DoubleArray(8001) { 10.0.pow((it / 100.0 - 70 + .691) / 10) }
        private var sum = 0.0
        private var count = 0L
        private var gatePosition = 0
        fun add(power: Double, loudness: Double) {
            counts[((loudness + 70) * 100).toInt().coerceIn(0,8000)]++
            sum += power; count++
            gatePosition = ((lufs(if (sum == 0.0) 1e-12 else sum / count) + gate + 70) * 100).toInt().coerceIn(0,8000)
        }
        fun integrated(): Double {
            var sum = 0.0; var count = 0L; var i = gatePosition
            while (i < counts.size) { count += counts[i]; sum += counts[i] * energies[i]; i++ }
            return if (count > 0) lufs(sum / count) else -70.0
        }
        fun range(): Double {
            var count = 0L; var i = gatePosition
            while (i < counts.size) { count += counts[i]; i++ }
            if (count == 0L) return 0.0
            val lowRank = (.1 * count + .5).toLong(); val highRank = (.95 * count + .5).toLong()
            var n = 0L; var low = gatePosition
            i = gatePosition
            while (i < counts.size) { n += counts[i]; if (n >= lowRank) { low = i; break }; i++ }
            n = count; var high = 8000; i = 8000
            while (i >= 0) { n -= min(n,counts[i]); if (n < highRank) { high = i; break }; i-- }
            return (high - low) / 100.0
        }
    }
    companion object { private fun lufs(energy: Double) = -.691 + 10 * log10(energy) }
}
