package me.misa198.airmedy.player

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization (FR-073): pins the coordinator's current crossfade orchestration as observed
 * through a fake engine and fake ports. Do not change expected values without an owner-approved
 * spec reason.
 */
class PlaybackCoordinatorCrossfadeCharacterizationTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
        artworkPath = "/art/$id.jpg",
    )

    /**
     * Plays [tracks], enables crossfade with [seconds], moves the engine to [positionMs] and runs
     * one [tick]. When [emitTransition] is true a successful fade start also promotes the incoming
     * item the way the native engine reports [EngineEvent.TransitionStarted].
     */
    private suspend fun startCrossfade(
        harness: PlaybackCoordinatorHarness,
        seconds: Int = 5,
        positionMs: Long = 175_000L,
        tracks: List<String> = listOf("a", "b", "c"),
        emitTransition: Boolean = true,
    ) {
        harness.engines.emitTransitionOnCrossfade = emitTransition
        harness.send(PlaybackService.ActionPlay, trackIds = tracks)
        harness.coordinator.onPlaybackSettings(seconds, true)
        harness.engine.position = positionMs
        harness.tick()
    }

    // 1. A fade starts only when the remaining time falls inside the configured window.
    @Test
    fun `crossfade starts only on automatic advance inside the window`() = runTest {
        val inside = PlaybackCoordinatorHarness(this)
        startCrossfade(inside, seconds = 5, positionMs = 175_000L, emitTransition = false)
        assertEquals(listOf(5_000L), inside.engine.crossfades)

        val outside = PlaybackCoordinatorHarness(this)
        startCrossfade(outside, seconds = 5, positionMs = 174_999L, emitTransition = false)
        assertTrue(outside.engine.crossfades.isEmpty())
    }

    // 2. Manual navigation never starts a crossfade, even inside the window.
    @Test
    fun `manual next during the window does not start a crossfade`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        harness.coordinator.onPlaybackSettings(5, true)
        harness.engine.position = 175_000L

        harness.send(PlaybackService.ActionNext)

        // The manual next prepared a fresh engine for "b" at position 0, so these
        // ticks sit outside the window and must not legitimately start a fade.
        harness.engine.position = 0L
        harness.tick()
        harness.tick()

        assertTrue(harness.engine.crossfades.isEmpty())
        assertNull(harness.flows.artworkCrossfade.value)
        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("b", playing.item.trackId)
        assertEquals("b", harness.engine.prepareCalls.last().item.trackId)
        assertEquals(0L, harness.engine.prepareCalls.last().startPositionMs)
        assertEquals("b", harness.flows.queueState.value.currentTrackId)
    }

    @Test
    fun `manual select during the window does not start a crossfade`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        harness.coordinator.onPlaybackSettings(5, true)
        harness.engine.position = 175_000L

        harness.send(PlaybackService.ActionSelect, trackIds = listOf("c"))

        // The manual select prepared a fresh engine for "c" at position 0, so these
        // ticks sit outside the window and must not legitimately start a fade.
        harness.engine.position = 0L
        harness.tick()
        harness.tick()

        assertTrue(harness.engine.crossfades.isEmpty())
        assertNull(harness.flows.artworkCrossfade.value)
        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("c", playing.item.trackId)
        assertEquals("c", harness.engine.prepareCalls.last().item.trackId)
        assertEquals(0L, harness.engine.prepareCalls.last().startPositionMs)
        assertEquals("c", harness.flows.queueState.value.currentTrackId)
    }

    // 1b. The effective fade duration shrinks to the remaining track time.
    @Test
    fun `crossfade duration is clamped to the remaining track time`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness, seconds = 5, positionMs = 176_000L, emitTransition = false)
        assertEquals(listOf(4_000L), harness.engine.crossfades)
        assertEquals(4_000L, harness.flows.artworkCrossfade.value!!.durationMs)
    }

    // 3. A zero-length crossfade is disabled at any position.
    @Test
    fun `a zero second crossfade never starts`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness, seconds = 0, positionMs = 179_000L, emitTransition = false)
        assertTrue(harness.engine.crossfades.isEmpty())
    }

    // 4. The transition publishes the incoming track and records the outgoing finish / incoming start.
    @Test
    fun `the transition publishes the incoming track and records the outgoing finish`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)

        assertEquals("b", harness.flows.queueState.value.currentTrackId)
        val playing = harness.flows.state.value as PlaybackState.Playing
        assertEquals("b", playing.item.trackId)
        assertEquals("b" to 175_000L, harness.scrobble.starts.last())

        val finished = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>().last()
        assertEquals("a", finished.value.trackId)
        assertEquals(PlaybackEndReason.COMPLETED, finished.value.endReason)

        val started = harness.listening.writes.filterIsInstance<ListeningWrite.AttemptStarted>().last()
        assertEquals("b", started.value.trackId)
    }

    // 5. Artwork transition metadata.
    @Test
    fun `artwork transition carries the fade id artwork paths and effective duration`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)

        val transition = harness.flows.artworkCrossfade.value
        assertNotNull(transition)
        assertEquals(1L, transition!!.id)
        assertEquals("/art/a.jpg", transition.fromArtworkPath)
        assertEquals("/art/b.jpg", transition.toArtworkPath)
        assertEquals(5_000L, transition.durationMs)
    }

    @Test
    fun `artwork transition is cleared once the fade ends`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        assertNotNull(harness.flows.artworkCrossfade.value)

        harness.engine.crossfading = false
        harness.tick()

        assertNull(harness.flows.artworkCrossfade.value)
    }

    @Test
    fun `disabling artwork blend clears the transition`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        assertNotNull(harness.flows.artworkCrossfade.value)

        harness.coordinator.onPlaybackSettings(5, false)

        assertNull(harness.flows.artworkCrossfade.value)
    }

    @Test
    fun `each fade receives a fresh artwork id`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        assertEquals(1L, harness.flows.artworkCrossfade.value!!.id)

        harness.engine.crossfading = false
        harness.tick() // clears artwork and preloads i+2
        harness.tick() // starts the second fade

        val second = harness.flows.artworkCrossfade.value
        assertNotNull(second)
        assertEquals(2L, second!!.id)
        assertEquals("/art/b.jpg", second.fromArtworkPath)
        assertEquals("/art/c.jpg", second.toArtworkPath)
    }

    // 6. The outgoing track's overlap is split at half the elapsed fade time, clamped to the fade length.
    @Test
    fun `crossfade overlap splits the outgoing session at half the elapsed fade time`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        harness.clock.elapsed = 4_000L
        harness.engine.crossfading = false

        harness.tick()

        val session = harness.listening.writes.filterIsInstance<ListeningWrite.Session>()
            .single { it.value.trackId == "a" }
        assertEquals(2, session.value.listenedSeconds)
        assertEquals(4_000L, session.value.endedAt)
    }

    @Test
    fun `crossfade overlap is clamped to the fade length`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        harness.clock.elapsed = 8_000L
        harness.engine.crossfading = false

        harness.tick()

        val session = harness.listening.writes.filterIsInstance<ListeningWrite.Session>()
            .single { it.value.trackId == "a" }
        assertEquals(2, session.value.listenedSeconds)
        assertEquals(5_000L, session.value.endedAt)
    }

    // 7. The following item is preloaded only after the fade completes.
    @Test
    fun `the following item is preloaded only after the fade`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        assertTrue(harness.engine.crossfading)
        assertNull(harness.engine.preloadedItem)

        harness.engine.crossfading = false
        harness.tick()

        assertEquals("c", harness.engine.preloadedItem?.trackId)
    }

    // 8. Stop and queue edits snap a running crossfade and clear its artwork.
    @Test
    fun `stop snaps the running crossfade and clears the artwork`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        assertTrue(harness.engine.crossfading)

        harness.send(PlaybackService.ActionStop)

        assertTrue(harness.engine.calls.contains("snapCrossfade"))
        assertNull(harness.flows.artworkCrossfade.value)
    }

    @Test
    fun `queue edits snap the running crossfade and realign the preloaded successor`() = runTest {
        val edits = listOf<Pair<String, suspend (PlaybackCoordinatorHarness) -> Unit>>(
            "SetShuffle" to { h -> h.send(PlaybackService.ActionSetShuffle, enabled = true) },
            "SetRepeat" to { h -> h.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.One) },
            "PlayNext" to { h -> h.send(PlaybackService.ActionPlayNext, trackIds = listOf("x")) },
            "Remove" to { h -> h.send(PlaybackService.ActionRemove, trackIds = listOf("c")) },
            "Reorder" to { h -> h.send(PlaybackService.ActionReorder, trackIds = listOf("a", "b", "d", "c")) },
        )
        edits.forEach { (name, edit) ->
            val harness = PlaybackCoordinatorHarness(this)
            startCrossfade(harness, tracks = listOf("a", "b", "c", "d"))
            assertTrue(name, harness.engine.crossfading)

            edit(harness)

            assertTrue("$name should snap the fade", harness.engine.calls.contains("snapCrossfade"))
            assertNull("$name should clear the artwork", harness.flows.artworkCrossfade.value)
            assertEquals("$name should realign the preload", harness.queue.peekNext(), harness.engine.preloadedItem?.trackId)
        }
    }

    // 9. Pause and seek during a fade clear the artwork and flush the outgoing overlap.
    @Test
    fun `pause during a fade clears artwork and flushes the outgoing overlap`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        harness.clock.elapsed = 4_000L

        harness.send(PlaybackService.ActionPause)

        assertNull(harness.flows.artworkCrossfade.value)
        assertTrue(harness.engine.calls.contains("pause"))
        val session = harness.listening.writes.filterIsInstance<ListeningWrite.Session>()
            .single { it.value.trackId == "a" }
        assertEquals(2, session.value.listenedSeconds)
    }

    @Test
    fun `seek during a fade clears artwork and flushes the outgoing overlap`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness)
        harness.clock.elapsed = 4_000L

        harness.send(PlaybackService.ActionSeek, positionMs = 120_000L)

        assertNull(harness.flows.artworkCrossfade.value)
        assertEquals(listOf(120_000L), harness.engine.seeks)
        val session = harness.listening.writes.filterIsInstance<ListeningWrite.Session>()
            .single { it.value.trackId == "a" }
        assertEquals(2, session.value.listenedSeconds)
    }

    // 10. A settings change during a running fade does not alter it.
    @Test
    fun `changing the crossfade length during a running fade leaves it untouched`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        startCrossfade(harness, seconds = 5)
        val artworkBefore = harness.flows.artworkCrossfade.value
        val crossfadesBefore = harness.engine.crossfades.toList()
        val callsBefore = harness.engine.calls.toList()

        harness.coordinator.onPlaybackSettings(10, true)

        assertEquals(artworkBefore, harness.flows.artworkCrossfade.value)
        assertEquals(crossfadesBefore, harness.engine.crossfades)
        assertEquals(callsBefore, harness.engine.calls)
    }

    // 11. A native transition whose incoming does not match the queue fails.
    // This relies on ActionPlay having preloaded the queue's next item ("b"), which is
    // the coordinator's precondition for consuming any transition event at all.
    @Test
    fun `a transition whose incoming does not match the queue fails`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))

        harness.engine.emit(EngineEvent.TransitionStarted(item("x"), 5_000L))
        harness.tick()

        assertEquals(
            PlaybackState.Failed("x", "Native transition no longer matches the playback queue"),
            harness.flows.state.value,
        )
    }
}
