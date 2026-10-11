// SPDX-License-Identifier: GPL-3.0-only
// Port of misa198/Airmedy internal/app/analysis/mood at
// 7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1 (GPL-3.0). Copyright misa198.
package com.amwolfstein.sistrum.analysis

import kotlin.math.exp

data class Percentile(val p1: Double = 0.0, val p5: Double = 0.0, val p50: Double = 0.0,
                      val p95: Double = 0.0, val p99: Double = 0.0)
data class RawFeatures(val rms: Double = 0.0, val spectralCentroid: Double = 0.0,
    val spectralFlux: Double = 0.0, val tempo: Double = 0.0, val crest: Double = 0.0,
    val onsetVariance: Double = 0.0, val loudnessRange: Double = 0.0)
data class MoodScores(val energy: Double, val danceability: Double, val brightness: Double)
data class CorpusPercentiles(val percentiles: Map<String, Percentile>, val sampleCount: Int)

object Mood {
    const val SIGMOID_STEEPNESS = 2.5
    val ALL_FEATURE_NAMES: List<String> = listOf("rms", "spectral_centroid", "spectral_flux", "tempo",
        "crest", "onset_variance", "loudness_range")
    private val empty = Percentile()

    fun normalize(x: Double, p: Percentile, k: Double = SIGMOID_STEEPNESS): Double {
        val lo = if (p.p1 > p.p99) p.p99 else p.p1
        val hi = if (p.p1 > p.p99) p.p1 else p.p99
        val clamped = if (x < lo) lo else if (x > hi) hi else x
        val spread = (p.p95 - p.p5) / 2
        if (spread == 0.0) return 0.5
        val z = (clamped - p.p50) / spread
        return 1 / (1 + exp(-k * z))
    }

    /** Linear interpolation of a non-empty sorted array, as in Go's interpolatedPercentile. */
    fun interpolatedPercentile(sorted: DoubleArray, p: Double): Double {
        require(sorted.isNotEmpty() && p >= 0 && p <= 1)
        if (sorted.size == 1) return sorted[0]
        val rank = p * (sorted.size - 1)
        val lo = rank.toInt()
        val hi = lo + 1
        if (hi >= sorted.size) return sorted.last()
        val frac = rank - lo
        return sorted[lo] + frac * (sorted[hi] - sorted[lo])
    }

    /** Pure computation part of RecomputeCorpusPercentiles. The caller owns persistence. */
    fun computeCorpusPercentiles(values: Map<String, DoubleArray>): CorpusPercentiles {
        val result = linkedMapOf<String, Percentile>()
        var count = 0
        for (name in ALL_FEATURE_NAMES) {
            val column = values[name] ?: continue
            if (column.isEmpty()) continue
            val sorted = column.copyOf()
            // Go sort.Float64s orders NaNs before ordinary values.
            var finiteCount = 0
            for (value in column) if (!value.isNaN()) sorted[finiteCount++] = value
            java.util.Arrays.sort(sorted, 0, finiteCount)
            val nanCount = sorted.size - finiteCount
            if (nanCount > 0) {
                sorted.copyInto(sorted, nanCount, 0, finiteCount)
                java.util.Arrays.fill(sorted, 0, nanCount, Double.NaN)
            }
            result[name] = Percentile(interpolatedPercentile(sorted, .01), interpolatedPercentile(sorted, .05),
                interpolatedPercentile(sorted, .50), interpolatedPercentile(sorted, .95), interpolatedPercentile(sorted, .99))
            count = column.size
        }
        return CorpusPercentiles(result, count)
    }

    fun tempoScore(bpm: Double): Double = when {
        bpm <= 60 || bpm >= 180 -> 0.0
        bpm <= 115 -> (bpm - 60) / (115 - 60)
        else -> (180 - bpm) / (180 - 115)
    }

    fun deriveEnergy(f: RawFeatures, p: Map<String, Percentile>): Double {
        val tempo = if (f.tempo > 180) 180.0 else f.tempo
        return .32 * normalize(f.rms, p["rms"] ?: empty) +
            .23 * normalize(f.spectralCentroid, p["spectral_centroid"] ?: empty) +
            .17 * normalize(f.spectralFlux, p["spectral_flux"] ?: empty) +
            .18 * normalize(tempo, p["tempo"] ?: empty) +
            .10 * (1 - normalize(f.crest, p["crest"] ?: empty))
    }
    fun deriveDanceability(f: RawFeatures, p: Map<String, Percentile>): Double =
        .45 * tempoScore(f.tempo) + .30 * (1 - normalize(f.onsetVariance, p["onset_variance"] ?: empty)) +
        .15 * (1 - normalize(f.crest, p["crest"] ?: empty)) +
        .10 * (1 - normalize(f.loudnessRange, p["loudness_range"] ?: empty))
    fun deriveBrightness(f: RawFeatures, p: Map<String, Percentile>): Double =
        normalize(f.spectralCentroid, p["spectral_centroid"] ?: empty)
    fun derive(f: RawFeatures, p: Map<String, Percentile>) =
        MoodScores(deriveEnergy(f, p), deriveDanceability(f, p), deriveBrightness(f, p))
}
