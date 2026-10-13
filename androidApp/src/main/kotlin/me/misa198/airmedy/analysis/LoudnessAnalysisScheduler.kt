package me.misa198.airmedy.analysis

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
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

    /** Publishes [progress] to observers. */
    fun publishProgress(progress: AnalysisProgress) {
        _progress.value = progress
    }

    /** Called after every library scan: enqueue a run, keeping any already-queued one. */
    fun onScanCompleted() {
        enqueuer.enqueueUnique(WORK_NAME, ExistingWorkPolicy.KEEP, buildRequest())
    }

    /** Called by the worker when a run left tracks pending: enqueue a continuation after [delayMs]. */
    fun enqueueContinuation(delayMs: Long) {
        enqueuer.enqueueUnique(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, buildRequest(delayMs))
    }

    companion object {
        /** Unique work name for the background analysis. */
        const val WORK_NAME = "loudness-analysis"

        @Volatile
        private var instance: LoudnessAnalysisScheduler? = null

        /** The process-wide scheduler; its enqueuer is WorkManager on [context]. */
        fun get(context: Context): LoudnessAnalysisScheduler {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: LoudnessAnalysisScheduler(
                    enqueuer = AnalysisWorkEnqueuer { name, policy, request ->
                        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(name, policy, request)
                    },
                ).also { instance = it }
            }
        }

        /** The background request: charging, battery not low, storage not low; [initialDelayMs] initial delay. */
        fun buildRequest(initialDelayMs: Long = 0): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<LoudnessAnalysisWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
    }
}
