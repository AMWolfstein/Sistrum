package com.amwolfstein.sistrum.analysis

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.amwolfstein.sistrum.player.decoders.FormatKey

/** Encodes/decodes [MeterResume] for the `loudness_progress` document (versioned, little-endian). */
internal object MeterResumeCodec {
    fun encode(resume: MeterResume): ByteArray {
        val state = resume.meterState
        val bb = ByteBuffer.allocate(4 + 8 + 4 + 4 + 4 + state.size + 1 + (if (resume.silence != null) 16 else 0)).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(VERSION)
        bb.putLong(resume.framesMetered)
        bb.putInt(resume.sampleRate)
        bb.putInt(resume.channels)
        bb.putInt(state.size)
        bb.put(state)
        bb.put(if (resume.silence != null) 1.toByte() else 0.toByte())
        resume.silence?.let { bb.putLong(it.leadFrames); bb.putLong(it.tailFrames) }
        return bb.array()
    }

    /** Null for a wrong version, truncated or corrupt input; never throws. */
    fun decode(bytes: ByteArray): MeterResume? = try {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.getInt() != VERSION) return null
        val framesMetered = bb.getLong()
        val sampleRate = bb.getInt()
        val channels = bb.getInt()
        val stateLen = bb.getInt()
        if (framesMetered < 0L) return null
        if (sampleRate <= 0) return null
        if (channels !in 1..8) return null
        if (stateLen < 0 || stateLen > bb.remaining()) return null
        val state = ByteArray(stateLen)
        bb.get(state)
        val silence = when (bb.get().toInt()) {
            0 -> null
            1 -> SilenceResume(bb.getLong(), bb.getLong()).also {
                if (it.leadFrames !in 0..framesMetered || it.tailFrames !in 0..framesMetered) return null
            }
            else -> return null
        }
        if (bb.remaining() != 0) return null
        MeterResume(framesMetered, sampleRate, channels, state, silence)
    } catch (t: Throwable) {
        null
    }

    private const val VERSION = 2
}

/** The job's [TrackMeasurer]: a fresh decoder + [TrackAnalyzer] per track (OfflineDecoder is not reentrant). */
internal class AnalyzerTrackMeasurer(private val analyzerFactory: () -> TrackAnalyzer) : TrackMeasurer {
    override fun measure(
        track: PendingTrack,
        resume: ByteArray?,
        onCheckpoint: (ByteArray) -> Unit,
        isCancelled: () -> Boolean,
    ): TrackLoudnessResult? {
        val key = if (track.format.isNotBlank() && track.codec.isNotBlank()) FormatKey(track.format, track.codec) else null
        val meterResume = resume?.let(MeterResumeCodec::decode)?.takeIf { it.silence != null }
        if (resume != null && meterResume == null) {
            Log.i("AnalysisMeasurer", "${track.trackId}: checkpoint lacks current silence state; restarting from frame zero")
        }
        val measurement = analyzerFactory().measure(
            path = track.audioPath,
            key = key,
            isCancelled = isCancelled,
            resume = meterResume,
            onCheckpoint = { onCheckpoint(MeterResumeCodec.encode(it)) },
        )
        return when (val outcome = measurement.outcome) {
            is DecodeOutcome.Completed -> {
                val lufs = measurement.integratedLufs
                val tp = measurement.truePeakDbtp
                val sp = measurement.samplePeakDbfs
                val histogram = measurement.histogram
                if (lufs != null && tp != null && sp != null && histogram != null) {
                    TrackLoudnessResult.Measured(track.trackId, track.fingerprint, lufs, tp, sp, histogram, measurement.silence)
                } else {
                    TrackLoudnessResult.Silent(track.trackId, track.fingerprint, measurement.silence)
                }
            }
            is DecodeOutcome.Failed ->
                TrackLoudnessResult.Failed(track.trackId, track.fingerprint, outcome.providerId ?: "unknown", outcome.reason)
            is DecodeOutcome.Unsupported ->
                TrackLoudnessResult.Failed(track.trackId, track.fingerprint, "none", outcome.reason)
            DecodeOutcome.Cancelled -> null
        }
    }
}
