package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.mood.MoodRadioBatchSize
import me.misa198.airmedy.mood.MoodRadioTrack
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization (FR-073): pins the coordinator's current queue/transport behaviour as observed
 * through a fake engine and fake ports. Do not change expected values without an owner-approved
 * spec reason.
 */
class PlaybackCoordinatorQueueCharacterizationTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
        artworkPath = "/art/$id.jpg",
    )

    private fun moodCandidate(id: String) = MoodRadioTrack(
        id = id,
        albumId = "album-$id",
        primaryArtistId = "artist-$id",
        energy = 0.5,
        danceability = 0.5,
        brightness = 0.5,
        tempo = 120.0,
    )

    // 1. Play prepares the selected track, preloads its successor and publishes transport.
    @Test
    fun `play prepares the selected track and preloads its successor`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        var observedDuringPrepare: PlaybackState? = null
        harness.engines.onPrepare = { observedDuringPrepare = harness.flows.state.value }

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 1)

        assertEquals("b", harness.engine.preparedItem?.trackId)
        assertEquals("Title b", harness.engine.preparedItem?.title)
        assertEquals(false, harness.engine.prepareCalls.single().startPaused)
        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("b", playing.item.trackId)
        assertEquals("b", harness.flows.queueState.value.currentTrackId)
        assertEquals("c", harness.engine.preloadedItem?.trackId)
        assertEquals(
            listOf(TransportState.Buffering, TransportState.Playing, TransportState.Playing),
            harness.nowPlaying.transportStates,
        )
        assertEquals(
            listOf("b" to TransportState.Buffering, "b" to TransportState.Playing),
            harness.nowPlaying.published.map { it.first.trackId to it.second },
        )
        assertEquals(PlaybackState.Preparing(item("b")), observedDuringPrepare)
    }

    // 2. Preparing -> Playing -> Paused.
    @Test
    fun `pause moves from preparing through playing to paused at the engine position`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        val states = mutableListOf<PlaybackState>()
        harness.engines.onPrepare = { states += harness.flows.state.value }

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        states += harness.flows.state.value
        harness.engine.position = 42_000L
        harness.send(PlaybackService.ActionPause)
        states += harness.flows.state.value

        assertEquals(
            listOf(
                PlaybackState.Preparing(item("a")),
                PlaybackState.Playing(item("a"), 0L, 180_000L),
                PlaybackState.Paused(item("a"), 42_000L, 180_000L),
            ),
            states,
        )
    }

    // 3. Resume keeps the same engine.
    @Test
    fun `resume plays the same engine without preparing again`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        harness.send(PlaybackService.ActionPause)
        harness.send(PlaybackService.ActionResume)

        assertEquals(1, harness.engines.created.size)
        assertEquals(1, harness.engine.prepareCalls.size)
        assertEquals("play", harness.engine.calls.last())
        assertEquals(PlaybackState.Playing(item("a"), 0L, 180_000L), harness.flows.state.value)
    }

    // 4. Stop releases engine, focus and notification.
    @Test
    fun `stop releases the engine focus and notification`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        val engine = harness.engine

        harness.send(PlaybackService.ActionStop)

        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertTrue(engine.closed)
        assertEquals(1, harness.focus.abandonments)
        assertEquals(TransportState.Stopped, harness.nowPlaying.transportStates.last())
        assertEquals(1, harness.nowPlaying.stoppedForeground)
        assertEquals(1, harness.nowPlaying.deactivated)
    }

    // 5. Next / previous while playing.
    @Test
    fun `next advances to the following track while playing`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)
        val first = harness.engine

        harness.send(PlaybackService.ActionNext)

        assertEquals("b", harness.engine.preparedItem?.trackId)
        assertEquals("b", harness.flows.queueState.value.currentTrackId)
        assertTrue(harness.flows.state.value is PlaybackState.Playing)
        assertEquals(2, harness.engines.created.size)
        assertTrue(first.closed)
    }

    @Test
    fun `previous past the restart threshold seeks the current track to the start`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)
        harness.send(PlaybackService.ActionNext)
        harness.engine.position = 4_000L

        harness.send(PlaybackService.ActionPrevious)

        assertEquals(listOf(0L), harness.engine.seeks)
        assertEquals("b", harness.flows.queueState.value.currentTrackId)
        assertEquals(0L, (harness.flows.state.value as PlaybackState.Playing).positionMs)
    }

    @Test
    fun `previous at the restart threshold goes to the previous track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)
        harness.send(PlaybackService.ActionNext)
        harness.engine.position = 3_000L

        harness.send(PlaybackService.ActionPrevious)

        assertEquals("a", harness.engine.preparedItem?.trackId)
        assertEquals("a", harness.flows.queueState.value.currentTrackId)
    }

    // 6. Next / previous while paused stay paused.
    @Test
    fun `next while paused keeps the new track paused`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)
        harness.send(PlaybackService.ActionPause)

        harness.send(PlaybackService.ActionNext)

        val paused = harness.flows.state.value as PlaybackState.Paused
        assertEquals("b", paused.item.trackId)
        assertEquals(true, harness.engine.prepareCalls.last().startPaused)
    }

    @Test
    fun `previous while paused keeps the previous track paused`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 1)
        harness.send(PlaybackService.ActionPause)

        harness.send(PlaybackService.ActionPrevious)

        val paused = harness.flows.state.value as PlaybackState.Paused
        assertEquals("a", paused.item.trackId)
        assertEquals(true, harness.engine.prepareCalls.last().startPaused)
    }

    // 7. Shuffle keeps the current track and is reversible.
    @Test
    fun `enabling shuffle keeps the current track and disabling restores the original order`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionSetShuffle, enabled = true)
        val shuffled = harness.queue.snapshot()
        assertEquals(listOf("a", "b", "c"), shuffled.activeTrackIds.sorted())
        assertEquals("a", shuffled.currentTrackId)
        assertTrue(shuffled.shuffle)
        assertEquals(1, harness.engines.created.size)

        harness.send(PlaybackService.ActionSetShuffle, enabled = false)
        val restored = harness.queue.snapshot()
        assertEquals(listOf("a", "b", "c"), restored.activeTrackIds)
        assertEquals("a", restored.currentTrackId)
        assertFalse(restored.shuffle)
    }

    // 8. Repeat Off / One / All at the natural end.
    @Test
    fun `repeat one replays the same track at the natural end`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 2)
        harness.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.One)
        val first = harness.engine

        first.emit(EngineEvent.Ended)
        harness.tick()

        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("c", playing.item.trackId)
        assertEquals(2, harness.engines.created.size)
        assertTrue(first.closed)
        val replay = harness.engine.prepareCalls.single()
        assertEquals("c", replay.item.trackId)
        assertEquals(0L, replay.startPositionMs)
        assertFalse(replay.startPaused)
    }

    @Test
    fun `repeat all wraps to the first track at the natural end of the last track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 2)
        harness.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.All)

        harness.engine.emit(EngineEvent.Ended)
        harness.tick()

        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("a", playing.item.trackId)
        assertEquals("a", harness.engine.preparedItem?.trackId)
    }

    @Test
    fun `repeat off stops paused at the completed final track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 2)
        val engine = harness.engine

        engine.emit(EngineEvent.Ended)
        harness.tick()

        val paused = harness.flows.state.value as PlaybackState.Paused
        assertEquals("c", paused.item.trackId)
        assertEquals(180_000L, paused.positionMs)
        assertEquals(180_000L, paused.durationMs)
        assertTrue(engine.closed)
    }

    // 9. Manual exhaustion.
    @Test
    fun `manual next on the last track with repeat off pauses at its start`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 2)

        harness.send(PlaybackService.ActionNext)

        val paused = harness.flows.state.value as PlaybackState.Paused
        assertEquals("c", paused.item.trackId)
        assertEquals(0L, paused.positionMs)
        assertEquals(180_000L, paused.durationMs)
    }

    // 10. Resume after natural end restarts the queue.
    @Test
    fun `resume after a natural end restarts from the first track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 2)
        harness.engine.emit(EngineEvent.Ended)
        harness.tick()
        assertTrue(harness.flows.state.value is PlaybackState.Paused)

        harness.send(PlaybackService.ActionResume)

        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("a", playing.item.trackId)
        assertEquals("a", harness.flows.queueState.value.currentTrackId)
        assertEquals("a", harness.engine.preparedItem?.trackId)
    }

    // 11. Queue edits.
    @Test
    fun `play next inserts after the current track and preloads the new successor`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionPlayNext, trackIds = listOf("x"))

        assertEquals(listOf("a", "x", "b", "c"), harness.queue.snapshot().activeTrackIds)
        assertEquals("a", harness.queue.snapshot().currentTrackId)
        assertEquals("x", harness.engine.preloadedItem?.trackId)
    }

    @Test
    fun `append adds tracks at the end without changing the current track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionAppend, trackIds = listOf("y"))

        assertEquals(listOf("a", "b", "c", "y"), harness.queue.snapshot().activeTrackIds)
        assertEquals("a", harness.queue.snapshot().currentTrackId)
        assertEquals("b", harness.engine.preloadedItem?.trackId)
    }

    @Test
    fun `removing a non-current track keeps playback and the current track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionRemove, trackIds = listOf("b"))

        assertEquals(listOf("a", "c"), harness.queue.snapshot().activeTrackIds)
        assertEquals("a", harness.queue.snapshot().currentTrackId)
        assertEquals("a", (harness.flows.state.value as PlaybackState.Playing).item.trackId)
        assertEquals("c", harness.engine.preloadedItem?.trackId)
    }

    @Test
    fun `removing the current track plays the following track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionRemove, trackIds = listOf("a"))

        assertEquals(listOf("b", "c"), harness.queue.snapshot().activeTrackIds)
        assertEquals("b", harness.queue.snapshot().currentTrackId)
        assertEquals("b", (harness.flows.state.value as PlaybackState.Playing).item.trackId)
        assertEquals("c", harness.engine.preloadedItem?.trackId)
    }

    @Test
    fun `reorder applies the permutation and keeps the current track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionReorder, trackIds = listOf("a", "c", "b"))

        assertEquals(listOf("a", "c", "b"), harness.queue.snapshot().activeTrackIds)
        assertEquals("a", harness.queue.snapshot().currentTrackId)
        assertEquals("c", harness.engine.preloadedItem?.trackId)
    }

    @Test
    fun `select jumps to the chosen track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)

        harness.send(PlaybackService.ActionSelect, trackIds = listOf("c"))

        assertEquals("c", harness.queue.snapshot().currentTrackId)
        assertEquals("c", (harness.flows.state.value as PlaybackState.Playing).item.trackId)
        assertEquals("c", harness.engine.preparedItem?.trackId)
    }

    @Test
    fun `clear queue stops playback and empties the queue`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"), startIndex = 0)
        val engine = harness.engine

        harness.send(PlaybackService.ActionClearQueue)

        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertTrue(engine.closed)
        assertTrue(harness.flows.queueState.value.activeTrackIds.isEmpty())
    }

    // 12. Mood Radio machinery (engine-neutral).
    @Test
    fun `mood radio start seeds the queue with the seed and a batch of candidates`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        val candidates = (1..20).map { moodCandidate("t$it") }
        harness.library.moodTracks = listOf(moodCandidate("s")) + candidates

        harness.send(PlaybackService.ActionStartMoodRadio, trackIds = listOf("s"))

        val snapshot = harness.queue.snapshot()
        assertEquals("s", snapshot.activeTrackIds.first())
        assertEquals(1 + MoodRadioBatchSize, snapshot.activeTrackIds.size)
        val candidateIds = candidates.map { it.id }.toSet()
        assertTrue(snapshot.activeTrackIds.drop(1).all { it in candidateIds })
        assertTrue(harness.flows.moodRadioActive.value)
        assertEquals("s", harness.engine.preparedItem?.trackId)
    }

    @Test
    fun `mood radio start on the current seed replaces the queue around it without re-preparing`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.library.moodTracks = listOf(moodCandidate("s")) + (1..20).map { moodCandidate("t$it") }
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("s", "other"))
        assertEquals("s", harness.queue.snapshot().currentTrackId)
        val enginesBefore = harness.engines.created.size

        harness.send(PlaybackService.ActionStartMoodRadio, trackIds = listOf("s"))

        assertEquals("s", harness.queue.snapshot().currentTrackId)
        assertEquals(1 + MoodRadioBatchSize, harness.queue.snapshot().activeTrackIds.size)
        assertEquals(enginesBefore, harness.engines.created.size)
        assertTrue(harness.flows.moodRadioActive.value)
    }

    @Test
    fun `mood radio refills candidates when fewer than three remain after the current track`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.library.moodTracks = listOf(moodCandidate("s")) + (1..39).map { moodCandidate("t$it") }
        harness.send(PlaybackService.ActionStartMoodRadio, trackIds = listOf("s"))
        val initial = harness.queue.snapshot()
        assertEquals(1 + MoodRadioBatchSize, initial.activeTrackIds.size)
        val queued = initial.activeTrackIds.toSet()

        harness.send(PlaybackService.ActionSelect, trackIds = listOf(initial.activeTrackIds.last()))
        assertEquals(0, harness.queue.snapshot().activeTrackIds.size - harness.queue.snapshot().currentIndex - 1)

        harness.tick()

        val refilled = harness.queue.snapshot()
        assertTrue(refilled.activeTrackIds.size > initial.activeTrackIds.size)
        assertEquals(refilled.activeTrackIds.toSet().size, refilled.activeTrackIds.size)
        val appended = refilled.activeTrackIds.drop(initial.activeTrackIds.size)
        assertTrue(appended.isNotEmpty())
        assertTrue(appended.all { it !in queued })
    }

    @Test
    fun `every stopping action clears the mood radio flag`() = runTest {
        val stopping = listOf(
            PlaybackService.ActionPlay to listOf("a", "b"),
            PlaybackService.ActionShuffle to listOf("a", "b"),
            PlaybackService.ActionStop to emptyList(),
            PlaybackService.ActionClearQueue to emptyList(),
            PlaybackService.ActionPlayNext to listOf("x"),
            PlaybackService.ActionAppend to listOf("x"),
            PlaybackService.ActionRemove to listOf("a"),
            PlaybackService.ActionReorder to listOf("b", "a"),
        )
        stopping.forEach { (action, ids) ->
            val harness = PlaybackCoordinatorHarness(this)
            harness.library.moodTracks = listOf(moodCandidate("s")) + (1..20).map { moodCandidate("t$it") }
            harness.send(PlaybackService.ActionStartMoodRadio, trackIds = listOf("s"))
            assertTrue("$action should activate mood radio", harness.flows.moodRadioActive.value)

            harness.send(action, trackIds = ids)

            assertFalse("$action should clear mood radio", harness.flows.moodRadioActive.value)
        }
    }

    @Test
    fun `mood radio start is ignored when library analysis is disabled`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.library.analysisEnabled = false
        harness.library.moodTracks = listOf(moodCandidate("s")) + (1..20).map { moodCandidate("t$it") }

        harness.send(PlaybackService.ActionStartMoodRadio, trackIds = listOf("s"))

        assertTrue(harness.queue.snapshot().activeTrackIds.isEmpty())
        assertEquals(PlaybackState.Idle, harness.flows.state.value)
        assertFalse(harness.flows.moodRadioActive.value)
    }
}
