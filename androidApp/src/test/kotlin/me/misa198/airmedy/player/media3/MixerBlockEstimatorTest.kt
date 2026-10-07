package me.misa198.airmedy.player.media3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import me.misa198.airmedy.player.media3.MixerBlockEstimator.HeadSample

/**
 * JVM tests pinning the behaviour of [MixerBlockEstimator] (T048a). The class is a test-first stub
 * returning `null` today, so the positive tests fail on assertions until T048b implements it.
 */
class MixerBlockEstimatorTest {

    private val pollIntervalNanos = 2_000_000L

    @Test
    fun cleanSteps48kGive40ms() {
        val samples = samplesFromDeltas(List(100) { 1920L })

        val block = MixerBlockEstimator.estimateBlockMs(samples, 48000)

        assertNotNull(block)
        assertEquals(40.0f, block!!, 0.01f)
    }

    @Test
    fun jitteredSteps96kGive40ms() {
        val samples = samplesFromDeltas(List(100) { index -> listOf(3839L, 3840L, 3841L)[index % 3] })

        val block = MixerBlockEstimator.estimateBlockMs(samples, 96000)

        assertNotNull(block)
        assertEquals(40.0f, block!!, 0.05f)
    }

    @Test
    fun missedPollsAreIgnored() {
        val deltas = List(100) { index -> if (index % 5 == 0) 3840L else 1920L }
        val samples = samplesFromDeltas(deltas)

        val block = MixerBlockEstimator.estimateBlockMs(samples, 48000)

        assertNotNull(block)
        assertEquals(40.0f, block!!, 0.01f)
    }

    @Test
    fun pausesAndResetsAreIgnored() {
        val deltas = mutableListOf<Long>()
        repeat(50) { deltas += 1920L }
        repeat(150) { deltas += 0L }
        deltas += -500L
        repeat(50) { deltas += 1920L }
        val samples = samplesFromDeltas(deltas)

        val block = MixerBlockEstimator.estimateBlockMs(samples, 48000)

        assertNotNull(block)
        assertEquals(40.0f, block!!, 0.01f)
    }

    @Test
    fun primaryThread4ms() {
        val samples = samplesFromDeltas(List(100) { 192L })

        val block = MixerBlockEstimator.estimateBlockMs(samples, 48000)

        assertNotNull(block)
        assertEquals(4.0f, block!!, 0.01f)
    }

    @Test
    fun tooFewStepsGiveNull() {
        val samples = samplesFromDeltas(List(5) { 1920L })

        assertNull(MixerBlockEstimator.estimateBlockMs(samples, 48000))
    }

    @Test
    fun noDominantStepGivesNull() {
        val samples = samplesFromDeltas(List(10) { index -> 1000L + index * 1000L })

        assertNull(MixerBlockEstimator.estimateBlockMs(samples, 48000))
    }

    @Test
    fun dominantClusterBelowHalfGivesNull() {
        val samples = samplesFromDeltas(List(40) { index -> listOf(1920L, 2400L, 2880L, 3360L)[index % 4] })

        assertNull(MixerBlockEstimator.estimateBlockMs(samples, 48000))
    }

    @Test
    fun clusterAtExactlyHalfIsAccepted() {
        val cycle = listOf(1920L, 2400L, 1920L, 2880L, 1920L, 3360L, 1920L, 3840L)
        val samples = samplesFromDeltas(List(32) { index -> cycle[index % 8] })

        val block = MixerBlockEstimator.estimateBlockMs(samples, 48000)

        assertNotNull(block)
        assertEquals(40.0f, block!!, 0.01f)
    }

    @Test
    fun noProgressGivesNull() {
        val samples = samplesFromDeltas(List(100) { 0L })

        assertNull(MixerBlockEstimator.estimateBlockMs(samples, 48000))
    }

    private fun samplesFromDeltas(
        deltas: List<Long>,
        initialHead: Long = 0L,
    ): List<HeadSample> {
        val samples = mutableListOf(HeadSample(0L, initialHead))
        var uptime = 0L
        var head = initialHead
        for (delta in deltas) {
            uptime += pollIntervalNanos
            head += delta
            samples += HeadSample(uptime, head)
        }
        return samples
    }
}
