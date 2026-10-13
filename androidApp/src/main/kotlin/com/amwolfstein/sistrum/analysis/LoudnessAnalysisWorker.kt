package com.amwolfstein.sistrum.analysis

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import com.amwolfstein.sistrum.sync.AndroidSyncRuntime

/** Store operations the analysis job uses. */
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

/** A process-wide, thread-safe set of track ids currently being measured by any runner. */
internal class AnalysisClaims {
    private val claimed = ConcurrentHashMap.newKeySet<String>()
    private val runs = mutableSetOf<RunClaims>()

    /** Tracks claimed during a run's pending snapshot, including claims already released. */
    internal class RunClaims(val seen: MutableSet<String>) {
        val abandoned = mutableSetOf<String>()
    }

    @Synchronized
    fun startRun(): RunClaims = RunClaims(claimed.toMutableSet()).also { runs.add(it) }

    @Synchronized
    fun endRun(run: RunClaims) {
        runs.remove(run)
    }

    /** Number of tracks currently claimed across every runner. */
    val size: Int get() = claimed.size

    /** Claims [trackId]; false when another runner already holds it. */
    @Synchronized
    fun claim(trackId: String): Boolean {
        if (!claimed.add(trackId)) return false
        runs.forEach { it.seen.add(trackId) }
        return true
    }

    /** Releases the slot, allowing active runs to retry a track that has no stored result. */
    @Synchronized
    fun release(trackId: String, completed: Boolean) {
        claimed.remove(trackId)
        if (!completed) {
            runs.forEach {
                it.abandoned.add(trackId)
                it.seen.remove(trackId)
            }
        }
    }

    /** Whether another runner abandoned any of the tracks this run already skipped. */
    @Synchronized
    fun hasAbandoned(run: RunClaims, skipped: Set<String>): Boolean = skipped.any { it in run.abandoned }

    /** Atomically claims [trackId] while fewer than [limit] tracks are claimed. */
    @Synchronized
    fun claimWithinLimit(trackId: String, limit: Int, run: RunClaims? = null): ClaimResult {
        if (run?.seen?.contains(trackId) == true || claimed.contains(trackId)) return ClaimResult.SKIPPED
        if (claimed.size >= limit) return ClaimResult.FULL
        claim(trackId)
        return ClaimResult.CLAIMED
    }

    companion object {
        /** The shared claims set every runner uses. */
        val process = AnalysisClaims()
    }
}

/** Outcome of attempting to claim a track within the shared worker limit. */
internal enum class ClaimResult { CLAIMED, SKIPPED, FULL }

