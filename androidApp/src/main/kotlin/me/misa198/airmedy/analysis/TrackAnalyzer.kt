package me.misa198.airmedy.analysis

import android.os.Process
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import me.misa198.airmedy.analysis.loudness.LoudnessHistogram
import me.misa198.airmedy.analysis.loudness.LoudnessMeter
import me.misa198.airmedy.player.decoders.FormatKey

/** Result of measuring one track. */
internal data class TrackMeasurement(
    val outcome: DecodeOutcome,
    val integratedLufs: Double?,
    val truePeakDbtp: Double?,
    val samplePeakDbfs: Double?,
    val histogram: LoudnessHistogram?,
)

/**
 * Decodes on the decoder's thread and meters on its own background thread, connected by a bounded
 * buffer pool that applies backpressure when the meter cannot keep up.
 */
internal class TrackAnalyzer(
    private val decoder: PcmDecoder,
    private val poolBuffers: Int = 8,
    private val bufferFrames: Int = 16_384,
) {

    /** Peak number of chunks in flight in the most recent [measure], for backpressure tests. */
    internal val peakInFlight: Int get() = peakInFlightCount.get()

    private val peakInFlightCount = AtomicInteger(0)

    fun measure(path: String, key: FormatKey?, isCancelled: () -> Boolean = { false }): TrackMeasurement {
        peakInFlightCount.set(0)
        val free = ArrayBlockingQueue<Chunk>(poolBuffers)
        val filled = ArrayBlockingQueue<Chunk>(poolBuffers)
        val meterRef = AtomicReference<LoudnessMeter?>()
        val meterErrorRef = AtomicReference<Throwable?>()
        val done = AtomicBoolean(false)
        val outstanding = AtomicInteger(0)
        val poolBuilt = AtomicBoolean(false)

        val consumer = PcmConsumer { samples, frames, rate, channels ->
            ensurePool(free, channels, poolBuilt)
            var off = 0
            while (off < frames) {
                val take = minOf(bufferFrames, frames - off)
                val chunk = free.take()
                chunk.rate = rate
                chunk.channels = channels
                chunk.frames = take
                samples.copyInto(chunk.data, 0, off * channels, (off + take) * channels)
                peakInFlightCount.updateAndGet { maxOf(it, outstanding.incrementAndGet()) }
                filled.put(chunk)
                off += take
            }
        }

        val meterThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            var error: Throwable? = null
            while (true) {
                // Read `done` BEFORE polling: every put happens-before done.set(true), so an empty poll after seeing
                // `finished` means the queue is really drained (polling first could drop the last chunks).
                val finished = done.get()
                val chunk = filled.poll(METER_POLL_MS, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    if (error == null) {
                        try {
                            val meter = meterRef.get() ?: LoudnessMeter(chunk.rate, chunk.channels).also { meterRef.set(it) }
                            meter.process(chunk.data, 0, chunk.frames)
                        } catch (t: Throwable) {
                            error = t
                            meterErrorRef.set(t)
                            meterRef.set(null)
                        }
                    }
                    outstanding.decrementAndGet()
                    free.put(chunk)
                } else if (finished) {
                    break
                }
            }
            if (error == null) meterRef.get()?.flush()
        }, "sistrum-analysis-meter").apply { start() }

        val outcome = try {
            decoder.decode(path, key, consumer, isCancelled)
        } finally {
            done.set(true)
            try {
                meterThread.join()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        val meterError = meterErrorRef.get()
        val finalOutcome = when {
            meterError != null ->
                DecodeOutcome.Failed(providerOf(outcome), "meter failed: ${meterError.message ?: meterError.javaClass.simpleName}")
            outcome is DecodeOutcome.Completed && (meterThread.isAlive || outstanding.get() != 0) ->
                DecodeOutcome.Failed(providerOf(outcome), "meter incomplete")
            else -> outcome
        }

        // Values only for a complete measurement; a cancelled or failed one never carries partial loudness.
        val meter = meterRef.get()?.takeIf { finalOutcome is DecodeOutcome.Completed }
        return TrackMeasurement(
            outcome = finalOutcome,
            integratedLufs = meter?.integratedLufs(),
            truePeakDbtp = meter?.truePeakDbtp(),
            samplePeakDbfs = meter?.samplePeakDbfs(),
            histogram = meter?.histogram(),
        )
    }

    /** Builds the fixed chunk pool on the first consumer call of this [measure], sized by its channel count. */
    private fun ensurePool(free: ArrayBlockingQueue<Chunk>, channels: Int, poolBuilt: AtomicBoolean) {
        if (!poolBuilt.compareAndSet(false, true)) return
        for (i in 0 until poolBuffers) {
            free.put(Chunk(FloatArray(bufferFrames * channels), 0, 0, channels))
        }
    }

    private fun providerOf(outcome: DecodeOutcome): String? = when (outcome) {
        is DecodeOutcome.Completed -> outcome.providerId
        is DecodeOutcome.Failed -> outcome.providerId
        else -> null
    }

    private class Chunk(var data: FloatArray, var frames: Int, var rate: Int, var channels: Int)

    private companion object {
        const val METER_POLL_MS = 10L
    }
}
