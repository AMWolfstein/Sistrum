package me.misa198.airmedy.player.media3

/**
 * Stub for T048a; implemented in T048b.
 */
internal object MixerBlockEstimator {

    data class HeadSample(val uptimeNanos: Long, val headFrames: Long)

    @Suppress("UNUSED_PARAMETER")
    fun estimateBlockMs(samples: List<HeadSample>, trackSampleRate: Int): Float? = null
}
