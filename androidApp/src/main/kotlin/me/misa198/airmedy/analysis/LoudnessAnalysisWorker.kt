package me.misa198.airmedy.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

/** Store operations the analysis job uses (LoudnessStore is adapted to it in T051f). */
internal interface AnalysisJobStore {
    /** Tracks that still need an analysis document. */
    suspend fun pendingTracks(): List<PendingTrack>

    /** Persists one track result. */
    suspend fun writeResult(result: TrackLoudnessResult)

    /** Saves the resume state of an in-progress track. */
    suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray)

    /** Loads the resume state of a track, or null when there is none. */
    suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray?

    /** Rewrites album loudness from the current track state. */
    suspend fun recomputeAlbums()

    /** A snapshot of overall analysis progress. */
    suspend fun progress(): AnalysisProgress
}

/**
 * Measures one track on the calling thread (blocking). [resume] is a checkpoint from an earlier interrupted run of
 * the same track, or null. It calls [onCheckpoint] from time to time during a long track and once more just before
 * returning when cancelled. Returns null when cancelled ([isCancelled] became true), otherwise the result.
 */
internal fun interface TrackMeasurer {
    fun measure(track: PendingTrack, resume: ByteArray?, onCheckpoint: (ByteArray) -> Unit, isCancelled: () -> Boolean): TrackLoudnessResult?
}

/** How a run ended. */
internal enum class RunOutcome { FINISHED, MORE_PENDING, PAUSED, STOPPED }

/** What the worker does after a run: enqueue a continuation after [delayMs], or nothing. */
internal data class RunFollowUp(val enqueueContinuation: Boolean, val delayMs: Long)

/** One budgeted pass over the pending tracks with an adaptive number of parallel measurements. */
internal class LoudnessAnalysisRunner(
    private val store: AnalysisJobStore,
    private val measurer: TrackMeasurer,
    private val workerCount: () -> Int,
    private val clockMs: () -> Long,
    private val onProgress: (AnalysisProgress) -> Unit = {},
    private val budgetMs: Long = BUDGET_MS,
    private val hardStopMs: Long = HARD_STOP_MS,
) {

    /** Runs one budgeted pass over the pending tracks; the stub returns [RunOutcome.FINISHED]. */
    suspend fun run(isStopped: () -> Boolean): RunOutcome = RunOutcome.FINISHED

    companion object {
        /** No new track starts once the run has lasted this long. */
        const val BUDGET_MS = 8 * 60_000L

        /** In-flight tracks are cancelled (checkpointed) once the run has lasted this long. */
        const val HARD_STOP_MS = 9 * 60_000L

        /** Delay before a continuation after a paused run. */
        const val PAUSE_DELAY_MS = 10 * 60_000L

        /** Maps a run outcome to the worker's follow-up (stub). */
        fun followUp(outcome: RunOutcome): RunFollowUp = RunFollowUp(false, 0)
    }
}

/** WorkManager entry point; T051f wires the runner, the store and the measurer. */
class LoudnessAnalysisWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    /** Runs the background analysis pass (stub). */
    override suspend fun doWork(): Result = Result.success()
}