/** One budgeted pass over the pending tracks with an adaptive number of parallel measurements. */
internal class LoudnessAnalysisRunner(
    private val store: AnalysisJobStore,
    private val measurer: TrackMeasurer,
    private val workerCount: () -> Int,
    private val clockMs: () -> Long,
    private val onProgress: (AnalysisProgress) -> Unit = {},
    private val budgetMs: Long = BUDGET_MS,
    private val hardStopMs: Long = HARD_STOP_MS,
    private val claims: AnalysisClaims = AnalysisClaims(),
    private val waitPollMs: Long = 250L,
) {

    /**
     * Runs one budgeted pass: measures each pending track at most once, up to [workerCount] in parallel,
     * writing each result before its slot starts the next track, and checkpointing on cancel.
     */
    suspend fun run(isStopped: () -> Boolean): RunOutcome {
        val run = claims.startRun()
        return try {
            runClaimed(isStopped, run)
        } finally {
            claims.endRun(run)
        }
    }

    private suspend fun runClaimed(isStopped: () -> Boolean, run: AnalysisClaims.RunClaims): RunOutcome = coroutineScope {
        val start = clockMs()
        val pending = store.pendingTracks()
        onProgress(store.progress())
        val total = pending.size

        var next = 0
        var finished = 0
        val skipped = mutableSetOf<String>()
        var paused = false

        val inFlight = ArrayList<Deferred<TrackLoudnessResult?>>()

        try {
            while (true) {
                // Start new tracks while there is budget, workers and pending work.
                var atLimit = false
                while (true) {
                    if (isStopped()) break
                    if (next >= total) break
                    if (clockMs() - start >= budgetMs) break
                    val capacity = workerCount()
                    paused = capacity <= 0
                    if (capacity <= 0) break
                    val track = pending[next]
                    when (claims.claimWithinLimit(track.trackId, capacity, run)) {
                        ClaimResult.FULL -> {
                            atLimit = true
                            break
                        }
                        ClaimResult.SKIPPED -> {
                            next++
                            skipped.add(track.trackId)
                            continue
                        }
                        ClaimResult.CLAIMED -> next++
                    }
                    val resume = try {
                        store.loadCheckpoint(track.trackId, track.fingerprint)
                    } catch (e: Throwable) {
                        claims.release(track.trackId, completed = false)
                        throw e
                    }
                    val deferred = async(Dispatchers.IO) {
                        val job = coroutineContext.job
                        var completed = false
                        try {
                            val result = measurer.measure(
                                track,
                                resume,
                                onCheckpoint = { bytes -> runBlocking { store.saveCheckpoint(track.trackId, track.fingerprint, bytes) } },
                                isCancelled = { isStopped() || clockMs() - start >= hardStopMs || !job.isActive },
                            )
                            if (result != null) {
                                withContext(NonCancellable) {
                                    store.writeResult(result)
                                    completed = true
                                    onProgress(store.progress())
                                }
                            }
                            result
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            val failed = TrackLoudnessResult.Failed(
                                track.trackId,
                                track.fingerprint,
                                "analyzer",
                                "analyzer error: ${e.message ?: e.javaClass.simpleName}",
                            )
                            withContext(NonCancellable) {
                                store.writeResult(failed)
                                completed = true
                                onProgress(store.progress())
                            }
                            failed
                        } finally {
                            claims.release(track.trackId, completed)
                        }
                    }
                    inFlight.add(deferred)
                }

                if (inFlight.isEmpty()) {
                    // The shared limit is fully held by another runner; wait and retry instead of ending.
                    if (atLimit && next < total && !isStopped() && clockMs() - start < budgetMs) {
                        delay(waitPollMs)
                        continue
                    }
                    break
                }

                // Await whichever in-flight measurement completes first, so a long track at one slot
                // does not block the other slots' finished results (no head-of-line blocking).
                val completed = select<Deferred<TrackLoudnessResult?>> {
                    inFlight.forEach { deferred -> deferred.onAwait { deferred } }
                }
                inFlight.remove(completed)
                if (completed.await() != null) finished++
            }
        } finally {
            withContext(NonCancellable) {
                store.recomputeAlbums()
            }
        }

        val morePending = finished + skipped.size < total || claims.hasAbandoned(run, skipped)
        when {
            isStopped() -> RunOutcome.STOPPED
            morePending && paused -> RunOutcome.PAUSED
            morePending -> RunOutcome.MORE_PENDING
            else -> RunOutcome.FINISHED
        }
    }

    companion object {
        /** No new track starts once the run has lasted this long. */
        const val BUDGET_MS = 8 * 60_000L

        /** In-flight tracks are cancelled (checkpointed) once the run has lasted this long. */
        const val HARD_STOP_MS = 9 * 60_000L

        /** Delay before a continuation after a paused run. */
        const val PAUSE_DELAY_MS = 10 * 60_000L

        /** Maps a run outcome to the worker's follow-up. */
        fun followUp(outcome: RunOutcome): RunFollowUp = when (outcome) {
            RunOutcome.FINISHED -> RunFollowUp(false, 0)
            RunOutcome.MORE_PENDING -> RunFollowUp(true, 0)
            RunOutcome.PAUSED -> RunFollowUp(true, PAUSE_DELAY_MS)
            RunOutcome.STOPPED -> RunFollowUp(false, 0)
        }
    }
}

