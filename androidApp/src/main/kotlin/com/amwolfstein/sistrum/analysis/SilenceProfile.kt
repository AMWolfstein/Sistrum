package com.amwolfstein.sistrum.analysis

import com.amwolfstein.sistrum.dsp.silence.Detector

/** Edge durations in the decoded PCM timeline, truncated to whole milliseconds. */
data class SilenceProfile(val leadMs: Long, val tailMs: Long) {
    companion object { const val VERSION = Detector.VERSION }
}

internal data class SilenceResume(val leadFrames: Long, val tailFrames: Long)

/** Detect chunk edges at frame resolution, join runs, then apply the one-second minimum at EOF. */
internal class SilenceAccumulator(
    private val rate: Int,
    channels: Int,
    bufferFrames: Int,
    private var frames: Long = 0,
    resume: SilenceResume? = null,
) {
    private val detector = Detector(rate, channels, -60.0, 1L, bufferFrames / 2 + 1)
    private val planar = Array(channels) { FloatArray(bufferFrames) }
    private var lead = resume?.leadFrames ?: 0L
    private var tail = resume?.tailFrames ?: 0L

    fun process(samples: FloatArray, count: Int) {
        for (c in planar.indices) for (f in 0 until count) planar[c][f] = samples[f * planar.size + c]
        detector.reset()
        detector.process(planar, frames = count)
        detector.flush()
        val first = if (detector.spanCount > 0 && detector.from(0) == 0L) detector.to(0) else 0L
        val last = detector.spanCount - 1
        val end = if (last >= 0 && detector.to(last) == count.toLong()) count - detector.from(last) else 0L
        if (lead == frames) lead += first
        tail = if (first == count.toLong()) tail + count else end
        frames += count
    }

    fun checkpoint() = SilenceResume(lead, tail)
    fun profile() = SilenceProfile(
        if (lead >= rate) lead * 1000 / rate else 0,
        if (tail >= rate) tail * 1000 / rate else 0,
    )
}
