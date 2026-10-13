package com.amwolfstein.sistrum.player.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * T056: pins the per-player equal-power fade before T058 implements it. Constant PCM input
 * exposes the product of normalization and fade gains. Every test has a positive fade expectation
 * that fails on an assertion against the no-op API, including tests of snaps and final values.
 */
class EqualPowerFadeTest {
    @Test
    fun incomingCurveMatchesFloatFormulaAndHoldsUnity() {
        val processor = configured(channels = 2)
        processor.startFade(FadeDirection.In, 100L)
        assertCurve(feed(processor, 4808, channels = 2), FadeDirection.In, 4800L, channels = 2)
    }

    @Test
    fun outgoingCurveMatchesFloatFormulaAndHoldsZero() {
        val processor = configured(channels = 2)
        processor.startFade(FadeDirection.Out, 100L)
        assertCurve(feed(processor, 4808, channels = 2), FadeDirection.Out, 4800L, channels = 2)
    }

    @Test
    fun tenMillisecondsAt44100HzHas441CurveFrames() {
        for (direction in FadeDirection.values()) {
            val processor = configured(rate = 44100)
            processor.startFade(direction, 10L)
            // Frame 440 is still on the curve; frame 441 is the first exact final value.
            assertCurve(feed(processor, 444), direction, 441L)
        }
    }

    @Test
    fun zeroDurationStillProcessesOneCurveFrame() {
        for (direction in FadeDirection.values()) {
            val processor = configured()
            processor.startFade(direction, 0L)
            assertCurve(feed(processor, 4), direction, 1L)
        }
    }

    @Test
    fun settledNormalizationMultipliesIncomingFade() {
        val processor = configured()
        processor.setTargetGainDb(-6f)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.startFade(FadeDirection.In, 100L)
        val normalization = 10.0.pow(-6.0 / 20.0).toFloat()
        assertCurve(feed(processor, 4808), FadeDirection.In, 4800L, scale = normalization)
    }

    @Test
    fun normalizationRampDuringFadeMultipliesEachFrame() {
        val processor = configured(channels = 2)
        val total = 24000L // A 500 ms fade outlasts the complete 200 ms normalization ramp.
        val boundary = 480
        processor.startFade(FadeDirection.In, 500L)
        val before = feed(processor, boundary, channels = 2)
        processor.setTargetGainDb(6f)
        val duringAndAfter = feed(processor, total.toInt() + 8 - boundary, channels = 2)
        val target = 10.0.pow(6.0 / 20.0).toFloat()
        val rampFrames = 9600
        for (frame in 0 until duringAndAfter.size / 2) {
            // Normalization advances before the frame is applied, starting at boundary + 1 step.
            val steps = minOf(frame + 1, rampFrames)
            val normalization = (1.0 + (target.toDouble() - 1.0) * steps / rampFrames).toFloat()
            val expected = normalization * curve(FadeDirection.In, boundary + frame.toLong(), total)
            for (channel in 0..1) {
                assertEquals("ramp frame $frame channel $channel", expected, duringAndAfter[frame * 2 + channel], 1e-5f)
            }
        }
        assertCurve(before, FadeDirection.In, total, channels = 2)
    }

    @Test
    fun snapIncomingImmediatelyHoldsNormalizedUnity() {
        assertSnap(FadeDirection.In, flushAfterSnap = false)
    }

    @Test
    fun snapOutgoingImmediatelyHoldsZero() {
        assertSnap(FadeDirection.Out, flushAfterSnap = false)
    }

    @Test
    fun snappedIncomingSurvivesFlush() {
        assertSnap(FadeDirection.In, flushAfterSnap = true)
    }

    @Test
    fun snappedOutgoingSurvivesFlush() {
        assertSnap(FadeDirection.Out, flushAfterSnap = true)
    }

    @Test
    fun plainFlushPreservesIncomingCurvePosition() {
        assertFlushPreservesPosition(FadeDirection.In)
    }

    @Test
    fun plainFlushPreservesOutgoingCurvePosition() {
        assertFlushPreservesPosition(FadeDirection.Out)
    }

    @Test
    fun twelveSecondIncomingFadeIsContinuousAcrossMixedBuffers() {
        assertLongFade(FadeDirection.In)
    }

    @Test
    fun twelveSecondOutgoingFadeIsContinuousAcrossMixedBuffers() {
        assertLongFade(FadeDirection.Out)
    }

    @Test
    fun futureOffsetHoldsIncomingStartForExactly100Frames() {
        assertFutureOffset(FadeDirection.In)
    }

    @Test
    fun futureOffsetHoldsOutgoingStartForExactly100Frames() {
        assertFutureOffset(FadeDirection.Out)
    }

    @Test
    fun pastOffsetStartsIncomingPartWayAndBeyondEndHoldsUnity() {
        assertPastOffsets(FadeDirection.In)
    }