/** Adapts [LoudnessStore] to [AnalysisJobStore], softening checkpoint failures so the job never dies on them. */
internal class LoudnessAnalysisStore(private val store: LoudnessStore) : AnalysisJobStore {
    override suspend fun pendingTracks(): List<PendingTrack> = store.pendingTracks()

    override suspend fun writeResult(result: TrackLoudnessResult) = store.writeResult(result)

    override suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray) {
        try {
            store.saveCheckpoint(trackId, fingerprint, state)
        } catch (e: Throwable) {
            Log.w(TAG, "checkpoint save failed for $trackId; it will be re-measured from an earlier point", e)
        }
    }

    override suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray? = try {
        store.loadCheckpoint(trackId, fingerprint)
    } catch (e: Throwable) {
        null
    }

    override suspend fun recomputeAlbums() = store.recomputeAlbums()

    override suspend fun progress(): AnalysisProgress = store.progress()

    private companion object {
        const val TAG = "SistrumAnalysis"
    }
}

/** Throttles notification updates: true on the first call, then only after [minIntervalMs], always true when [final]. */
internal class ProgressThrottle(
    private val clockMs: () -> Long,
    private val minIntervalMs: Long = 1000,
) {
    private var lastTrueMs: Long? = null

    /** Whether an update should be emitted now. */
    @Synchronized
    fun shouldUpdate(final: Boolean = false): Boolean {
        if (final) return true
        val now = clockMs()
        val last = lastTrueMs
        if (last == null || now - last >= minIntervalMs) {
            lastTrueMs = now
            return true
        }
        return false
    }
}

/** A foreground "now" run is only allowed on its first attempt; a re-run hands off to the background job. */
internal fun nowRunAllowed(runAttemptCount: Int): Boolean = runAttemptCount == 0

/** A cancelled or incomplete foreground run must append background work; null means it threw before returning. */
internal fun nowRunHandOff(outcome: RunOutcome?): Boolean = outcome != RunOutcome.FINISHED

/** Maps the device SDK level to the foreground-service type the "now" analysis must declare. */
@SuppressLint("InlinedApi")
internal fun analysisForegroundServiceType(sdkInt: Int): Int = when {
    sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    else -> 0
}

