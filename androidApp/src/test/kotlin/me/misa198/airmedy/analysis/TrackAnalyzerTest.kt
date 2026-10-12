package me.misa198.airmedy.analysis

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import me.misa198.airmedy.analysis.loudness.LoudnessMeter
import me.misa198.airmedy.player.decoders.FormatKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [TrackAnalyzer]: pipelined decoding/metering, ordering, backpressure, cancel and failure. */
class TrackAnalyzerTest {

    @Test
    fun unevenChunksMatchDirectMeter() {
        val rate = 48_000
        val channels = 2
        val frames = 53_104
        val samples = signal(frames, channels, rate)
        val analyzer = TrackAnalyzer(
            FakeDecoder(listOf(1, 7, 4096, 50_000), samples, channels, rate),
            poolBuffers = 4,
            bufferFrames = 4096,
        )

        val result = analyzer.measure("path", null)
        assertTrue("expected Completed, got ${result.outcome}", result.outcome is DecodeOutcome.Completed)

        val direct = LoudnessMeter(rate, channels)
        direct.process(samples, 0, frames)
        direct.flush()

        assertEquals("integrated loudness", direct.integratedLufs(), result.integratedLufs)
        assertEquals("true peak", direct.truePeakDbtp(), result.truePeakDbtp)
        assertEquals("sample peak", direct.samplePeakDbfs(), result.samplePeakDbfs)
        assertEquals("histogram", direct.histogram().encode(), result.histogram?.encode())
    }

    @Test
    fun backpressureNeverExceedsPool() {
        val rate = 48_000
        val channels = 2
        val frames = 48_000
        val samples = signal(frames, channels, rate)
        val analyzer = TrackAnalyzer(
            FakeDecoder(List(frames / 240) { 240 }, samples, channels, rate),
            poolBuffers = 4,
            bufferFrames = 240,
        )

        val result = analyzer.measure("path", null)
        assertTrue("expected Completed, got ${result.outcome}", result.outcome is DecodeOutcome.Completed)
        assertTrue("no chunk was ever in flight", analyzer.peakInFlight > 0)
        assertTrue("outstanding exceeded the pool: ${analyzer.peakInFlight}", analyzer.peakInFlight <= 4)
    }

    @Test
    fun cancelReturnsCancelledAndEndsThreads() {
        val rate = 48_000
        val channels = 2
        val frames = 48_000
        val samples = signal(frames, channels, rate)
        val analyzer = TrackAnalyzer(
            FakeDecoder(List(200) { 240 }, samples, channels, rate, emitDelayMs = 5),
            poolBuffers = 4,
            bufferFrames = 240,
        )
        val flip = AtomicBoolean(false)
        val resultRef = AtomicReference<TrackMeasurement?>()
        val thread = Thread({ resultRef.set(analyzer.measure("path", null) { flip.get() }) }, "measure-thread")
        thread.start()
        Thread.sleep(30)
        flip.set(true)
        thread.join(10_000)
        assertTrue("measure thread did not end", !thread.isAlive)
        val result = resultRef.get()
        assertNotNull("measure returned null", result)
        assertTrue("expected Cancelled, got ${result?.outcome}", result?.outcome is DecodeOutcome.Cancelled)
    }

    @Test
    fun failedDecoderReturnsNulls() {
        val analyzer = TrackAnalyzer(
            FakeDecoder(emptyList(), FloatArray(0), 2, 48_000, result = DecodeOutcome.Failed("fake", "boom")),
        )
        val result = analyzer.measure("path", null)
        assertEquals(DecodeOutcome.Failed("fake", "boom"), result.outcome)
        assertNull(result.integratedLufs)
        assertNull(result.truePeakDbtp)
        assertNull(result.samplePeakDbfs)
        assertNull(result.histogram)
    }

    @Test
    fun zeroFramesReturnsCompletedWithNulls() {
        val analyzer = TrackAnalyzer(
            FakeDecoder(emptyList(), FloatArray(0), 2, 48_000, result = DecodeOutcome.Completed(48_000, 2, 0, "fake")),
        )
        val result = analyzer.measure("path", null)
        assertTrue("expected Completed, got ${result.outcome}", result.outcome is DecodeOutcome.Completed)
        assertNull(result.integratedLufs)
        assertNull(result.truePeakDbtp)
        assertNull(result.samplePeakDbfs)
        assertNull(result.histogram)
    }

    @Test(timeout = 10_000)
    fun twoConsecutiveMeasurementsOnOneAnalyzer() {
        val rate = 48_000
        val stereo = signal(24_000, 2, rate)
        val mono = signal(24_000, 1, rate)
        val analyzer = TrackAnalyzer(
            MapDecoder(mapOf("stereo" to MapDecoder.Signal(stereo, 2, rate), "mono" to MapDecoder.Signal(mono, 1, rate))),
            poolBuffers = 4,
            bufferFrames = 8192,
        )
        assertMatchesDirect(analyzer, "stereo", stereo, 2, rate)
        assertMatchesDirect(analyzer, "mono", mono, 1, rate)
        assertMatchesDirect(analyzer, "stereo", stereo, 2, rate)
    }