    @Test
    fun pastOffsetStartsOutgoingPartWayAndBeyondEndHoldsZero() {
        assertPastOffsets(FadeDirection.Out)
    }

    @Test
    fun remainingFramesTracksCurveProgressAndEnd() {
        val processor = configured()
        assertEquals("no fade", 0L, processor.fadeRemainingFrames)
        processor.startFade(FadeDirection.In, 100L)
        feed(processor, 480)
        assertEquals("after first buffer", 4320L, processor.fadeRemainingFrames)
        feed(processor, 4319)
        assertEquals("last curve frame remains", 1L, processor.fadeRemainingFrames)
        feed(processor, 1)
        assertEquals("after last curve frame", 0L, processor.fadeRemainingFrames)
        feed(processor, 8)
        assertEquals("after final hold", 0L, processor.fadeRemainingFrames)
    }

    @Test
    fun remainingFramesIncludesFutureOffset() {
        val processor = configured()
        processor.startFade(FadeDirection.In, 100L, startOffsetFrames = 100L)
        feed(processor, 480)
        assertEquals("held frames count too", 4420L, processor.fadeRemainingFrames)
        feed(processor, 4420)
        assertEquals("held frames and curve consumed", 0L, processor.fadeRemainingFrames)
    }

    @Test
    fun remainingFramesIsZeroAfterSnap() {
        val processor = configured()
        processor.startFade(FadeDirection.In, 100L)
        feed(processor, 480)
        val beforeSnap = processor.fadeRemainingFrames
        processor.snapFade()
        feed(processor, 1)
        assertEquals("snap consumes remaining curve", 0L, processor.fadeRemainingFrames)
        assertEquals("positive remaining count before snap", 4320L, beforeSnap)
    }

    @Test
    fun audibleEndIncludesLeadAndRoundsUp() {
        // ADR-003: the fade-out pause waits until buffered audio is also audibly finished.
        assertEquals("zero curve and zero lead", 0L, fadeAudibleEndMs(0L, 0L, 48000))
        assertEquals("curve plus processed-but-inaudible lead", 1190L, fadeAudibleEndMs(24000L, 33120L, 48000))
        assertEquals("fractional millisecond rounds up", 1L, fadeAudibleEndMs(1L, 0L, 44100))
        assertEquals("lead alone still delays the audible end", 690L, fadeAudibleEndMs(0L, 33120L, 48000))
    }

    @Test
    fun fadeStartedOnAnotherThreadIsAppliedAtNextBuffer() {
        val processor = configured()
        feed(processor, 480)
        val thread = Thread { processor.startFade(FadeDirection.In, 100L) }
        thread.start()
        thread.join()
        assertCurve(feed(processor, 480), FadeDirection.In, 4800L)
    }

    @Test
    fun sixteenBitInputMultipliesIncomingCurve() {
        val processor = configured(channels = 2, encoding = C.ENCODING_PCM_16BIT)
        processor.startFade(FadeDirection.In, 100L)
        val output = feed(processor, 4808, channels = 2, encoding = C.ENCODING_PCM_16BIT)
        assertCurve(output, FadeDirection.In, 4800L, channels = 2, scale = 0.5f)
    }

    @Test
    fun completedIncomingCanBeReusedForOutgoing() {
        val processor = configured()
        processor.startFade(FadeDirection.In, 100L)
        val incoming = feed(processor, 4808)
        processor.startFade(FadeDirection.Out, 100L)
        val outgoing = feed(processor, 4808)
        assertEquals("incoming holds unity before role swap", 1f, incoming.last(), 0f)
        assertCurve(outgoing, FadeDirection.Out, 4800L)
        assertCurve(incoming, FadeDirection.In, 4800L)
    }

    @Test
    fun newFadeReplacesRunningCurveFromFrameZero() {
        val processor = configured()
        processor.startFade(FadeDirection.Out, 100L)
        val outgoing = feed(processor, 1000)
        processor.startFade(FadeDirection.In, 100L)
        val incoming = feed(processor, 1000)
        processor.startFade(FadeDirection.In, 100L)
        val restartedIncoming = feed(processor, 4808)
        assertCurve(restartedIncoming, FadeDirection.In, 4800L)
        assertCurve(incoming, FadeDirection.In, 4800L)
        assertCurve(outgoing, FadeDirection.Out, 4800L)
    }

    private fun assertSnap(direction: FadeDirection, flushAfterSnap: Boolean) {
        val processor = configured()
        processor.setTargetGainDb(-6f)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val normalization = 10.0.pow(-6.0 / 20.0).toFloat()
        processor.startFade(direction, 100L)
        val beforeSnap = feed(processor, 1000)
        processor.snapFade()
        if (flushAfterSnap) processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val afterSnap = feed(processor, 480)
        val finalGain = if (direction == FadeDirection.In) normalization else 0f
        for (frame in afterSnap.indices) {
            assertEquals("$direction snapped frame $frame", finalGain, afterSnap[frame], 0f)
        }
        assertEquals("$direction remaining after snap", 0L, processor.fadeRemainingFrames)
        // Incoming final gain alone would pass on the stub; the pre-snap curve must exist too.
        assertCurve(beforeSnap, direction, 4800L, scale = normalization)
    }

