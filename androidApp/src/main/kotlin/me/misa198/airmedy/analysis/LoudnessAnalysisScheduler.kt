package me.misa198.airmedy.analysis

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Seam over WorkManager.enqueueUniqueWork so the scheduler is testable without a Context. */
internal fun interface AnalysisWorkEnqueuer {
    /** Enqueues [request] under [name] with [policy]. */
    fun enqueueUnique(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest)
}

/** Schedules the background loudness analysis and publishes its progress. */
internal class LoudnessAnalysisScheduler(private val enqueuer: AnalysisWorkEnqueuer) {

    private val _progress = MutableStateFlow<AnalysisProgress?>(null)

    /** The latest published progress, or null before the first report. */
    val progress: StateFlow<AnalysisProgress?> = _progress.asStateFlow()

    /** Publishes [progress] to observers (stub). */
    fun publishProgress(progress: AnalysisProgress) { }

    /** Called after every library scan (stub). */
    fun onScanCompleted() { }

    /** Called by the worker when a run left tracks pending (stub). */
    fun enqueueContinuation(delayMs: Long) { }

    companion object {
        /** Unique work name for the background analysis. */
        const val WORK_NAME = "loudness-analysis"

        /** The background request: charging, battery not low, storage not low; [initialDelayMs] initial delay (stub). */
        fun buildRequest(initialDelayMs: Long = 0): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<LoudnessAnalysisWorker>().build()
    }
}