    @Test(timeout = 10_000)
    fun meterFailureNeverBlocksTheDecoder() {
        val rate = 48_000
        val samples = signal(10_000, 2, rate)
        val analyzer = TrackAnalyzer(
            FakeDecoder(List(100) { 100 }, samples, 2, rate, reportedRate = 0),
            poolBuffers = 4,
            bufferFrames = 100,
        )
        val result = analyzer.measure("path", null)
        assertTrue("expected Failed, got ${result.outcome}", result.outcome is DecodeOutcome.Failed)
        val failed = result.outcome as DecodeOutcome.Failed
        assertTrue("reason should mention meter failure: ${failed.reason}", failed.reason.startsWith("meter failed"))
        assertNull(result.integratedLufs)
        assertNull(result.truePeakDbtp)
        assertNull(result.samplePeakDbfs)
        assertNull(result.histogram)
    }

    private fun assertMatchesDirect(analyzer: TrackAnalyzer, path: String, samples: FloatArray, channels: Int, rate: Int) {
        val result = analyzer.measure(path, null)
        assertTrue("expected Completed for $path, got ${result.outcome}", result.outcome is DecodeOutcome.Completed)
        val direct = LoudnessMeter(rate, channels)
        direct.process(samples, 0, samples.size / channels)
        direct.flush()
        assertEquals("integrated for $path", direct.integratedLufs(), result.integratedLufs)
        assertEquals("true peak for $path", direct.truePeakDbtp(), result.truePeakDbtp)
        assertEquals("sample peak for $path", direct.samplePeakDbfs(), result.samplePeakDbfs)
        assertEquals("histogram for $path", direct.histogram().encode(), result.histogram?.encode())
    }

    private fun signal(frames: Int, channels: Int, rate: Int): FloatArray {
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val v = (0.1 * sin(2.0 * PI * 440.0 * f / rate) + 0.05 * sin(2.0 * PI * 997.0 * f / rate)).toFloat()
            for (c in 0 until channels) out[f * channels + c] = v
        }
        return out
    }

    private class FakeDecoder(
        private val chunkSizes: List<Int>,
        private val samples: FloatArray,
        private val channels: Int,
        private val rate: Int,
        private val result: DecodeOutcome? = null,
        private val emitDelayMs: Long = 0,
        private val reportedRate: Int? = null,
    ) : PcmDecoder {
        override fun decode(
            path: String,
            key: FormatKey?,
            consumer: PcmConsumer,
            isCancelled: () -> Boolean,
        ): DecodeOutcome {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference<DecodeOutcome?>()
            Thread({
                outcome.set(emit(consumer, isCancelled))
                latch.countDown()
            }, "fake-decode").start()
            latch.await()
            return outcome.get()!!
        }

        private fun emit(consumer: PcmConsumer, isCancelled: () -> Boolean): DecodeOutcome {
            val totalFrames = samples.size / channels
            var off = 0
            for (size in chunkSizes) {
                if (isCancelled()) return DecodeOutcome.Cancelled
                val take = minOf(size, totalFrames - off)
                if (take <= 0) break
                val chunk = FloatArray(take * channels)
                samples.copyInto(chunk, 0, off * channels, (off + take) * channels)
                consumer.accept(chunk, take, reportedRate ?: rate, channels)
                off += take
                if (emitDelayMs > 0) Thread.sleep(emitDelayMs)
            }
            return result ?: DecodeOutcome.Completed(reportedRate ?: rate, channels, off.toLong(), "fake")
        }
    }

    /** Emits a different signal per `path`, for consecutive [measure] calls on one analyzer. */
    private class MapDecoder(
        private val signals: Map<String, Signal>,
        private val chunkSize: Int = 8192,
    ) : PcmDecoder {
        data class Signal(val samples: FloatArray, val channels: Int, val rate: Int)

        override fun decode(
            path: String,
            key: FormatKey?,
            consumer: PcmConsumer,
            isCancelled: () -> Boolean,
        ): DecodeOutcome {
            val signal = signals[path] ?: return DecodeOutcome.Failed("fake", "no signal for $path")
            val totalFrames = signal.samples.size / signal.channels
            var off = 0
            while (off < totalFrames) {
                if (isCancelled()) return DecodeOutcome.Cancelled
                val take = minOf(chunkSize, totalFrames - off)
                val chunk = FloatArray(take * signal.channels)
                signal.samples.copyInto(chunk, 0, off * signal.channels, (off + take) * signal.channels)
                consumer.accept(chunk, take, signal.rate, signal.channels)
                off += take
            }
            return DecodeOutcome.Completed(signal.rate, signal.channels, totalFrames.toLong(), "fake")
        }
    }
}