/** WorkManager entry point: measures every pending track within one run's budget, then reschedules if needed. */
class LoudnessAnalysisWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    /** Runs the background analysis pass. */
    override suspend fun doWork(): Result {
        AndroidSyncRuntime.initialize(applicationContext)
        val store = LoudnessAnalysisStore(LoudnessStore(AndroidSyncRuntime.database()))
        val scheduler = LoudnessAnalysisScheduler.get(applicationContext)
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val workers = ThermalWorkerCounter(powerManager, Runtime.getRuntime().availableProcessors())

        val mode = inputData.getString(LoudnessAnalysisScheduler.KEY_MODE)
        if (mode == LoudnessAnalysisScheduler.MODE_NOW) {
            return runNow(store, scheduler, workers)
        }
        return runBackground(store, scheduler, workers)
    }

    /** The existing charging-driven pass; unchanged except for the shared claims set. */
    private suspend fun runBackground(
        store: LoudnessAnalysisStore,
        scheduler: LoudnessAnalysisScheduler,
        workers: ThermalWorkerCounter,
    ): Result {
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(OfflineDecoder(applicationContext)) }
        val runner = LoudnessAnalysisRunner(
            store = store,
            measurer = measurer,
            workerCount = workers::get,
            clockMs = SystemClock::elapsedRealtime,
            onProgress = scheduler::publishProgress,
            claims = AnalysisClaims.process,
        )

        val analyzedBefore = store.progress().analyzed
        val startMs = SystemClock.elapsedRealtime()
        val outcome = runner.run { isStopped }
        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        val written = store.progress().analyzed - analyzedBefore

        val followUp = LoudnessAnalysisRunner.followUp(outcome)
        if (followUp.enqueueContinuation) scheduler.enqueueContinuation(followUp.delayMs)

        Log.i(TAG, "run $outcome, $written track(s) written, ${elapsedMs} ms")
        return Result.success()
    }

    /** The user-started foreground pass: no budget, default thread priority, a progress notification with Cancel. */
    private suspend fun runNow(
        store: LoudnessAnalysisStore,
        scheduler: LoudnessAnalysisScheduler,
        workers: ThermalWorkerCounter,
    ): Result {
        if (!nowRunAllowed(runAttemptCount)) {
            scheduler.onScanCompleted()
            return Result.success()
        }

        AnalysisNotification.ensureChannel(applicationContext)
        val initial = store.progress()
        setForeground(foregroundInfo(AnalysisNotification.build(applicationContext, id, initial.analyzed, initial.total)))

        val measurer = AnalyzerTrackMeasurer {
            TrackAnalyzer(
                OfflineDecoder(applicationContext, threadPriority = Process.THREAD_PRIORITY_DEFAULT),
                meterThreadPriority = Process.THREAD_PRIORITY_DEFAULT,
            )
        }
        val throttle = ProgressThrottle(SystemClock::elapsedRealtime)
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        val updateNotification: (AnalysisProgress, Boolean) -> Unit = { progress, final ->
            // After a stop, WorkManager removes the foreground notification; posting again would leave it stuck.
            if (!isStopped && throttle.shouldUpdate(final)) {
                notificationManager.notify(
                    NOTIFICATION_ID,
                    AnalysisNotification.build(applicationContext, id, progress.analyzed, progress.total),
                )
            }
        }

        val runner = LoudnessAnalysisRunner(
            store = store,
            measurer = measurer,
            workerCount = workers::get,
            clockMs = SystemClock::elapsedRealtime,
            onProgress = { progress ->
                scheduler.publishProgress(progress)
                updateNotification(progress, false)
            },
            budgetMs = Long.MAX_VALUE,
            hardStopMs = Long.MAX_VALUE,
            claims = AnalysisClaims.process,
        )

        var outcome: RunOutcome? = null
        try {
            outcome = runner.run { isStopped }
        } finally {
            withContext(NonCancellable) {
                if (nowRunHandOff(outcome)) scheduler.enqueueContinuation(0)
                if (outcome == null || isStopped) notificationManager.cancel(NOTIFICATION_ID)
                else updateNotification(store.progress(), true)
            }
        }

        Log.i(TAG, "now run $outcome")
        return Result.success()
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo {
        val type = analysisForegroundServiceType(Build.VERSION.SDK_INT)
        return if (type == 0) ForegroundInfo(NOTIFICATION_ID, notification)
        else ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    /** Caches the thermal worker count so [PowerManager.getThermalHeadroom] is sampled at most every 10 s. */
    private class ThermalWorkerCounter(
        private val powerManager: PowerManager,
        private val cores: Int,
    ) {
        private var cached = -1
        private var cachedAtMs = 0L

        fun get(): Int {
            val now = SystemClock.elapsedRealtime()
            if (cached < 0 || now - cachedAtMs >= CACHE_MS) {
                val headroom = powerManager.getThermalHeadroom(10)
                cached = AnalysisConcurrency.workerCount(
                    availableCores = cores,
                    thermalStatus = powerManager.currentThermalStatus,
                    thermalHeadroom = if (headroom.isNaN()) null else headroom,
                )
                cachedAtMs = now
            }
            return cached
        }
    }

    private companion object {
        const val TAG = "SistrumAnalysis"
        const val CACHE_MS = 10_000L
        const val NOTIFICATION_ID = 2003
    }
}