    private fun assertFlushPreservesPosition(direction: FadeDirection) {
        val processor = configured()
        processor.startFade(direction, 100L)
        val beforeFlush = feed(processor, 1000)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val afterFlush = feed(processor, 3808)
        assertCurve(afterFlush, direction, 4800L, firstCurveFrame = 1000L)
        assertCurve(beforeFlush, direction, 4800L)
    }

    private fun assertLongFade(direction: FadeDirection) {
        val total = 576000L
        val processor = configured()
        processor.startFade(direction, 12000L)
        val output = feed(processor, total.toInt() + 8, bufferSizes = intArrayOf(1, 7, 480, 1024, 4096))
        val maximumStep = (PI / 2 / total).toFloat() + 1e-6f
        for (frame in 1 until output.size) {
            val step = abs(output[frame] - output[frame - 1])
            assertTrue("$direction step at frame $frame: $step > $maximumStep", step <= maximumStep)
        }
        // Continuity alone passes with unity output; pin the entire curve and its final hold too.
        assertCurve(output, direction, total)
    }

    private fun assertFutureOffset(direction: FadeDirection) {
        val processor = configured()
        processor.startFade(direction, 100L, startOffsetFrames = 100L)
        // Mixed buffers straddle the held/start/curve boundary.
        val output = feed(processor, 4908, bufferSizes = intArrayOf(7, 1, 480))
        assertCurve(output, direction, 4800L, firstCurveFrame = -100L)
    }

    private fun assertPastOffsets(direction: FadeDirection) {
        val processor = configured()
        processor.startFade(direction, 100L, startOffsetFrames = -1000L)
        val partWay = feed(processor, 3808)
        processor.startFade(direction, 100L, startOffsetFrames = -5000L)
        val beyondEnd = feed(processor, 8)
        assertCurve(beyondEnd, direction, 4800L, firstCurveFrame = 5000L)
        assertEquals("$direction already finished", 0L, processor.fadeRemainingFrames)
        // Incoming beyond-end unity alone would pass on the stub.
        assertCurve(partWay, direction, 4800L, firstCurveFrame = 1000L)
    }

    /** Reference float formula, independent of any processor fade state or implementation. */
    private fun curve(direction: FadeDirection, at: Long, total: Long): Float {
        if (at < 0L) return if (direction == FadeDirection.In) 0f else 1f
        if (at >= total) return if (direction == FadeDirection.In) 1f else 0f
        val phase = at.toFloat() / total.toFloat() * (PI / 2).toFloat()
        return if (direction == FadeDirection.In) sin(phase) else cos(phase)
    }

    private fun assertCurve(
        output: FloatArray,
        direction: FadeDirection,
        total: Long,
        firstCurveFrame: Long = 0L,
        channels: Int = 1,
        scale: Float = 1f,
    ) {
        for (frame in 0 until output.size / channels) {
            val at = firstCurveFrame + frame
            val expected = scale * curve(direction, at, total)
            // Held start and final values must be exact, including the first post-curve frame.
            val tolerance = if (at < 0L || at >= total) 0f else 1e-6f
            for (channel in 0 until channels) {
                assertEquals("$direction frame $frame (curve $at) channel $channel", expected, output[frame * channels + channel], tolerance)
            }
        }
    }

    private fun configured(
        rate: Int = 48000,
        channels: Int = 1,
        encoding: Int = C.ENCODING_PCM_FLOAT,
    ): GainProcessor = GainProcessor().apply {
        configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    private fun feed(
        processor: GainProcessor,
        frames: Int,
        channels: Int = 1,
        encoding: Int = C.ENCODING_PCM_FLOAT,
        bufferSizes: IntArray = intArrayOf(480),
    ): FloatArray {
        val output = FloatArray(frames * channels)
        var done = 0
        var block = 0
        while (done < frames) {
            val count = minOf(bufferSizes[block % bufferSizes.size], frames - done)
            val samples = count * channels
            val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val input = ByteBuffer.allocateDirect(samples * bytesPerSample).order(ByteOrder.nativeOrder())
            if (encoding == C.ENCODING_PCM_FLOAT) {
                input.asFloatBuffer().put(FloatArray(samples) { 1f })
            } else {
                input.asShortBuffer().put(ShortArray(samples) { 16384.toShort() })
            }
            input.position(0)
            processor.queueInput(input)
            val produced = processor.getOutput().order(ByteOrder.nativeOrder()).asFloatBuffer()
            assertEquals("output sample count for block $block", samples, produced.remaining())
            produced.get(output, done * channels, samples)
            done += count
            block++
        }
        return output
    }
}
