package me.misa198.airmedy.player.media3

/**
 * Estimates the output thread's mixer block duration from consecutive
 * [HeadSample.headFrames] readings, using the device fact that the mixer advances each track's
 * playback head once per mixer cycle (so polling faster than that cycle yields the block size in
 * track frames as the dominant positive delta).
 */
internal object MixerBlockEstimator {

    data class HeadSample(val uptimeNanos: Long, val headFrames: Long)

    /**
     * Clusters the positive head-frame deltas (zero and negative deltas are ignored) around the most
     * frequent exact value within ±2 frames. Returns the largest cluster's mean, in milliseconds, or
     * null when the largest cluster has fewer than 8 members or less than half of all positive deltas.
     */
    fun estimateBlockMs(samples: List<HeadSample>, trackSampleRate: Int): Float? {
        if (trackSampleRate <= 0) return null
        if (samples.size < 2) return null

        val positiveDeltas = ArrayList<Long>(samples.size - 1)
        for (i in 1 until samples.size) {
            val delta = samples[i].headFrames - samples[i - 1].headFrames
            if (delta > 0) positiveDeltas += delta
        }
        if (positiveDeltas.isEmpty()) return null

        val clusters = mutableListOf<List<Long>>()
        val remaining = positiveDeltas.toMutableList()
        while (remaining.isNotEmpty()) {
            val centre = mostFrequent(remaining)
            val members = remaining.filter { it in centre - 2..centre + 2 }
            clusters += members
            remaining.removeAll(members)
        }

        val largest = clusters.maxByOrNull { it.size } ?: return null
        if (largest.size < 8) return null
        if (largest.size * 2 < positiveDeltas.size) return null

        val blockFrames = largest.average().toFloat()
        return blockFrames / trackSampleRate.toFloat() * 1000f
    }

    private fun mostFrequent(values: List<Long>): Long {
        val counts = HashMap<Long, Int>()
        var best = values[0]
        var bestCount = 0
        for (value in values) {
            val count = (counts[value] ?: 0) + 1
            counts[value] = count
            if (count > bestCount) {
                bestCount = count
                best = value
            }
        }
        return best
    }
}
