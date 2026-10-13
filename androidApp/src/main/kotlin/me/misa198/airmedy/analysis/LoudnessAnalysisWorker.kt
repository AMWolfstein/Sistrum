package me.misa198.airmedy.analysis

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import me.misa198.airmedy.sync.AndroidSyncRuntime

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

    /**
     * Runs one budgeted pass: measures each pending track at most once, up to [workerCount] in parallel,
     * writing each result before its slot starts the next track, and checkpointing on cancel.
     */
    suspend fun run(isStopped: () -> Boolean): RunOutcome = coroutineScope {
        val start = clockMs()
        val pending = store.pendingTracks()
        onProgress(store.progress())
        val total = pending.size

        var next = 0
        var finished = 0
        var paused = false

        val inFlight = ArrayList<Deferred<TrackLoudnessResult?>>()

        while (true) {
            // Start new tracks while there is budget, workers and pending work.
            while (true) {
                if (isStopped()) break
                if (next >= total) break
                if (clockMs() - start >= budgetMs) break
                val capacity = workerCount()
                paused = capacity <= 0
                if (capacity <= 0) break
                if (inFlight.size >= capacity) break
                val track = pending[next++]
                val resume = store.loadCheckpoint(track.trackId, track.fingerprint)
                val deferred = async(Dispatchers.IO) {
                    try {
                        measurer.measure(
                            track,
                            resume,
                            onCheckpoint = { bytes -> runBlocking { store.saveCheckpoint(track.trackId, track.fingerprint, bytes) } },
                            isCancelled = { isStopped() || clockMs() - start >= hardStopMs },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        TrackLoudnessResult.Failed(track.trackId, track.fingerprint, "analyzer", "analyzer error: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
                inFlight.add(deferred)
            }

            if (inFlight.isEmpty()) break

            // Await whichever in-flight measurement completes first, so a long track at one slot
            // does not block writing the other slots' finished results (no head-of-line blocking).
            val completed = select<Deferred<TrackLoudnessResult?>> {
                inFlight.forEach { deferred -> deferred.onAwait { deferred } }
            }
            inFlight.remove(completed)
            val result = completed.await()
            if (result != null) {
                store.writeResult(result)
                onProgress(store.progress())
                finished++
            }
        }

        store.recomputeAlbums()

        when {
            isStopped() -> RunOutcome.STOPPED
            finished < total && paused -> RunOutcome.PAUSED
            finished < total -> RunOutcome.MORE_PENDING
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

/** WorkManager entry point: measures every pending track within one run's budget, then reschedules if needed. */
class LoudnessAnalysisWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    /** Runs the background analysis pass. */
    override suspend fun doWork(): Result {
        AndroidSyncRuntime.initialize(applicationContext)
        val store = LoudnessAnalysisStore(LoudnessStore(AndroidSyncRuntime.database()))
        val scheduler = LoudnessAnalysisScheduler.get(applicationContext)
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(OfflineDecoder(applicationContext)) }
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val workers = ThermalWorkerCounter(powerManager, Runtime.getRuntime().availableProcessors())

        val runner = LoudnessAnalysisRunner(
            store = store,
            measurer = measurer,
            workerCount = workers::get,
            clockMs = SystemClock::elapsedRealtime,
            onProgress = scheduler::publishProgress,
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
    }
}
