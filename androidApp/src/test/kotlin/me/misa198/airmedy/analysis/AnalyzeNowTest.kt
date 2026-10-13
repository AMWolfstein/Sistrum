package me.misa198.airmedy.analysis

import android.content.pm.ServiceInfo
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T051g/T051h: JVM specification of "analyze now" — the foreground request, shared track claims, the re-run guard,
 * foreground-service typing, the notification throttle, and saving finished work on a cancelled run.
 */
class AnalyzeNowTest {

    @Test
    fun nowRunningReflectsAllActiveAndTerminalStates() = runBlocking {
        val snapshots = listOf(
            emptyList(), listOf(WorkInfo.State.ENQUEUED), listOf(WorkInfo.State.SUCCEEDED),
            listOf(WorkInfo.State.RUNNING), listOf(WorkInfo.State.FAILED),
            listOf(WorkInfo.State.BLOCKED), listOf(WorkInfo.State.CANCELLED),
            listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED), emptyList(),
        )
        val scheduler = LoudnessAnalysisScheduler(FakeEnqueuer(), nowStates = flowOf(*snapshots.toTypedArray()))
        assertEquals(listOf(false, true, false, true, false, true, false, true, false), scheduler.nowRunning.toList())
    }

    @Test
    fun loadProgressPublishesPersistedCounts() = runBlocking {
        val store = TestStore(listOf(track("a"), track("b")))
        store.writeResult(TrackLoudnessResult.Silent("a", "fp-a"))
        val scheduler = LoudnessAnalysisScheduler(FakeEnqueuer(), progressLoader = store::progress)
        scheduler.loadProgress()
        assertEquals(AnalysisProgress(1, 2), scheduler.progress.value)
    }

    @Test
    fun loadProgressFailurePreservesLatestCountsAndDoesNotThrow() = runBlocking {
        val scheduler = LoudnessAnalysisScheduler(FakeEnqueuer(), progressLoader = { error("store unavailable") })
        scheduler.loadProgress()
        assertEquals(null, scheduler.progress.value)
        scheduler.publishProgress(AnalysisProgress(3, 5))
        scheduler.loadProgress()
        assertEquals(AnalysisProgress(3, 5), scheduler.progress.value)
    }

    @Test
    fun analyzeNowEnqueuesReplaceUniqueNowWork() {
        val enqueuer = FakeEnqueuer()
        val scheduler = LoudnessAnalysisScheduler(enqueuer)

        scheduler.analyzeNow()

        assertEquals(1, enqueuer.calls.size)
        val (name, policy, request) = enqueuer.calls.single()
        assertEquals(LoudnessAnalysisScheduler.NOW_WORK_NAME, name)
        assertEquals(ExistingWorkPolicy.REPLACE, policy)
        assertEquals(LoudnessAnalysisWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals(LoudnessAnalysisScheduler.MODE_NOW, request.workSpec.input.getString(LoudnessAnalysisScheduler.KEY_MODE))
    }

    @Test
    fun nowRequestHasOnlyStorageConstraintAndIsNotExpedited() {
        val request = LoudnessAnalysisScheduler.buildNowRequest()

        assertEquals(LoudnessAnalysisWorker::class.java.name, request.workSpec.workerClassName)
        assertFalse("must not require charging", request.workSpec.constraints.requiresCharging())
        assertFalse("must not require battery not low", request.workSpec.constraints.requiresBatteryNotLow())
        assertTrue("must require storage not low", request.workSpec.constraints.requiresStorageNotLow())
        assertFalse("must not be expedited", request.workSpec.expedited)
    }

    @Test
    fun analyzeNowDoesNotCancelBackgroundWork() {
        val enqueuer = FakeEnqueuer()
        val canceller = FakeCanceller()
        val scheduler = LoudnessAnalysisScheduler(enqueuer, canceller::cancel)

        scheduler.analyzeNow()

        assertTrue("never cancels the background work", canceller.names.none { it == LoudnessAnalysisScheduler.WORK_NAME })
        assertEquals(1, enqueuer.calls.size)
        assertEquals(LoudnessAnalysisScheduler.NOW_WORK_NAME, enqueuer.calls.single().first)
    }

    @Test
    fun cancelAnalyzeNowCancelsOnlyNowWork() {
        val enqueuer = FakeEnqueuer()
        val canceller = FakeCanceller()
        val scheduler = LoudnessAnalysisScheduler(enqueuer, canceller::cancel)

        scheduler.cancelAnalyzeNow()

        assertEquals(listOf(LoudnessAnalysisScheduler.NOW_WORK_NAME), canceller.names.toList())
        assertTrue(enqueuer.calls.isEmpty())
    }

    @Test(timeout = 10_000)
    fun twoRunnersSharingClaimsNeverMeasureTheSameTrack() {
        val tracks = (1..6).map { track("t$it") }
        val claims = AnalysisClaims()
        val store = TestStore(tracks)
        val measurer = GatedMeasurer()
        val runnersStarted = CountDownLatch(2)
        val onProgress: (AnalysisProgress) -> Unit = {
            runnersStarted.countDown()
            check(runnersStarted.await(2, TimeUnit.SECONDS)) { "both runners must start before measuring" }
        }

        val runnerA = LoudnessAnalysisRunner(store, measurer, { 2 }, { 0L }, onProgress, claims = claims)
        val runnerB = LoudnessAnalysisRunner(store, measurer, { 2 }, { 0L }, onProgress, claims = claims)

        val a = startInThread { runnerA.run { false } }
        val b = startInThread { runnerB.run { false } }

        await { measurer.active() > 0 }
        measurer.release.countDown()

        a.join(8000)
        b.join(8000)
        assertFalse("runner A did not return", a.thread.isAlive)
        assertFalse("runner B did not return", b.thread.isAlive)

        tracks.forEach { assertEquals("track ${it.trackId} measured exactly once", 1, measurer.callCount(it.trackId)) }
        assertTrue("in flight never exceeded workerCount", measurer.peakInFlight <= 2)
        assertEquals(6, store.results.size)
    }

    @Test(timeout = 10_000)
    fun trackClaimedByOtherRunIsNotCountedUnfinished() {
        val tracks = listOf(track("t1"), track("t2"), track("t3"))
        val claims = AnalysisClaims()
        tracks.forEach { claims.claim(it.trackId) }
        val store = TestStore(tracks)
        val measurer = CountingMeasurer()

        val runner = LoudnessAnalysisRunner(store, measurer, { 10 }, { 0L }, claims = claims)
        val outcome = runBlocking { runner.run { false } }

        assertEquals(RunOutcome.FINISHED, outcome)
        tracks.forEach { assertEquals("nothing is measured by the runner", 0, measurer.callCount(it.trackId)) }
        assertTrue(store.results.isEmpty())
    }

    @Test(timeout = 10_000)
    fun foregroundRunHasNoBudget() {
        val tracks = listOf(track("t1"), track("t2"), track("t3"))
        val store = TestStore(tracks)
        val clock = AtomicLong(0)
        val measurer = ClockJumpMeasurer(clock)

        val runner = LoudnessAnalysisRunner(
            store,
            measurer,
            { 1 },
            clock::get,
            budgetMs = Long.MAX_VALUE,
            hardStopMs = Long.MAX_VALUE,
        )
        val outcome = runBlocking { runner.run { false } }

        assertEquals(RunOutcome.FINISHED, outcome)
        tracks.forEach { assertEquals(1, measurer.callCount(it.trackId)) }
        assertEquals(3, store.results.size)
    }

    @Test
    fun reRunAfterStopDoesNotAnalyze() {
        assertTrue(nowRunAllowed(0))
        assertFalse(nowRunAllowed(1))
        assertFalse(nowRunAllowed(2))
    }

    @Test
    fun foregroundTypePerSdk() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, analysisForegroundServiceType(35))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, analysisForegroundServiceType(36))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, analysisForegroundServiceType(34))
        assertEquals(0, analysisForegroundServiceType(33))
        assertEquals(0, analysisForegroundServiceType(31))
    }

    @Test
    fun progressThrottleAtMostOncePerSecond() {
        val clock = AtomicLong(0)
        val throttle = ProgressThrottle(clock::get)

        assertTrue("first update is emitted", throttle.shouldUpdate())
        assertFalse("no update within one second", throttle.shouldUpdate())
        clock.addAndGet(999)
        assertFalse("still within one second", throttle.shouldUpdate())
        clock.addAndGet(1)
        assertTrue("one second later", throttle.shouldUpdate())
        assertFalse("reset after an update", throttle.shouldUpdate())
        assertTrue("final update always emitted", throttle.shouldUpdate(final = true))
    }

    @Test(timeout = 10_000)
    fun cancelledRunStillWritesFinishedAndRecomputesOnce() {
        val tracks = listOf(track("t1"), track("t2"))
        val store = TestStore(tracks)
        val measurer = CancelAwareMeasurer(store)
        val runner = LoudnessAnalysisRunner(store, measurer, { 2 }, { 0L })

        val job = Job()
        val error = AtomicReference<Throwable?>()
        val thread = Thread({
            try {
                runBlocking(job) { runner.run { false } }
            } catch (t: Throwable) {
                error.set(t)
            }
        }, "cancel-run")
        thread.isDaemon = true
        thread.start()

        await { store.results.size == 1 }
        job.cancel()
        thread.join(8000)
        assertFalse("run returned after cancellation", thread.isAlive)

        assertTrue("cancellation propagated", error.get() is CancellationException)
        assertEquals("only the finished track is written", listOf(TrackLoudnessResult.Silent("t1", "fp-t1")), store.results.toList())
        assertEquals("albums recomputed exactly once", 1, store.recomputeCalls)
        assertTrue("the in-flight measurement observed cancellation", measurer.sawCancelled.get())
    }

    @Test(timeout = 10_000)
    fun runnerWaitsWhileOtherRunnerHoldsAllSlots() {
        val claims = AnalysisClaims()
        val measurerA = GatedMeasurer()
        val storeA = TestStore(listOf(track("a")))
        val runnerA = LoudnessAnalysisRunner(storeA, measurerA, { 1 }, { 0L }, claims = claims)

        val bWorkerRetried = CountDownLatch(2)
        val measurerB = CountingMeasurer()
        val storeB = TestStore(listOf(track("b")))
        val runnerB = LoudnessAnalysisRunner(
            storeB,
            measurerB,
            { bWorkerRetried.countDown(); 1 },
            { 0L },
            claims = claims,
            waitPollMs = 10L,
        )

        val handleA = startInThread { runnerA.run { false } }
        try {
            assertTrue("A holds the slot", measurerA.started.await(2, TimeUnit.SECONDS))

            val handleB = startInThread { runnerB.run { false } }
            assertTrue("B retried while A still holds the slot", bWorkerRetried.await(2, TimeUnit.SECONDS))
            assertTrue("B must still be running while A holds the slot", handleB.thread.isAlive)
            assertEquals("B measured nothing while waiting", 0, measurerB.callCount("b"))

            measurerA.release.countDown()
            handleB.join(2000)
            assertFalse(handleB.thread.isAlive)
            assertEquals(1, measurerB.callCount("b"))
            assertEquals(RunOutcome.FINISHED, handleB.outcome.get())
        } finally {
            measurerA.release.countDown()
            handleA.join(2000)
            assertFalse("runner A did not return", handleA.thread.isAlive)
            assertEquals(RunOutcome.FINISHED, handleA.outcome.get())
        }
    }

    @Test(timeout = 10_000)
    fun waitingRunnerStillStopsOnIsStopped() {
        val claims = AnalysisClaims()
        val measurerA = GatedMeasurer()
        val storeA = TestStore(listOf(track("a")))
        val runnerA = LoudnessAnalysisRunner(storeA, measurerA, { 1 }, { 0L }, claims = claims)

        val stopped = AtomicBoolean(false)
        val bWorkerRetried = CountDownLatch(2)
        val measurerB = CountingMeasurer()
        val storeB = TestStore(listOf(track("b")))
        val runnerB = LoudnessAnalysisRunner(
            storeB,
            measurerB,
            { bWorkerRetried.countDown(); 1 },
            { 0L },
            claims = claims,
            waitPollMs = 10L,
        )

        val handleA = startInThread { runnerA.run { false } }
        try {
            assertTrue("A holds the slot", measurerA.started.await(2, TimeUnit.SECONDS))

            val handleB = startInThread { runnerB.run { stopped.get() } }
            assertTrue("B retried while A still holds the slot", bWorkerRetried.await(2, TimeUnit.SECONDS))
            assertTrue("B must still be running while A holds the slot", handleB.thread.isAlive)

            stopped.set(true)
            handleB.join(2000)
            assertFalse(handleB.thread.isAlive)
            assertEquals(RunOutcome.STOPPED, handleB.outcome.get())
            assertEquals(0, measurerB.callCount("b"))

            assertTrue("A still holds the slot when B stops", handleA.thread.isAlive)
        } finally {
            measurerA.release.countDown()
            handleA.join(2000)
            assertFalse("runner A did not return", handleA.thread.isAlive)
            assertEquals(RunOutcome.FINISHED, handleA.outcome.get())
        }
    }

    @Test(timeout = 10_000)
    fun waitingRunnerStillHonoursBudget() {
        val claims = AnalysisClaims()
        val measurerA = GatedMeasurer()
        val storeA = TestStore(listOf(track("a")))
        val runnerA = LoudnessAnalysisRunner(storeA, measurerA, { 1 }, { 0L }, claims = claims)

        val clock = AtomicLong(0)
        val bWorkerRetried = CountDownLatch(2)
        val measurerB = CountingMeasurer()
        val storeB = TestStore(listOf(track("b")))
        val runnerB = LoudnessAnalysisRunner(
            storeB,
            measurerB,
            { bWorkerRetried.countDown(); 1 },
            clock::get,
            budgetMs = 1_000L,
            claims = claims,
            waitPollMs = 10L,
        )

        val handleA = startInThread { runnerA.run { false } }
        try {
            assertTrue("A holds the slot", measurerA.started.await(2, TimeUnit.SECONDS))

            val handleB = startInThread { runnerB.run { false } }
            assertTrue("B retried while A still holds the slot", bWorkerRetried.await(2, TimeUnit.SECONDS))
            assertTrue("B must still be running while A holds the slot", handleB.thread.isAlive)

            clock.addAndGet(2_000L)
            handleB.join(2000)
            assertFalse(handleB.thread.isAlive)
            assertEquals(RunOutcome.MORE_PENDING, handleB.outcome.get())
            assertEquals(0, measurerB.callCount("b"))

            assertTrue("A still holds the slot when B exhausts its budget", handleA.thread.isAlive)
        } finally {
            measurerA.release.countDown()
            handleA.join(2000)
            assertFalse("runner A did not return", handleA.thread.isAlive)
            assertEquals(RunOutcome.FINISHED, handleA.outcome.get())
        }
    }

    @Test(timeout = 10_000)
    fun waitingRunnerSkipsTracksFinishedDuringItsWait() {
        val claims = AnalysisClaims()
        val measurerA = GatedMeasurer()
        val runnerA = LoudnessAnalysisRunner(
            TestStore(listOf(track("a"), track("b"))), measurerA, { 1 }, { 0L }, claims = claims,
        )
        val bWorkerCalls = AtomicInteger(0)
        val bRetrying = CountDownLatch(1)
        val releaseB = CountDownLatch(1)
        val measurerB = CountingMeasurer()
        val runnerB = LoudnessAnalysisRunner(
            TestStore(listOf(track("b"), track("c"))),
            measurerB,
            {
                if (bWorkerCalls.incrementAndGet() == 2) {
                    bRetrying.countDown()
                    check(releaseB.await(2, TimeUnit.SECONDS))
                }
                1
            },
            { 0L },
            claims = claims,
            waitPollMs = 10L,
        )

        val handleA = startInThread { runnerA.run { false } }
        var handleB: RunHandle? = null
        try {
            assertTrue("A holds the slot", measurerA.started.await(2, TimeUnit.SECONDS))
            handleB = startInThread { runnerB.run { false } }
            assertTrue("B is retrying", bRetrying.await(2, TimeUnit.SECONDS))
            measurerA.release.countDown()
            handleA.join(2000)
            assertEquals(RunOutcome.FINISHED, handleA.outcome.get())
            assertEquals(1, measurerA.callCount("b"))

            releaseB.countDown()
            handleB.join(2000)
            assertFalse(handleB.thread.isAlive)
            assertEquals(RunOutcome.FINISHED, handleB.outcome.get())
            assertEquals("B skips the track A finished while B waited", 0, measurerB.callCount("b"))
            assertEquals(1, measurerB.callCount("c"))

            // History belongs only to overlapping runs; a later pending snapshot can analyze b again.
            assertEquals(RunOutcome.FINISHED, runBlocking { runnerB.run { false } })
            assertEquals(1, measurerB.callCount("b"))
        } finally {
            measurerA.release.countDown()
            releaseB.countDown()
            handleA.join(2000)
            handleB?.join(2000)
        }
    }

    @Test(timeout = 10_000)
    fun abandonedTrackFromOtherRunnerIsNotCountedDone() {
        assertOtherRunnerTrackOutcome(completed = false)
    }

    @Test(timeout = 10_000)
    fun abandonedTrackNotYetReachedIsMeasuredBySameRun() {
        val claims = AnalysisClaims()
        val nStarted = CountDownLatch(1)
        val releaseN = CountDownLatch(1)
        val runnerN = LoudnessAnalysisRunner(
            TestStore(listOf(track("x"))),
            TrackMeasurer { _, _, _, _ ->
                nStarted.countDown()
                check(releaseN.await(3, TimeUnit.SECONDS))
                null
            },
            { 1 }, { 0L }, claims = claims,
        )
        val bRetried = CountDownLatch(2)
        val measurerB = CountingMeasurer()
        val storeB = TestStore(listOf(track("y"), track("x")))
        val runnerB = LoudnessAnalysisRunner(
            storeB, measurerB, { bRetried.countDown(); 1 }, { 0L }, claims = claims, waitPollMs = 10L,
        )

        val handleN = startInThread { runnerN.run { false } }
        var handleB: RunHandle? = null
        try {
            assertTrue("N holds x", nStarted.await(2, TimeUnit.SECONDS))
            handleB = startInThread { runnerB.run { false } }
            assertTrue("B is active but has not reached x", bRetried.await(2, TimeUnit.SECONDS))
            assertEquals(0, measurerB.callCount("y"))

            releaseN.countDown()
            handleN.join(2000)
            assertEquals(RunOutcome.MORE_PENDING, handleN.outcome.get())
            handleB.join(2000)
            assertFalse("B returned", handleB.thread.isAlive)
            assertEquals(RunOutcome.FINISHED, handleB.outcome.get())
            assertEquals("B measures the abandoned x exactly once", 1, measurerB.callCount("x"))
            assertEquals(1, measurerB.callCount("y"))
            assertEquals(2, storeB.results.size)
        } finally {
            releaseN.countDown()
            handleN.join(1000)
            handleB?.join(1000)
        }
    }

    @Test(timeout = 10_000)
    fun completedTrackFromOtherRunnerStillCountsDone() {
        assertOtherRunnerTrackOutcome(completed = true)
    }

    @Test(timeout = 10_000)
    fun cancelledTrackFromOtherRunnerIsNotCountedDone() {
        assertOtherRunnerTrackOutcome(completed = false, cancelledByException = true)
    }

    @Test(timeout = 10_000)
    fun nowRunHandOffAppendsBackgroundWorkUnlessFinished() {
        (listOf<RunOutcome?>(null) + RunOutcome.values().toList()).forEach { outcome ->
            val enqueuer = FakeEnqueuer()
            val scheduler = LoudnessAnalysisScheduler(enqueuer)

            assertEquals("hand-off decision for $outcome", outcome != RunOutcome.FINISHED, nowRunHandOff(outcome))
            if (nowRunHandOff(outcome)) scheduler.enqueueContinuation(0)

            if (outcome == RunOutcome.FINISHED) {
                assertTrue("finished work needs no continuation", enqueuer.calls.isEmpty())
            } else {
                val (name, policy, request) = enqueuer.calls.single()
                assertEquals(LoudnessAnalysisScheduler.WORK_NAME, name)
                assertEquals("hand-off appends after running background work", ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
                assertEquals(0L, request.workSpec.initialDelay)
            }
        }
    }

    private fun assertOtherRunnerTrackOutcome(completed: Boolean, cancelledByException: Boolean = false) {
        val claims = AnalysisClaims()
        val nStarted = CountDownLatch(1)
        val releaseN = CountDownLatch(1)
        val storeN = TestStore(listOf(track("x")))
        val runnerN = LoudnessAnalysisRunner(
            storeN,
            TrackMeasurer { track, _, _, _ ->
                nStarted.countDown()
                check(releaseN.await(3, TimeUnit.SECONDS))
                if (cancelledByException) throw CancellationException("now cancelled")
                if (completed) TrackLoudnessResult.Silent(track.trackId, track.fingerprint) else null
            },
            { 2 }, { 0L }, claims = claims,
        )
        val bStartedY = CountDownLatch(1)
        val releaseB = CountDownLatch(1)
        val measurerB = CountingMeasurer()
        val storeB = TestStore(listOf(track("x"), track("y")))
        val runnerB = LoudnessAnalysisRunner(
            storeB,
            TrackMeasurer { track, resume, onCheckpoint, isCancelled ->
                if (track.trackId == "y") {
                    bStartedY.countDown()
                    check(releaseB.await(3, TimeUnit.SECONDS))
                }
                measurerB.measure(track, resume, onCheckpoint, isCancelled)
            },
            { 2 }, { 0L }, claims = claims,
        )

        val handleN = startInThread { runnerN.run { false } }
        var handleB: RunHandle? = null
        try {
            assertTrue("N holds x", nStarted.await(2, TimeUnit.SECONDS))
            handleB = startInThread { runnerB.run { false } }
            assertTrue("B skipped x and started y", bStartedY.await(2, TimeUnit.SECONDS))
            assertEquals("B does not measure x", 0, measurerB.callCount("x"))

            releaseN.countDown()
            handleN.join(2000)
            assertFalse("N returned and released x", handleN.thread.isAlive)
            if (cancelledByException) {
                assertTrue("N propagates cancellation", handleN.error.get() is CancellationException)
            } else {
                assertEquals(if (completed) RunOutcome.FINISHED else RunOutcome.MORE_PENDING, handleN.outcome.get())
            }
            assertEquals(if (completed) 1 else 0, storeN.results.size)

            releaseB.countDown()
            handleB.join(2000)
            assertFalse("B returned", handleB.thread.isAlive)
            assertEquals(if (completed) RunOutcome.FINISHED else RunOutcome.MORE_PENDING, handleB.outcome.get())
            assertEquals("B never measures skipped x", 0, measurerB.callCount("x"))
            assertEquals(1, measurerB.callCount("y"))
            assertEquals(1, storeB.results.size)
            assertEquals("all claims released", 0, claims.size)
        } finally {
            releaseN.countDown()
            releaseB.countDown()
            handleN.join(1000)
            handleB?.join(1000)
        }
    }

    // ------------------------------------------------------------------
    // Helpers and fakes
    // ------------------------------------------------------------------

    private fun track(id: String): PendingTrack = PendingTrack(
        trackId = id,
        audioPath = "/audio/$id.flac",
        fingerprint = "fp-$id",
        format = "flac",
        codec = "flac",
        durationMs = 1_000L,
    )

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
    }

    private class RunHandle(val thread: Thread, val outcome: AtomicReference<RunOutcome?>, val error: AtomicReference<Throwable?>) {
        fun join(timeoutMs: Long) {
            thread.join(timeoutMs)
        }
    }

    private fun startInThread(block: suspend () -> RunOutcome): RunHandle {
        val outcome = AtomicReference<RunOutcome?>()
        val error = AtomicReference<Throwable?>()
        val thread = Thread({
            try {
                outcome.set(runBlocking { block() })
            } catch (t: Throwable) {
                error.set(t)
            }
        }, "runner")
        thread.isDaemon = true
        thread.start()
        return RunHandle(thread, outcome, error)
    }

    private class FakeEnqueuer : AnalysisWorkEnqueuer {
        val calls = CopyOnWriteArrayList<Triple<String, ExistingWorkPolicy, OneTimeWorkRequest>>()

        override fun enqueueUnique(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
            calls.add(Triple(name, policy, request))
        }
    }

    private class FakeCanceller {
        val names = CopyOnWriteArrayList<String>()
        fun cancel(name: String) {
            names.add(name)
        }
    }

    private class TestStore(
        private val pending: List<PendingTrack>,
    ) : AnalysisJobStore {
        val results = java.util.Collections.synchronizedList(mutableListOf<TrackLoudnessResult>())

        @Volatile
        var recomputeCalls = 0

        override suspend fun pendingTracks(): List<PendingTrack> = pending

        override suspend fun writeResult(result: TrackLoudnessResult) {
            results.add(result)
        }

        override suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray) = Unit

        override suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray? = null

        override suspend fun recomputeAlbums() {
            recomputeCalls++
        }

        override suspend fun progress(): AnalysisProgress = AnalysisProgress(results.size, pending.size)
    }

    /** Records calls without concurrency control; one call per measure. */
    private class CountingMeasurer : TrackMeasurer {
        private val callCounts = ConcurrentHashMap<String, AtomicInteger>()

        fun callCount(trackId: String): Int = callCounts[trackId]?.get() ?: 0

        override fun measure(
            track: PendingTrack,
            resume: ByteArray?,
            onCheckpoint: (ByteArray) -> Unit,
            isCancelled: () -> Boolean,
        ): TrackLoudnessResult? {
            callCounts.getOrPut(track.trackId) { AtomicInteger(0) }.incrementAndGet()
            return TrackLoudnessResult.Silent(track.trackId, track.fingerprint)
        }
    }

    /** Gates every measurement on one shared latch so two runners overlap. */
    private class GatedMeasurer : TrackMeasurer {
        private val callCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val inFlight = AtomicInteger(0)
        private val lock = Any()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)

        @Volatile
        var peakInFlight = 0

        fun callCount(trackId: String): Int = callCounts[trackId]?.get() ?: 0

        fun active(): Int = inFlight.get()

        override fun measure(
            track: PendingTrack,
            resume: ByteArray?,
            onCheckpoint: (ByteArray) -> Unit,
            isCancelled: () -> Boolean,
        ): TrackLoudnessResult? {
            callCounts.getOrPut(track.trackId) { AtomicInteger(0) }.incrementAndGet()
            val active = inFlight.incrementAndGet()
            synchronized(lock) { if (active > peakInFlight) peakInFlight = active }
            started.countDown()
            try {
                release.await(30, TimeUnit.SECONDS)
                return TrackLoudnessResult.Silent(track.trackId, track.fingerprint)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    /** Advances the clock by ten minutes per measurement, far past the background hard-stop. */
    private class ClockJumpMeasurer(private val clock: AtomicLong) : TrackMeasurer {
        private val callCounts = ConcurrentHashMap<String, AtomicInteger>()

        fun callCount(trackId: String): Int = callCounts[trackId]?.get() ?: 0

        override fun measure(
            track: PendingTrack,
            resume: ByteArray?,
            onCheckpoint: (ByteArray) -> Unit,
            isCancelled: () -> Boolean,
        ): TrackLoudnessResult? {
            callCounts.getOrPut(track.trackId) { AtomicInteger(0) }.incrementAndGet()
            clock.addAndGet(10 * 60_000L)
            return TrackLoudnessResult.Silent(track.trackId, track.fingerprint)
        }
    }

    /** t1 finishes immediately; t2 busy-polls [isCancelled] until the run is cancelled. */
    private class CancelAwareMeasurer(private val store: TestStore) : TrackMeasurer {
        val sawCancelled = AtomicBoolean(false)

        override fun measure(
            track: PendingTrack,
            resume: ByteArray?,
            onCheckpoint: (ByteArray) -> Unit,
            isCancelled: () -> Boolean,
        ): TrackLoudnessResult? {
            if (track.trackId == "t2") {
                while (!isCancelled()) Thread.sleep(1)
                sawCancelled.set(true)
                return null
            }
            return TrackLoudnessResult.Silent(track.trackId, track.fingerprint)
        }
    }
}
