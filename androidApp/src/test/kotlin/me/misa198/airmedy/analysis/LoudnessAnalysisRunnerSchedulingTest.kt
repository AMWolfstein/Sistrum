package me.misa198.airmedy.analysis

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [LoudnessAnalysisRunner] slot scheduling (no head-of-line blocking) and pause semantics. */
class LoudnessAnalysisRunnerSchedulingTest {

    @Test(timeout = 10_000)
    fun slowTrackDoesNotBlockOtherSlots() {
        val tracks = listOf(track("slow"), track("a"), track("b"), track("c"))
        val store = FakeStore(tracks)
        val measurer = FakeMeasurer()
        val slowGate = CountDownLatch(1)
        measurer.gates["slow"] = slowGate

        val runner = LoudnessAnalysisRunner(store, measurer, { 2 }, { 0L })
        val outcomeRef = AtomicReference<RunOutcome?>()
        val thread = Thread({ outcomeRef.set(runBlocking { runner.run { false } }) }, "sched-run")
        thread.isDaemon = true
        thread.start()

        await { store.written.size >= 3 }
        assertEquals("a, b and c are measured and written while slow is still held", 3, store.written.size)
        assertEquals("slow is still in flight", 1L, slowGate.count)

        slowGate.countDown()
        thread.join(10_000)
        assertTrue("run returned", !thread.isAlive)
        assertEquals(RunOutcome.FINISHED, outcomeRef.get())
        assertEquals(4, store.written.size)
    }

    @Test(timeout = 10_000)
    fun pauseClearsWhenWorkersReturn() {
        val tracks = listOf(track("t1"), track("t2"))
        val store = FakeStore(tracks)
        val measurer = FakeMeasurer()
        val calls = AtomicInteger(0)

        val runner = LoudnessAnalysisRunner(store, measurer, { if (calls.incrementAndGet() == 2) 0 else 1 }, { 0L })
        assertEquals(RunOutcome.FINISHED, runBlocking { runner.run { false } })
        assertEquals(2, store.written.size)
    }

    @Test(timeout = 10_000)
    fun alwaysZeroWorkersPauses() {
        val tracks = listOf(track("t1"), track("t2"))
        val store = FakeStore(tracks)
        val measurer = FakeMeasurer()

        val runner = LoudnessAnalysisRunner(store, measurer, { 0 }, { 0L })
        assertEquals(RunOutcome.PAUSED, runBlocking { runner.run { false } })
        assertEquals(0, store.written.size)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
    }

    private fun track(id: String) = PendingTrack(
        trackId = id,
        audioPath = "/audio/$id.flac",
        fingerprint = "fp-$id",
        format = "flac",
        codec = "flac",
        durationMs = 1_000L,
    )

    private class FakeStore(private val pending: List<PendingTrack>) : AnalysisJobStore {
        val written = mutableListOf<TrackLoudnessResult>()
        private val lock = Any()

        override suspend fun pendingTracks(): List<PendingTrack> = pending

        override suspend fun writeResult(result: TrackLoudnessResult) {
            synchronized(lock) { written.add(result) }
        }

        override suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray) = Unit

        override suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray? = null

        override suspend fun recomputeAlbums() = Unit

        override suspend fun progress(): AnalysisProgress =
            synchronized(lock) { AnalysisProgress(written.size, pending.size) }
    }

    private class FakeMeasurer : TrackMeasurer {
        val gates = java.util.concurrent.ConcurrentHashMap<String, CountDownLatch>()

        override fun measure(
            track: PendingTrack,
            resume: ByteArray?,
            onCheckpoint: (ByteArray) -> Unit,
            isCancelled: () -> Boolean,
        ): TrackLoudnessResult? {
            gates[track.trackId]?.await()
            return TrackLoudnessResult.Silent(track.trackId, track.fingerprint)
        }
    }
}
