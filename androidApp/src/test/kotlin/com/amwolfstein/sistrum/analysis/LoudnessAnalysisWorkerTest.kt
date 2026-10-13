package com.amwolfstein.sistrum.analysis

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
import kotlinx.coroutines.runBlocking
import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * T051e: JVM specification of the background loudness-analysis job. The runner and the scheduler are stubs for now,
 * so every test fails on an assertion; T051f implements the behaviour without changing these tests.
 */
class LoudnessAnalysisWorkerTest {

    @Test(timeout = 10_000)
    fun measuresOnlyPendingTracksOncePerRun() {
        val tracks = listOf(track("t1"), track("t2"))
        val store = FakeStore(tracks, 2)
        val measurer = FakeMeasurer(FakeClock(), store.events)

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer))
        assertEquals(1, store.pendingCalls)
        assertEquals(1, measurer.callCount("t1"))
        assertEquals(1, measurer.callCount("t2"))
        assertEquals(2, store.results.size)
    }

    @Test(timeout = 10_000)
    fun writesEachResultBeforeSlotStartsNextTrack() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val store = FakeStore(tracks, 3)
        val measurer = FakeMeasurer(FakeClock(), store.events)

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer, workerCount = { 1 }))
        val ordered = store.events.snapshot().filter { it.startsWith("measure:") || it.startsWith("write:") }
        assertEquals(listOf("measure:a", "write:a", "measure:b", "write:b", "measure:c", "write:c"), ordered)
    }

    @Test(timeout = 10_000)
    fun writesMeasuredSilentAndFailedResultsAsReturned() {
        val a = track("a")
        val b = track("b")
        val c = track("c")
        val store = FakeStore(listOf(a, b, c), 3)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val measuredA = measured(a)
        val silentB = silent(b)
        val failedC = failed(c)
        measurer.script(a.trackId).result = { measuredA }
        measurer.script(b.trackId).result = { silentB }
        measurer.script(c.trackId).result = { failedC }

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer, workerCount = { 1 }))
        assertEquals(listOf<TrackLoudnessResult>(measuredA, silentB, failedC), store.results)
    }

    @Test(timeout = 10_000)
    fun measurerExceptionIsWrittenAsFailedAndRunContinues() {
        val a = track("a")
        val b = track("b")
        val store = FakeStore(listOf(a, b), 2)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        measurer.script(a.trackId).failWith = RuntimeException("boom")

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer, workerCount = { 1 }))
        val expectedFailed = TrackLoudnessResult.Failed(a.trackId, a.fingerprint, "analyzer", "analyzer error: boom")
        assertEquals(listOf<TrackLoudnessResult>(expectedFailed, silent(b)), store.results)
    }

    @Test(timeout = 10_000)
    fun runsUpToWorkerCountTracksInParallel() {
        val tracks = (1..6).map { track("t$it") }
        val store = FakeStore(tracks, 6)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val gates = tracks.associate { it.trackId to CountDownLatch(1) }
        tracks.forEach { t -> measurer.script(t.trackId).apply { chunks = 1; gate = gates.getValue(t.trackId) } }
        measurer.startLatch = CountDownLatch(3)

        val run = startRun(LoudnessAnalysisRunner(store, measurer, { 3 }, clockOf(measurer)))
        assertTrue("three tracks held in flight", measurer.startLatch!!.await(5, TimeUnit.SECONDS))
        assertEquals("exactly workerCount in flight", 3, measurer.peakConcurrency)
        gates.values.forEach { it.countDown() }
        assertEquals(RunOutcome.FINISHED, run.join())
        assertEquals("never exceeded workerCount", 3, measurer.peakConcurrency)
        tracks.forEach { assertEquals(1, measurer.callCount(it.trackId)) }
        assertEquals("every track was written", 6, store.results.size)
    }

    @Test(timeout = 10_000)
    fun workerCountDropIsAppliedBeforeNextStart() {
        val tracks = (1..4).map { track("t$it") }
        val store = FakeStore(tracks, 4)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val gates = tracks.associate { it.trackId to CountDownLatch(1) }
        tracks.forEach { t -> measurer.script(t.trackId).apply { chunks = 1; gate = gates.getValue(t.trackId) } }
        measurer.startLatch = CountDownLatch(3)
        measurer.startedTracks["t4"] = CountDownLatch(1)

        val runner = LoudnessAnalysisRunner(
            store = store,
            measurer = measurer,
            workerCount = { if (measurer.finishedCount() == 0) 3 else 1 },
            clockMs = clockOf(measurer),
        )
        val run = startRun(runner)
        assertTrue("three tracks held in flight", measurer.startLatch!!.await(5, TimeUnit.SECONDS))
        assertEquals("peak before the drop", 3, measurer.peakConcurrency)

        gates.getValue("t1").countDown()
        awaitFinished(measurer, 1)
        assertEquals("t2 and t3 still in flight", 2, measurer.activeCount())
        gates.getValue("t2").countDown()
        gates.getValue("t3").countDown()

        assertTrue("t4 started", measurer.startedTracks.getValue("t4").await(5, TimeUnit.SECONDS))
        assertEquals("t4 started alone after the drop", 1, measurer.inFlightAtStart("t4"))
        gates.getValue("t4").countDown()
        assertEquals(RunOutcome.FINISHED, run.join())

        val startedAfterDrop = tracks
            .mapNotNull { t -> measurer.inFlightAtStart(t.trackId)?.takeIf { (measurer.finishedAtStart(t.trackId) ?: 0) >= 1 } }
        assertEquals("only one slot starts after the drop", 1, startedAfterDrop.size)
        assertEquals("peak after the drop", 1, startedAfterDrop.max())
    }

    @Test(timeout = 10_000)
    fun zeroWorkersPausesWithoutStarting() {
        val tracks = listOf(track("t1"), track("t2"))
        val store = FakeStore(tracks, 2)
        val measurer = FakeMeasurer(FakeClock(), store.events)

        assertEquals(RunOutcome.PAUSED, runOnce(store, measurer, workerCount = { 0 }))
        assertEquals(0, measurer.callCount("t1"))
        assertEquals(0, measurer.callCount("t2"))
        assertEquals(1, store.recomputeCalls)
    }

    @Test(timeout = 10_000)
    fun budgetStopsNewTracksAndReportsMorePending() {
        val tracks = listOf(track("t1"), track("t2"))
        val store = FakeStore(tracks, 2)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        tracks.forEach { measurer.script(it.trackId).chunkCostMs = 1_000 }

        assertEquals(RunOutcome.MORE_PENDING, runOnce(store, measurer, budgetMs = 500, hardStopMs = 100_000))
        assertEquals(1, measurer.callCount("t1"))
        assertEquals(0, measurer.callCount("t2"))
        assertEquals(listOf<TrackLoudnessResult>(silent(tracks[0])), store.results)
    }

    @Test(timeout = 10_000)
    fun budgetWithNothingLeftReportsFinished() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        measurer.script(t.trackId).chunkCostMs = 1_000

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer, budgetMs = 500, hardStopMs = 100_000))
        assertEquals(1, measurer.callCount(t.trackId))
        assertEquals(listOf<TrackLoudnessResult>(silent(t)), store.results)
    }

    @Test(timeout = 10_000)
    fun hardStopCancelsInFlightTrackAndSavesCheckpoint() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        measurer.script(t.trackId).apply {
            chunks = 5
            chunkCostMs = 1_000
            cancelCheckpoint = byteArrayOf(42)
        }

        assertEquals(
            "a track cut off by the hard stop is still pending, so the run asks for a continuation",
            RunOutcome.MORE_PENDING,
            runOnce(store, measurer, budgetMs = 100_000, hardStopMs = 2_500),
        )
        assertEquals(1, measurer.callCount(t.trackId))
        assertTrue("a cancelled measurement writes nothing", store.results.isEmpty())
        assertArrayEquals("the cancellation checkpoint is saved", byteArrayOf(42), store.lastCheckpoint(t.trackId, t.fingerprint))
    }

    @Test(timeout = 10_000)
    fun stoppedRunSavesCheckpointAndReportsStopped() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val stopped = AtomicBoolean(false)
        measurer.script(t.trackId).apply {
            chunks = 5
            cancelCheckpoint = byteArrayOf(5)
            onChunk = { _, i -> if (i == 2) stopped.set(true) }
        }

        val outcome = runOnce(store, measurer, stopped = stopped, budgetMs = 100_000, hardStopMs = 100_000)
        assertEquals(RunOutcome.STOPPED, outcome)
        assertTrue("a stopped measurement writes nothing", store.results.isEmpty())
        assertArrayEquals(byteArrayOf(5), store.lastCheckpoint(t.trackId, t.fingerprint))
    }

    @Test(timeout = 10_000)
    fun interruptedTrackResumesFromCheckpointNextRun() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val clock = FakeClock()
        val stopped = AtomicBoolean(false)
        val first = FakeMeasurer(clock, store.events)
        first.script(t.trackId).apply {
            chunks = 5
            cancelCheckpoint = byteArrayOf(7, 8, 9)
            onChunk = { _, i -> if (i == 2) stopped.set(true) }
        }

        assertEquals(RunOutcome.STOPPED, runOnce(store, first, stopped = stopped, budgetMs = 100_000, hardStopMs = 100_000))
        assertArrayEquals(byteArrayOf(7, 8, 9), store.lastCheckpoint(t.trackId, t.fingerprint))

        val second = FakeMeasurer(clock, store.events)
        second.script(t.trackId).result = { silent(it) }
        assertEquals(RunOutcome.FINISHED, runOnce(store, second))
        assertArrayEquals("second run resumes from the first run's checkpoint", byteArrayOf(7, 8, 9), second.resumeFor(t.trackId))
        assertEquals(listOf<TrackLoudnessResult>(silent(t)), store.results)
    }

    @Test(timeout = 10_000)
    fun everyCheckpointIsSaved() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        measurer.script(t.trackId).chunks = 5

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer, workerCount = { 1 }))
        assertEquals(1, measurer.callCount(t.trackId))
        val saved = store.checkpointsFor(t.trackId)
        assertTrue("at least one checkpoint per chunk, got ${saved.size}", saved.size >= 5)
        for (i in 0 until 5) {
            assertArrayEquals("checkpoint $i saved in order", byteArrayOf(i.toByte()), saved[i])
        }
    }

    @Test(timeout = 10_000)
    fun freshTrackGetsNoResume() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer))
        assertEquals(1, measurer.callCount(t.trackId))
        assertNull("a fresh track is measured without a resume state", measurer.resumeFor(t.trackId))
    }

    @Test(timeout = 10_000)
    fun albumsRecomputedOnceAfterLastWriteForEveryOutcome() {
        val s1 = FakeStore(listOf(track("f1")), 1)
        val m1 = FakeMeasurer(FakeClock(), s1.events)
        assertEquals(RunOutcome.FINISHED, runOnce(s1, m1))

        val s2 = FakeStore(listOf(track("m1"), track("m2")), 2)
        val m2 = FakeMeasurer(FakeClock(), s2.events)
        listOf("m1", "m2").forEach { m2.script(it).chunkCostMs = 1_000 }
        assertEquals(RunOutcome.MORE_PENDING, runOnce(s2, m2, budgetMs = 500, hardStopMs = 100_000))

        val s3 = FakeStore(listOf(track("p1")), 1)
        val m3 = FakeMeasurer(FakeClock(), s3.events)
        assertEquals(RunOutcome.PAUSED, runOnce(s3, m3, workerCount = { 0 }))

        val s4 = FakeStore(listOf(track("s1")), 1)
        val m4 = FakeMeasurer(FakeClock(), s4.events)
        assertEquals(RunOutcome.STOPPED, runOnce(s4, m4, stopped = AtomicBoolean(true)))

        val s5 = FakeStore(emptyList(), 0)
        val m5 = FakeMeasurer(FakeClock(), s5.events)
        assertEquals(RunOutcome.FINISHED, runOnce(s5, m5))

        listOf("finished" to s1, "more pending" to s2, "paused" to s3, "stopped" to s4, "empty" to s5)
            .forEach { (name, store) -> assertEquals("recompute called once for $name", 1, store.recomputeCalls) }

        val events = s1.events.snapshot()
        assertTrue(
            "albums are recomputed after the last write",
            events.indexOf("albums") > events.indexOfLast { it.startsWith("write:") },
        )
    }

    @Test(timeout = 10_000)
    fun progressReportedAtStartAndAfterEveryWrite() {
        val tracks = listOf(track("t1"), track("t2"), track("t3"))
        val store = FakeStore(tracks, 3)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val reported = CopyOnWriteArrayList<AnalysisProgress>()

        assertEquals(RunOutcome.FINISHED, runOnce(store, measurer, onProgress = { reported.add(it) }))
        assertEquals(
            listOf(AnalysisProgress(0, 3), AnalysisProgress(1, 3), AnalysisProgress(2, 3), AnalysisProgress(3, 3)),
            reported.toList(),
        )
    }

    @Test(timeout = 10_000)
    fun runReturnsOnlyAfterAllMeasurementsEnded() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val gate = CountDownLatch(1)
        measurer.script(t.trackId).apply { chunks = 1; this.gate = gate }
        measurer.startLatch = CountDownLatch(1)

        val run = startRun(LoudnessAnalysisRunner(store, measurer, { 1 }, clockOf(measurer)))
        assertTrue("measurement started", measurer.startLatch!!.await(5, TimeUnit.SECONDS))
        assertEquals("still running while the run is blocked", 1, measurer.activeCount())
        gate.countDown()
        assertEquals(RunOutcome.FINISHED, run.join())
        assertEquals("no measurement is still running when run returns", 0, measurer.activeCount())
    }

    @Test(timeout = 10_000)
    fun stopWaitsForInFlightMeasurementBeforeReturning() {
        val t = track("t1")
        val store = FakeStore(listOf(t), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        val gate = CountDownLatch(1)
        val stopped = AtomicBoolean(false)
        measurer.script(t.trackId).apply { chunks = 5; this.gate = gate }
        measurer.startLatch = CountDownLatch(1)

        val run = startRun(LoudnessAnalysisRunner(store, measurer, { 1 }, clockOf(measurer)), stopped)
        assertTrue("measurement started", measurer.startLatch!!.await(5, TimeUnit.SECONDS))
        stopped.set(true)
        gate.countDown()

        assertEquals(RunOutcome.STOPPED, run.join())
        assertEquals("no measurement is still running when run returns", 0, measurer.activeCount())
        assertTrue("a stopped measurement writes nothing", store.results.isEmpty())
    }

    @Test(timeout = 10_000)
    fun cancellationExceptionFromMeasurerIsNotWrittenAsFailed() {
        val a = track("a")
        val store = FakeStore(listOf(a), 1)
        val measurer = FakeMeasurer(FakeClock(), store.events)
        measurer.script(a.trackId).failWith = CancellationException("cancelled")

        try {
            runOnce(store, measurer, workerCount = { 1 })
            fail("run should propagate the measurer's CancellationException")
        } catch (expected: CancellationException) {
            // expected: cancellation is not a failure and must not be written
        }
        assertTrue("a cancelled measurement writes nothing", store.results.isEmpty())
    }

    @Test
    fun followUpPerOutcome() {
        assertEquals(RunFollowUp(false, 0), LoudnessAnalysisRunner.followUp(RunOutcome.FINISHED))
        assertEquals(RunFollowUp(true, 0), LoudnessAnalysisRunner.followUp(RunOutcome.MORE_PENDING))
        assertEquals(RunFollowUp(true, LoudnessAnalysisRunner.PAUSE_DELAY_MS), LoudnessAnalysisRunner.followUp(RunOutcome.PAUSED))
        assertEquals(RunFollowUp(false, 0), LoudnessAnalysisRunner.followUp(RunOutcome.STOPPED))
    }

    @Test
    fun scanCompletedEnqueuesUniqueKeepWork() {
        val enqueuer = FakeEnqueuer()
        val scheduler = LoudnessAnalysisScheduler(enqueuer)

        scheduler.onScanCompleted()

        assertEquals(1, enqueuer.calls.size)
        val (name, policy, request) = enqueuer.calls.single()
        assertEquals(LoudnessAnalysisScheduler.WORK_NAME, name)
        assertEquals(ExistingWorkPolicy.KEEP, policy)
        assertEquals(LoudnessAnalysisWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals("the scan request has no initial delay", 0L, request.workSpec.initialDelay)
        assertBackgroundConstraints(request)
    }

    @Test
    fun continuationAppendsWithDelay() {
        val enqueuer = FakeEnqueuer()
        val scheduler = LoudnessAnalysisScheduler(enqueuer)

        scheduler.enqueueContinuation(1234L)

        assertEquals(1, enqueuer.calls.size)
        val (name, policy, request) = enqueuer.calls.single()
        assertEquals(LoudnessAnalysisScheduler.WORK_NAME, name)
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
        assertEquals(1234L, request.workSpec.initialDelay)
        assertBackgroundConstraints(request)
    }

    @Test
    fun requestRequiresChargingBatteryAndStorage() {
        val request = LoudnessAnalysisScheduler.buildRequest()

        assertEquals(LoudnessAnalysisWorker::class.java.name, request.workSpec.workerClassName)
        assertBackgroundConstraints(request)
        assertEquals("any network", NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun progressFlowHoldsLastPublishedValue() {
        val scheduler = LoudnessAnalysisScheduler(FakeEnqueuer())

        assertNull(scheduler.progress.value)
        scheduler.publishProgress(AnalysisProgress(2, 5))
        assertEquals(AnalysisProgress(2, 5), scheduler.progress.value)
    }

    // ------------------------------------------------------------------
    // Helpers and fakes
    // ------------------------------------------------------------------

    private fun clockOf(measurer: FakeMeasurer): () -> Long = measurer::now

    private fun runOnce(
        store: FakeStore,
        measurer: FakeMeasurer,
        workerCount: () -> Int = { 1 },
        stopped: AtomicBoolean = AtomicBoolean(false),
        budgetMs: Long = LoudnessAnalysisRunner.BUDGET_MS,
        hardStopMs: Long = LoudnessAnalysisRunner.HARD_STOP_MS,
        onProgress: (AnalysisProgress) -> Unit = {},
    ): RunOutcome {
        val runner = LoudnessAnalysisRunner(store, measurer, workerCount, measurer::now, onProgress, budgetMs, hardStopMs)
        return runBlocking { runner.run { stopped.get() } }
    }

    private fun startRun(runner: LoudnessAnalysisRunner, stopped: AtomicBoolean = AtomicBoolean(false)): RunHandle {
        val outcome = AtomicReference<RunOutcome?>()
        val error = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                outcome.set(runBlocking { runner.run { stopped.get() } })
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        thread.isDaemon = true
        thread.start()
        return RunHandle(thread, outcome, error)
    }

    private fun assertBackgroundConstraints(request: OneTimeWorkRequest) {
        val constraints = request.workSpec.constraints
        assertTrue("requires charging", constraints.requiresCharging())
        assertTrue("requires battery not low", constraints.requiresBatteryNotLow())
        assertTrue("requires storage not low", constraints.requiresStorageNotLow())
    }

    private fun awaitFinished(measurer: FakeMeasurer, count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (measurer.finishedCount() < count && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("expected $count finished measurements, got ${measurer.finishedCount()}", measurer.finishedCount() >= count)
    }

    private class RunHandle(
        private val thread: Thread,
        private val outcome: AtomicReference<RunOutcome?>,
        private val error: AtomicReference<Throwable?>,
    ) {
        fun join(): RunOutcome {
            thread.join(TimeUnit.SECONDS.toMillis(8))
            error.get()?.let { throw AssertionError("run threw", it) }
            assertFalse("run did not return", thread.isAlive)
            return outcome.get()!!
        }
    }

    private class EventLog {
        private val lock = Any()
        private val events = mutableListOf<String>()

        fun record(event: String) {
            synchronized(lock) { events.add(event) }
        }

        fun snapshot(): List<String> = synchronized(lock) { events.toList() }
    }

    private class FakeClock {
        private val value = AtomicLong(0L)
        fun now(): Long = value.get()
        fun advance(ms: Long) { value.addAndGet(ms) }
    }

    private class FakeStore(
        var pending: List<PendingTrack> = emptyList(),
        var total: Int = pending.size,
    ) : AnalysisJobStore {
        private val lock = Any()
        val events = EventLog()
        val results = mutableListOf<TrackLoudnessResult>()
        private val checkpoints = mutableMapOf<String, ByteArray>()
        private val checkpointHistory = mutableMapOf<String, MutableList<ByteArray>>()
        var recomputeCalls = 0
        var pendingCalls = 0
        private var writes = 0

        override suspend fun pendingTracks(): List<PendingTrack> {
            synchronized(lock) {
                pendingCalls++
                events.record("pending")
            }
            return pending
        }

        override suspend fun writeResult(result: TrackLoudnessResult) {
            synchronized(lock) {
                results.add(result)
                writes++
                events.record("write:${result.trackId}")
            }
        }

        override suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray) {
            synchronized(lock) {
                checkpoints[key(trackId, fingerprint)] = state.copyOf()
                checkpointHistory.getOrPut(trackId) { mutableListOf() }.add(state.copyOf())
            }
        }

        override suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray? =
            synchronized(lock) { checkpoints[key(trackId, fingerprint)]?.copyOf() }

        override suspend fun recomputeAlbums() {
            synchronized(lock) {
                recomputeCalls++
                events.record("albums")
            }
        }

        override suspend fun progress(): AnalysisProgress = synchronized(lock) { AnalysisProgress(writes, total) }

        fun lastCheckpoint(trackId: String, fingerprint: String): ByteArray? =
            synchronized(lock) { checkpoints[key(trackId, fingerprint)]?.copyOf() }

        fun checkpointsFor(trackId: String): List<ByteArray> =
            synchronized(lock) { checkpointHistory[trackId]?.map { it.copyOf() } ?: emptyList() }

        private fun key(trackId: String, fingerprint: String) = "$trackId/$fingerprint"
    }

    private class FakeMeasurer(
        private val clockMs: FakeClock,
        private val events: EventLog,
    ) : TrackMeasurer {

        class Script(
            var chunks: Int = 1,
            var chunkCostMs: Long = 0,
            var checkpoint: (Int) -> ByteArray = { byteArrayOf(it.toByte()) },
            var cancelCheckpoint: ByteArray = byteArrayOf(0x7F),
            var result: (PendingTrack) -> TrackLoudnessResult? = { t -> TrackLoudnessResult.Silent(t.trackId, t.fingerprint) },
            var gate: CountDownLatch? = null,
            var onChunk: ((PendingTrack, Int) -> Unit)? = null,
            var failWith: Throwable? = null,
        )

        private val scripts = ConcurrentHashMap<String, Script>()
        private val callCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val resumes = java.util.Collections.synchronizedMap(HashMap<String, ByteArray?>())
        private val inFlight = AtomicInteger(0)
        private val finished = AtomicInteger(0)
        private val lock = Any()
        private val startedAtFinished = ConcurrentHashMap<String, Int>()
        private val startedAtInFlight = ConcurrentHashMap<String, Int>()

        val startedTracks = ConcurrentHashMap<String, CountDownLatch>()

        @Volatile
        var peakConcurrency = 0

        @Volatile
        var startLatch: CountDownLatch? = null

        fun now(): Long = clockMs.now()

        fun script(trackId: String): Script = scripts.getOrPut(trackId) { Script() }

        fun callCount(trackId: String): Int = callCounts[trackId]?.get() ?: 0

        fun resumeFor(trackId: String): ByteArray? = resumes[trackId]

        fun finishedCount(): Int = finished.get()

        fun activeCount(): Int = inFlight.get()

        fun inFlightAtStart(trackId: String): Int? = startedAtInFlight[trackId]

        fun finishedAtStart(trackId: String): Int? = startedAtFinished[trackId]

        override fun measure(
            track: PendingTrack,
            resume: ByteArray?,
            onCheckpoint: (ByteArray) -> Unit,
            isCancelled: () -> Boolean,
        ): TrackLoudnessResult? {
            val active = inFlight.incrementAndGet()
            synchronized(lock) { if (active > peakConcurrency) peakConcurrency = active }
            callCounts.getOrPut(track.trackId) { AtomicInteger(0) }.incrementAndGet()
            startedAtFinished[track.trackId] = finished.get()
            startedAtInFlight[track.trackId] = active
            resumes[track.trackId] = resume
            events.record("measure:${track.trackId}")
            startLatch?.countDown()
            startedTracks[track.trackId]?.countDown()
            try {
                val script = scripts.getOrPut(track.trackId) { Script() }
                script.gate?.await()
                for (i in 0 until script.chunks) {
                    if (isCancelled()) {
                        onCheckpoint(script.cancelCheckpoint.copyOf())
                        events.record("cancel:${track.trackId}")
                        return null
                    }
                    clockMs.advance(script.chunkCostMs)
                    onCheckpoint(script.checkpoint(i).copyOf())
                    script.onChunk?.invoke(track, i)
                }
                script.failWith?.let { throw it }
                events.record("done:${track.trackId}")
                return script.result(track)
            } finally {
                inFlight.decrementAndGet()
                finished.incrementAndGet()
            }
        }
    }

    private class FakeEnqueuer : AnalysisWorkEnqueuer {
        val calls = CopyOnWriteArrayList<Triple<String, ExistingWorkPolicy, OneTimeWorkRequest>>()

        override fun enqueueUnique(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
            calls.add(Triple(name, policy, request))
        }
    }

    private fun track(id: String): PendingTrack = PendingTrack(
        trackId = id,
        audioPath = "/audio/$id.flac",
        fingerprint = "fp-$id",
        format = "flac",
        codec = "flac",
        durationMs = 1_000L,
    )

    private fun measured(t: PendingTrack): TrackLoudnessResult =
        TrackLoudnessResult.Measured(t.trackId, t.fingerprint, -14.0, -1.0, -1.2, LoudnessHistogram())

    private fun silent(t: PendingTrack): TrackLoudnessResult = TrackLoudnessResult.Silent(t.trackId, t.fingerprint)

    private fun failed(t: PendingTrack): TrackLoudnessResult = TrackLoudnessResult.Failed(t.trackId, t.fingerprint, "decoder", "nope")
}
