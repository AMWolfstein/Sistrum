package com.amwolfstein.sistrum.analysis

import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram

/**
 * Version tag for everything measured by the loudness pipeline (meter, histogram, decode path).
 * A mismatch makes a track pending again; old values stay in use until the new ones are written.
 */
const val ANALYZER_VERSION = "sistrum-loudness-1/waxflow-446ca31"

/** Bump whenever a decoder provider is added or replaced; retries failed tracks only. */
const val DECODER_SET_VERSION = "sistrum-decoders-002-m1"

/** An available track that has no current `analysis` document for the requested analyzer version. */
data class PendingTrack(
    val trackId: String,
    val audioPath: String,
    val fingerprint: String,
    val format: String,
    val codec: String,
    val durationMs: Long,
)

/** The outcome of analyzing one track. [fingerprint] is the audio asset sha256 seen at analysis time. */
sealed interface TrackLoudnessResult {
    val trackId: String
    val fingerprint: String

    data class Measured(
        override val trackId: String,
        override val fingerprint: String,
        val integratedLufs: Double,
        val truePeakDbtp: Double,
        val samplePeakDbfs: Double,
        val histogram: LoudnessHistogram,
    ) : TrackLoudnessResult

    data class Silent(
        override val trackId: String,
        override val fingerprint: String,
    ) : TrackLoudnessResult

    data class Failed(
        override val trackId: String,
        override val fingerprint: String,
        val provider: String,
        val reason: String,
    ) : TrackLoudnessResult
}

/** Worker progress: [analyzed] tracks with a current document (any status) out of [total] available tracks. */
data class AnalysisProgress(val analyzed: Int, val total: Int)

/**
 * One track as the album planner sees it. [status] is `"ok"`, `"silent"`, `"failed"` or null
 * (not analyzed); [histogram] is the encoded histogram for `ok` and `silent`.
 */
data class PlannerTrack(
    val trackId: String,
    val albumId: String,
    val album: String,
    val audioPath: String,
    val tagAlbum: String,
    val available: Boolean,
    val fingerprint: String,
    val status: String?,
    val analyzerVersion: String?,
    val histogram: String?,
    val truePeakDbtp: Double?,
)

/** A complete album measurement; [membersFingerprint] gates whether a rewrite is needed. */
data class AlbumRecord(
    val loudnessLufs: Double,
    val truePeakDbtp: Double,
    val members: Int,
    val membersFingerprint: String,
)

/** The planner's desired album state: records to write and album ids whose existing records to delete. */
data class AlbumPlan(
    val write: Map<String, AlbumRecord>,
    val delete: Set<String>,
)
