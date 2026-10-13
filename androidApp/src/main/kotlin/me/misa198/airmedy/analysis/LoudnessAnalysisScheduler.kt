package me.misa198.airmedy.analysis

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
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
internal class LoudnessAnalysisScheduler(
    private val enqueuer: AnalysisWorkEnqueuer,
    private val canceller: (String) -> Unit = {},
) {

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

    /** Starts a user-requested foreground analysis, replacing any already-running "now" work. */
    fun analyzeNow() {
        enqueuer.enqueueUnique(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, buildNowRequest())
    }

    /** Cancels the foreground "now" work without touching the background job. */
    fun cancelAnalyzeNow() {
        canceller(NOW_WORK_NAME)
    }

    companion object {
        /** Unique work name for the background analysis. */
        const val WORK_NAME = "loudness-analysis"

        /** Unique work name for the user-requested foreground analysis. */
        const val NOW_WORK_NAME = "loudness-analysis-now"

        /** Input-data key whose value distinguishes the foreground "now" run. */
        const val KEY_MODE = "mode"

        /** Input-data value marking the foreground "now" run. */
        const val MODE_NOW = "now"

        @Volatile
        private var instance: LoudnessAnalysisScheduler? = null

        /** The process-wide scheduler; its enqueuer and canceller are WorkManager on [context]. */
        fun get(context: Context): LoudnessAnalysisScheduler {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: LoudnessAnalysisScheduler(
                    enqueuer = AnalysisWorkEnqueuer { name, policy, request ->
                        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(name, policy, request)
                    },
                    canceller = { name -> WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name) },
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

        /** The foreground request: storage not low only, tagged and marked with the "now" mode. */
        fun buildNowRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<LoudnessAnalysisWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .setInputData(Data.Builder().putString(KEY_MODE, MODE_NOW).build())
                .addTag(NOW_WORK_NAME)
                .build()
    }
}
