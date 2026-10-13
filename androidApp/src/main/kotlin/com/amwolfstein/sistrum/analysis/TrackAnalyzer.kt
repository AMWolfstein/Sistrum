package com.amwolfstein.sistrum.analysis

import android.os.Process
import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import com.amwolfstein.sistrum.analysis.loudness.LoudnessMeter
import com.amwolfstein.sistrum.player.decoders.FormatKey

/** Where to continue an interrupted measurement: frames already metered and the meter state after them. */
internal class MeterResume(
    val framesMetered: Long,
    val sampleRate: Int,
    val channels: Int,
    val meterState: ByteArray,
)

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
 * buffer pool that applies backpressure when the meter cannot keep up. Supports resuming from a
 * [MeterResume] checkpoint and periodic [onCheckpoint] callbacks from the meter thread.
 */
internal class TrackAnalyzer(
    private val decoder: PcmDecoder,
    private val poolBuffers: Int = 8,
    private val bufferFrames: Int = 16_384,
    private val checkpointIntervalMs: Long = 60_000L,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val meterThreadPriority: Int = Process.THREAD_PRIORITY_BACKGROUND,
) {

    /** Peak number of chunks in flight in the most recent [measure], for backpressure tests. */
    internal val peakInFlight: Int get() = peakInFlightCount.get()

    private val peakInFlightCount = AtomicInteger(0)

    fun measure(
        path: String,
        key: FormatKey?,
        isCancelled: () -> Boolean = { false },
        resume: MeterResume? = null,
        onCheckpoint: (MeterResume) -> Unit = {},
    ): TrackMeasurement {
        peakInFlightCount.set(0)
        val prepared = restoreOrNull(resume)
        val first = runPass(path, key, isCancelled, onCheckpoint, prepared)
        return if (first.hadFormatMismatch) {
            runPass(path, key, isCancelled, onCheckpoint, null).measurement
        } else {
            first.measurement
        }
    }

    /** One decode pass; [resume] is null for a full measurement from frame 0. */
    private fun runPass(
        path: String,
        key: FormatKey?,
        isCancelled: () -> Boolean,
        onCheckpoint: (MeterResume) -> Unit,
        resume: PreparedResume?,
    ): PassResult {
        val free = ArrayBlockingQueue<Chunk>(poolBuffers)
        val filled = ArrayBlockingQueue<Chunk>(poolBuffers)
        val meterRef = AtomicReference<LoudnessMeter?>()
        val meterErrorRef = AtomicReference<Throwable?>()
        val decodeOutcomeRef = AtomicReference<DecodeOutcome?>()
        val done = AtomicBoolean(false)
        val outstanding = AtomicInteger(0)
        val poolBuilt = AtomicBoolean(false)
        val mismatch = AtomicBoolean(false)
        val firstChunkSeen = AtomicBoolean(false)

        if (resume != null) meterRef.set(resume.meter)

        val startUs = if (resume != null) resume.framesMetered * 1_000_000L / resume.sampleRate else 0L

        val consumer = PcmConsumer { samples, frames, rate, channels ->
            var consume = true
            if (resume != null && firstChunkSeen.compareAndSet(false, true)) {
                if (rate != resume.sampleRate || channels != resume.channels) {
                    mismatch.set(true)
                    consume = false
                }
            }
            if (consume && !mismatch.get()) {
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
        }

        val meterThread = Thread({
            Process.setThreadPriority(meterThreadPriority)
            var error: Throwable? = null
            var framesMetered = resume?.framesMetered ?: 0L
            var lastCheckpointMs = clockMs()
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
                            framesMetered += chunk.frames
                        } catch (t: Throwable) {
                            error = t
                            meterErrorRef.set(t)
                            meterRef.set(null)
                        }
                    }
                    outstanding.decrementAndGet()
                    free.put(chunk)
                    if (error == null) {
                        val meter = meterRef.get()
                        if (meter != null && clockMs() - lastCheckpointMs >= checkpointIntervalMs) {
                            emitCheckpoint(onCheckpoint, mismatch, meter, framesMetered)
                            lastCheckpointMs = clockMs()
                        }
                    }
                } else if (finished) {
                    break
                }
            }
            if (error == null) {
                val meter = meterRef.get()
                if (meter != null) {
                    if (decodeOutcomeRef.get() is DecodeOutcome.Cancelled) {
                        emitCheckpoint(onCheckpoint, mismatch, meter, framesMetered)
                    } else {
                        meter.flush()
                    }
                }
            }
        }, "sistrum-analysis-meter").apply { start() }

        val outcome = try {
            decoder.decode(path, key, consumer, { isCancelled() || mismatch.get() }, startUs).also { decodeOutcomeRef.set(it) }
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
        return PassResult(
            TrackMeasurement(
                outcome = finalOutcome,
                integratedLufs = meter?.integratedLufs(),
                truePeakDbtp = meter?.truePeakDbtp(),
                samplePeakDbfs = meter?.samplePeakDbfs(),
                histogram = meter?.histogram(),
            ),
            hadFormatMismatch = mismatch.get(),
        )
    }

    /** Builds the fixed chunk pool on the first consumer call of this [measure], sized by its channel count. */
    private fun ensurePool(free: ArrayBlockingQueue<Chunk>, channels: Int, poolBuilt: AtomicBoolean) {
        if (!poolBuilt.compareAndSet(false, true)) return
        for (i in 0 until poolBuffers) {
            free.put(Chunk(FloatArray(bufferFrames * channels), 0, 0, channels))
        }
    }

    /** Restores [resume]'s meter, or returns null when it is unusable (bad state, mismatch or a corrupt meter state). */
    private fun restoreOrNull(resume: MeterResume?): PreparedResume? {
        if (resume == null) return null
        if (resume.framesMetered < 0L) return null
        return try {
            val meter = LoudnessMeter.restore(resume.meterState)
            if (meter.sampleRate != resume.sampleRate || meter.channels != resume.channels) {
                null
            } else {
                PreparedResume(meter, resume.framesMetered, resume.sampleRate, resume.channels)
            }
        } catch (t: Throwable) {
            null
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

        /** Emits one checkpoint, swallowing any exception thrown by [onCheckpoint]. */
        fun emitCheckpoint(
            onCheckpoint: (MeterResume) -> Unit,
            mismatch: AtomicBoolean,
            meter: LoudnessMeter,
            framesMetered: Long,
        ) {
            if (mismatch.get()) return
            try {
                onCheckpoint(MeterResume(framesMetered, meter.sampleRate, meter.channels, meter.checkpoint()))
            } catch (t: Throwable) {
                // an exception from the callback must not stop the measurement
            }
        }
    }

    /** A resume that restored successfully, with the decoder's expected output format. */
    private data class PreparedResume(
        val meter: LoudnessMeter,
        val framesMetered: Long,
        val sampleRate: Int,
        val channels: Int,
    )

    /** One [measure] pass's result, plus whether a resume format mismatch forced a restart from zero. */
    private data class PassResult(val measurement: TrackMeasurement, val hadFormatMismatch: Boolean)
}
