package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization (FR-073): pins the coordinator's current session restore, audio focus, output
 * recovery, listening statistics and Last.fm behaviour as observed through a fake engine and fake
 * ports. Do not change expected values without an owner-approved spec reason.
 */
class PlaybackCoordinatorSessionCharacterizationTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
        artworkPath = "/art/$id.jpg",
    )

    private fun session(
        trackIds: List<String>,
        currentIndex: Int,
        positionMs: Long,
    ) = PlaybackSession(
        queue = PlaybackQueueSnapshot(
            originalTrackIds = trackIds,
            activeTrackIds = trackIds,
            currentIndex = currentIndex,
        ),
        positionMs = positionMs,
    )

    // ---------------------------------------------------------------- restore

    // 1. A restored session is prepared paused at the saved position and never starts a decoder.
    @Test
    fun `restore prepares the saved track paused at the saved position`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b", "c"), currentIndex = 1, positionMs = 42_000L), setOf("a", "b", "c"))

        val prepared = harness.engine.prepareCalls.single()
        assertEquals(item("b"), prepared.item)
        assertEquals(42_000L, prepared.startPositionMs)
        assertEquals(true, prepared.startPaused)
        assertFalse(harness.engine.calls.contains("play"))
        assertEquals(PlaybackState.Paused(item("b"), 42_000L, 180_000L), harness.flows.state.value)
        assertEquals("b", harness.flows.queueState.value.currentTrackId)
        assertEquals(listOf("b" to 42_000L), harness.scrobble.starts)
    }

    // 2. Ticking a restored session never promotes it to Playing.
    @Test
    fun `restore never auto-plays while ticking`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b"), currentIndex = 0, positionMs = 5_000L), setOf("a", "b"))
        repeat(3) { harness.tick() }

        assertEquals(PlaybackState.Paused(item("a"), 5_000L, 180_000L), harness.flows.state.value)
        assertFalse(harness.engine.playing)
        assertFalse(harness.engine.calls.contains("play"))
    }

    // 3. A saved position beyond the track duration is clamped in the published state.
    @Test
    fun `restore clamps a saved position beyond the duration`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a"), currentIndex = 0, positionMs = 200_000L), setOf("a"))

        assertEquals(200_000L, harness.engine.prepareCalls.single().startPositionMs)
        assertEquals(PlaybackState.Paused(item("a"), 180_000L, 180_000L), harness.flows.state.value)
        assertEquals(listOf("a" to 180_000L), harness.scrobble.starts)
    }

    // 4a. Tracks missing after the current one are dropped and the current track is unchanged.
    @Test
    fun `restore drops a missing track after the current one`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b", "c"), currentIndex = 1, positionMs = 1_000L), setOf("a", "b"))

        assertEquals(listOf("a", "b"), harness.queue.snapshot().activeTrackIds)
        assertEquals("b", harness.queue.snapshot().currentTrackId)
        assertEquals(PlaybackState.Paused(item("b"), 1_000L, 180_000L), harness.flows.state.value)
    }

    // 4b. A track missing before the current one is dropped from the queue; the retained selection
    // stays paused. The resulting current track is not pinned here.
    @Test
    fun `restore drops a missing track before the current one`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b", "c"), currentIndex = 2, positionMs = 1_000L), setOf("a", "c"))

        assertEquals(listOf("a", "c"), harness.queue.snapshot().activeTrackIds)
        assertTrue(harness.flows.state.value is PlaybackState.Paused)
        assertEquals(true, harness.engine.prepareCalls.single().startPaused)
    }

    // 5a. An empty available set clears the restored session.
    @Test
    fun `restore with an empty available set clears the session`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.restore(session(listOf("a", "b"), currentIndex = 0, positionMs = 1_000L), emptySet())

        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertTrue(harness.queue.snapshot().activeTrackIds.isEmpty())
        assertEquals(1, harness.sessionStore.cleared)
    }

    // 5b. An unresolvable current track clears the restored session.
    @Test
    fun `restore with an unresolvable current track clears the session`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.resolver.missing += "a"

        harness.restore(session(listOf("a", "b"), currentIndex = 0, positionMs = 1_000L), setOf("a", "b"))

        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertTrue(harness.queue.snapshot().activeTrackIds.isEmpty())
        assertEquals(1, harness.sessionStore.cleared)
    }

    // 5c. A failing prepare clears the restored session.
    @Test
    fun `restore with a failing prepare clears the session`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepare = IllegalStateException("boom")

        harness.restore(session(listOf("a", "b"), currentIndex = 0, positionMs = 1_000L), setOf("a", "b"))

        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertTrue(harness.queue.snapshot().activeTrackIds.isEmpty())
        assertEquals(1, harness.sessionStore.cleared)
    }

    // 6. A dispatch issued before markRestored() does not run until markRestored() is called.
    @Test
    fun `dispatch before markRestored has no effect until markRestored`() = runTest {
        val harness = PlaybackCoordinatorHarness(this, markRestoredOnInit = false)

        val job = harness.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.advance()

        assertTrue(harness.queue.snapshot().activeTrackIds.isEmpty())
        assertEquals(PlaybackState.Idle, harness.flows.state.value)

        harness.coordinator.markRestored()
        harness.advance()
        job.join()

        assertEquals(listOf("a"), harness.queue.snapshot().activeTrackIds)
        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), harness.flows.state.value)
    }

    // ---------------------------------------------------------------- focus

    // 7a. A permanent pause restores gain to 1.0 and a later focus gain never resumes it.
    @Test
    fun `permanent pause keeps its gain and does not resume on focus restore`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.send(PlaybackService.ActionPause)
        harness.send(PlaybackService.ActionRestoreFocus)

        assertEquals(PlaybackState.Paused(item("a"), 0L, 180_000L), harness.flows.state.value)
        assertEquals(1.0f, harness.engine.focusGains.last())
        assertEquals(1, harness.engines.created.size)
    }

    // 7b. A transient loss while Playing pauses and a later focus gain resumes the same engine.
    @Test
    fun `transient focus loss while playing resumes on focus restore`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.send(PlaybackService.ActionPauseForTransientFocusLoss)
        assertEquals(PlaybackState.Paused(item("a"), 0L, 180_000L), harness.flows.state.value)

        harness.send(PlaybackService.ActionRestoreFocus)

        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), harness.flows.state.value)
        assertEquals(1, harness.engines.created.size)
        assertEquals(1, harness.engine.calls.count { it == "play" })
    }

    // 7c. A transient loss while already Paused does not arm an automatic resume.
    @Test
    fun `transient focus loss while paused stays paused on focus restore`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPause)

        harness.send(PlaybackService.ActionPauseForTransientFocusLoss)
        harness.send(PlaybackService.ActionRestoreFocus)

        assertEquals(PlaybackState.Paused(item("a"), 0L, 180_000L), harness.flows.state.value)
        assertEquals(1, harness.engines.created.size)
    }

    // 7d. Duck lowers the live engine gain to 0.2 and restore raises it back to 1.0.
    @Test
    fun `duck lowers the gain and restore raises it`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.send(PlaybackService.ActionDuck)
        assertEquals(0.2f, harness.engine.focusGains.last())

        harness.send(PlaybackService.ActionRestoreFocus)
        assertEquals(1.0f, harness.engine.focusGains.last())
    }

    // 7e. A track prepared by the restore path inherits the ducked gain, because restoreCurrent does
    // not reset the focus gain before it creates the engine.
    @Test
    fun `a restored track prepared while ducked starts at the ducked gain`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionDuck)

        harness.restore(session(listOf("a"), currentIndex = 0, positionMs = 1_000L), setOf("a"))

        assertEquals(listOf(0.2f), harness.engine.focusGains)
    }

    // ---------------------------------------------------------------- output recovery

    // 8a. An output disconnect while Playing recreates the engine at the same position.
    @Test
    fun `output disconnect while playing recreates the engine at the same position`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        val first = harness.engine
        first.position = 5_000L
        first.emit(EngineEvent.OutputDisconnected)

        harness.tick()

        val recreated = harness.engine
        assertNotSame(first, recreated)
        assertTrue(first.closed)
        assertEquals(2, harness.engines.created.size)
        assertEquals(PlaybackState.Playing(item("a"), 5_000L, 180_000L), harness.flows.state.value)
        assertEquals(5_000L, recreated.position)
        assertEquals(false, recreated.prepareCalls.single().startPaused)
        assertEquals(5_000L, recreated.prepareCalls.single().startPositionMs)
    }

    // 8b. Recovery resumes the same listening attempt: the writes list is byte-for-byte identical
    // across the recovery tick, and the attempt only ends later at Stop.
    @Test
    fun `output disconnect recovery does not finish or restart listening`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.clock.now = 1_000L
        harness.clock.elapsed = 1_000L
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.clock.now = 21_000L
        harness.clock.elapsed = 21_000L
        val before = harness.listening.writes.toList()

        harness.engine.emit(EngineEvent.OutputDisconnected)
        harness.tick()

        assertEquals(before, harness.listening.writes)
        assertTrue(harness.listening.writes.none { it is ListeningWrite.Session })
        assertTrue(harness.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>().isEmpty())

        harness.clock.now = 31_000L
        harness.clock.elapsed = 31_000L
        harness.send(PlaybackService.ActionStop)

        val finished = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>()
            .single { it.value.trackId == "a" }
        assertEquals(PlaybackEndReason.STOPPED, finished.value.endReason)
        assertEquals(1_000L, finished.value.startedAt)
        assertEquals(31_000L, finished.value.endedAt)
        assertEquals(30, finished.value.listenedSeconds)
    }

    // 9. A pause after a disconnect leaves a pending recovery that resumes by recreating the engine.
    @Test
    fun `output disconnect then pause resumes by recreating the engine at the paused position`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        val first = harness.engine
        first.position = 5_000L
        first.emit(EngineEvent.OutputDisconnected)

        harness.send(PlaybackService.ActionPause)
        assertEquals(PlaybackState.Paused(item("a"), 5_000L, 180_000L), harness.flows.state.value)
        assertEquals(1, harness.engines.created.size)

        harness.send(PlaybackService.ActionResume)

        assertTrue(first.closed)
        assertEquals(2, harness.engines.created.size)
        assertEquals(PlaybackState.Playing(item("a"), 5_000L, 180_000L), harness.flows.state.value)
        assertEquals(5_000L, harness.engine.position)
    }

    // ---------------------------------------------------------------- Last.fm

    // 10a. Every track start reports a scrobble start.
    @Test
    fun `scrobble starts playback when a track is played`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        assertEquals(listOf("a" to 0L), harness.scrobble.starts)
    }

    // 10b. A native gapless advance reports a scrobble start for the incoming track.
    @Test
    fun `scrobble starts playback on a native gapless transition`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        harness.engine.emit(EngineEvent.GaplessAdvanced(item("b")))
        harness.tick()

        assertEquals(listOf("a" to 0L, "b" to 0L), harness.scrobble.starts)
    }

    // 10c. Seek reports the clamped target.
    @Test
    fun `seek reports the clamped target to scrobble`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.send(PlaybackService.ActionSeek, positionMs = 200_000L)

        assertEquals(listOf(180_000L), harness.scrobble.seeks)
        assertEquals(listOf(180_000L), harness.engine.seeks)
        assertEquals(PlaybackState.Playing(item("a"), 180_000L, 180_000L), harness.flows.state.value)
    }

    // 10e. Progress is reported once per tick only when the engine position changed.
    @Test
    fun `scrobble reports playback once per tick with a changed position`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        harness.tick()
        assertEquals(0, harness.scrobble.reports.size)

        harness.engine.position = 10_000L
        harness.tick()
        assertEquals(listOf(Triple(item("a"), 10_000L, 180_000L)), harness.scrobble.reports)

        harness.tick()
        assertEquals(1, harness.scrobble.reports.size)

        harness.engine.position = 20_000L
        harness.tick()
        assertEquals(2, harness.scrobble.reports.size)
        assertEquals(20_000L, harness.scrobble.reports.last().second)
    }

    // ---------------------------------------------------------------- listening statistics

    // 11a. A manual next finishes the previous attempt as SKIPPED.
    @Test
    fun `manual next finishes the previous attempt as skipped`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.clock.now = 1_000L
        harness.clock.elapsed = 1_000L
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.clock.now = 5_000L
        harness.clock.elapsed = 5_000L

        harness.send(PlaybackService.ActionNext)

        val finished = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>()
            .single { it.value.trackId == "a" }
        assertEquals(PlaybackEndReason.SKIPPED, finished.value.endReason)
        assertEquals("test-device", finished.value.sourceDeviceId)
        assertEquals(1_000L, finished.value.startedAt)
        assertEquals(5_000L, finished.value.endedAt)
        assertEquals(4, finished.value.listenedSeconds)
        assertEquals(0L, finished.value.startPositionMs)
    }

    // 11b. A natural end finishes the previous attempt as COMPLETED.
    @Test
    fun `natural end finishes the previous attempt as completed`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.clock.now = 1_000L
        harness.clock.elapsed = 1_000L
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        harness.clock.now = 4_000L
        harness.clock.elapsed = 4_000L
        harness.engine.emit(EngineEvent.Ended)

        harness.tick()

        val finished = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>()
            .single { it.value.trackId == "a" }
        assertEquals(PlaybackEndReason.COMPLETED, finished.value.endReason)
        assertEquals(4_000L, finished.value.endedAt)
        assertEquals(3, finished.value.listenedSeconds)
    }

    // 11c. Stop finishes the attempt as STOPPED.
    @Test
    fun `stop finishes the attempt as stopped`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.clock.now = 1_000L
        harness.clock.elapsed = 1_000L
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.clock.now = 3_000L
        harness.clock.elapsed = 3_000L

        harness.send(PlaybackService.ActionStop)

        val finished = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>().single()
        assertEquals(PlaybackEndReason.STOPPED, finished.value.endReason)
        assertEquals(3_000L, finished.value.endedAt)
        assertEquals(2, finished.value.listenedSeconds)
    }
}
