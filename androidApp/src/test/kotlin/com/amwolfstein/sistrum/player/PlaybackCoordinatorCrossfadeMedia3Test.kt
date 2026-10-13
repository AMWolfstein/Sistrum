package com.amwolfstein.sistrum.player

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.amwolfstein.sistrum.mood.MoodRadioTrack
import com.amwolfstein.sistrum.player.engine.EngineEvent
import com.amwolfstein.sistrum.player.engine.EngineKind
import com.amwolfstein.sistrum.player.fakes.FakePlayerEngine
import com.amwolfstein.sistrum.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared coordinator crossfade policy exercised with a Media3-kind engine (T060). */
class PlaybackCoordinatorCrossfadeMedia3Test {
    private fun harness(scope: TestScope, kind: EngineKind = EngineKind.Media3) =
        PlaybackCoordinatorHarness(scope).also {
            it.engines.kind = kind
            it.engines.emitTransitionOnCrossfade = true
            it.engines.crossfadeIncomingPosition = 0L
        }

    private suspend fun startFade(
        h: PlaybackCoordinatorHarness,
        positionMs: Long = 175_000L,
        tracks: List<String> = listOf("a", "b", "c", "d"),
    ) {
        h.coordinator.onPlaybackSettings(5, true)
        h.send(PlaybackService.ActionPlay, trackIds = tracks)
        h.engine.position = positionMs
        h.tick()
        assertTrue(h.engine.crossfading)
        assertEquals("b", h.flows.queueState.value.currentTrackId)
    }

    private fun assertOverlap(h: PlaybackCoordinatorHarness, outgoing: String = "a", endedAt: Long = 4_000L) {
        val session = h.listening.writes.filterIsInstance<ListeningWrite.Session>()
            .single { it.value.trackId == outgoing }
        assertEquals(2, session.value.listenedSeconds)
        assertEquals(endedAt, session.value.endedAt)
    }

    private fun assertSnapped(h: PlaybackCoordinatorHarness, engine: FakePlayerEngine = h.engine) {
        assertFalse(engine.crossfading)
        assertEquals(1, engine.calls.count { it == "snapCrossfade" })
        assertNull(h.flows.artworkCrossfade.value)
        assertOverlap(h)
    }

    @Test
    fun `prepare and changed seconds deliver crossfade settings for either engine kind`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = harness(this, kind)
            h.coordinator.onPlaybackSettings(5, true)
            h.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
            assertEquals(listOf(5_000L to true), h.engine.crossfadeSettingsCalls)
            if (kind == EngineKind.Media3) {
                assertTrue(h.engine.calls.indexOf("setCrossfade") < h.engine.calls.indexOf("prepare"))
            }

            h.coordinator.onPlaybackSettings(8, true)
            assertEquals(listOf(5_000L to true, 8_000L to true), h.engine.crossfadeSettingsCalls)
            val callsBefore = h.engine.calls.toList()
            h.coordinator.onPlaybackSettings(8, true)
            assertEquals(callsBefore, h.engine.calls)
            assertEquals(2, h.engine.crossfadeSettingsCalls.size)
            h.coordinator.onPlaybackSettings(8, false)
            assertEquals(2, h.engine.crossfadeSettingsCalls.size)
            h.coordinator.onPlaybackSettings(0, false)
            assertEquals(0L to true, h.engine.crossfadeSettingsCalls.last())
            h.engine.position = 179_000L
            h.tick()
            assertTrue(h.engine.crossfades.isEmpty())

            h.send(PlaybackService.ActionNext)
            assertEquals(listOf(0L to true), h.engine.crossfadeSettingsCalls)
        }
    }

    @Test
    fun `fresh prepare receives zero even without a settings emission`() = runTest {
        val h = harness(this)
        h.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
        assertEquals(listOf(0L to true), h.engine.crossfadeSettingsCalls)
    }

    @Test
    fun `restore recovery and restart prepares receive the current settings`() = runTest {
        val restored = harness(this)
        restored.coordinator.onPlaybackSettings(7, true)
        restored.restore(
            PlaybackSession(PlaybackQueueSnapshot(originalTrackIds = listOf("a", "b"), currentIndex = 0), 12_000L),
            setOf("a", "b"),
        )
        assertEquals(listOf(7_000L to true), restored.engine.crossfadeSettingsCalls)
        assertTrue(restored.engine.prepareCalls.single().startPaused)
        assertTrue(restored.engine.calls.indexOf("setCrossfade") < restored.engine.calls.indexOf("prepare"))

        val recovered = harness(this)
        recovered.coordinator.onPlaybackSettings(6, true)
        recovered.send(PlaybackService.ActionPlay, trackIds = listOf("a"))
        val original = recovered.engine
        original.position = 15_000L
        original.emit(EngineEvent.OutputDisconnected)
        recovered.tick()
        assertTrue(original.closed)
        assertEquals(listOf(6_000L to true), recovered.engine.crossfadeSettingsCalls)
        assertEquals(15_000L, recovered.engine.prepareCalls.single().startPositionMs)

        recovered.send(PlaybackService.ActionNext) // exhausted queue retains a paused item without an engine
        recovered.send(PlaybackService.ActionResume)
        assertEquals(listOf(6_000L to true), recovered.engine.crossfadeSettingsCalls)
        assertEquals(3, recovered.engines.created.size)
    }

    @Test
    fun `automatic fade clamps duration and switches state session listening and artwork once`() = runTest {
        val h = harness(this)
        h.coordinator.onPlaybackSettings(5, true)
        h.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        val publicationsBefore = h.nowPlaying.published.size
        val notificationsBefore = h.nowPlaying.notifications.size
        h.engine.position = 174_999L
        h.tick()
        assertTrue(h.engine.crossfades.isEmpty())
        h.engine.position = 176_000L
        h.tick()

        assertEquals(listOf(4_000L), h.engine.crossfades)
        val playing = h.flows.state.value as PlaybackState.Playing
        assertEquals("b", playing.item.trackId)
        assertEquals(0L, playing.positionMs)
        assertEquals("b", h.flows.queueState.value.currentTrackId)
        assertEquals(listOf("b" to TransportState.Playing), h.nowPlaying.published.drop(publicationsBefore).map { it.first.trackId to it.second })
        assertEquals(0L, h.nowPlaying.publishedPositions.last())
        assertTrue(h.nowPlaying.notifications.drop(notificationsBefore).all { it.trackId == "b" })
        assertEquals("b" to 0L, h.scrobble.starts.last())
        val finished = h.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>().single()
        assertEquals("a", finished.value.trackId)
        assertEquals(PlaybackEndReason.COMPLETED, finished.value.endReason)
        assertEquals(listOf("a", "b"), h.listening.writes.filterIsInstance<ListeningWrite.AttemptStarted>().map { it.value.trackId })
        val artwork = h.flows.artworkCrossfade.value!!
        assertEquals(1L, artwork.id)
        assertEquals("/art/a.jpg", artwork.fromArtworkPath)
        assertEquals("/art/b.jpg", artwork.toArtworkPath)
        assertEquals(4_000L, artwork.durationMs)
        assertNull(h.engine.preloadedItem)
        h.tick()
        assertEquals(listOf(4_000L), h.engine.crossfades)
        assertEquals(1, h.nowPlaying.published.size - publicationsBefore)

        h.clock.elapsed = 8_000L
        h.engine.crossfading = false
        h.tick()
        assertNull(h.flows.artworkCrossfade.value)
        assertOverlap(h)
        assertEquals("c", h.engine.preloadedItem?.trackId)
        h.engine.position = 175_000L
        h.tick()
        assertEquals(2L, h.flows.artworkCrossfade.value!!.id)
        assertEquals("/art/b.jpg", h.flows.artworkCrossfade.value!!.fromArtworkPath)
        assertEquals("/art/c.jpg", h.flows.artworkCrossfade.value!!.toArtworkPath)
    }

    @Test
    fun `settings during a fade apply to later fades and identical writes have no effects`() = runTest {
        val h = harness(this)
        startFade(h)
        val artwork = h.flows.artworkCrossfade.value
        val fade = h.engine.crossfades.toList()
        val preloads = h.engine.preloadCalls.toList()
        h.coordinator.onPlaybackSettings(10, true)
        assertEquals(10_000L to true, h.engine.crossfadeSettingsCalls.last())
        assertEquals(artwork, h.flows.artworkCrossfade.value)
        assertEquals(fade, h.engine.crossfades)
        assertEquals(preloads, h.engine.preloadCalls)
        assertTrue(h.engine.crossfading)
        val calls = h.engine.calls.toList()
        h.coordinator.onPlaybackSettings(10, true)
        assertEquals(calls, h.engine.calls)
        h.engine.crossfading = false
        h.tick()
        h.engine.position = 170_000L
        h.tick()
        assertEquals(listOf(5_000L, 10_000L), h.engine.crossfades)
    }

    @Test
    fun `disabling seconds during a fade leaves the running fade intact`() = runTest {
        val h = harness(this)
        startFade(h)
        val artwork = h.flows.artworkCrossfade.value
        h.coordinator.onPlaybackSettings(0, true)
        assertEquals(0L to true, h.engine.crossfadeSettingsCalls.last())
        assertEquals(artwork, h.flows.artworkCrossfade.value)
        assertTrue(h.engine.crossfading)
        h.engine.crossfading = false
        h.tick()
        h.engine.position = 179_000L
        h.tick()
        assertEquals(listOf(5_000L), h.engine.crossfades)
    }

    @Test
    fun `a refused fade creates no artwork and does not swallow a pending ended advance`() = runTest {
        val h = harness(this)
        h.coordinator.onPlaybackSettings(5, true)
        h.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        val outgoing = h.engine
        outgoing.beginCrossfadeResult = false
        outgoing.position = 176_000L
        outgoing.emit(EngineEvent.Ended)
        h.tick()
        assertEquals(listOf(4_000L), outgoing.crossfades)
        assertNull(h.flows.artworkCrossfade.value)
        assertFalse(outgoing.crossfading)
        assertTrue(outgoing.closed)
        assertEquals("b", (h.flows.state.value as PlaybackState.Playing).item.trackId)
        assertEquals("b", h.engine.prepareCalls.single().item.trackId)
        h.tick()
        assertEquals("b", h.flows.queueState.value.currentTrackId)
    }

    @Test
    fun `a refused fade later consumes gapless advance without artwork or overlap`() = runTest {
        val h = harness(this)
        h.coordinator.onPlaybackSettings(5, true)
        h.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        h.engine.beginCrossfadeResult = false
        h.engine.position = 176_000L
        val publishedBefore = h.nowPlaying.published.size
        h.tick()
        assertNull(h.flows.artworkCrossfade.value)
        assertEquals("a", h.flows.queueState.value.currentTrackId)
        assertEquals(publishedBefore, h.nowPlaying.published.size)
        val incoming = h.engine.preloadedItem!!
        h.engine.position = 0L
        h.engine.preloadedItem = null
        h.engine.emit(EngineEvent.GaplessAdvanced(incoming))
        h.tick()
        assertNull(h.flows.artworkCrossfade.value)
        assertEquals("b", (h.flows.state.value as PlaybackState.Playing).item.trackId)
        assertEquals(listOf("b"), h.nowPlaying.published.drop(publishedBefore).map { it.first.trackId })
        assertEquals("c", h.engine.preloadedItem?.trackId)
        h.clock.elapsed = 4_000L
        h.tick()
        assertTrue(h.listening.writes.filterIsInstance<ListeningWrite.Session>().none { it.value.trackId == "a" })
        assertEquals(1, h.nowPlaying.published.size - publishedBefore)
    }

    @Test
    fun `next snaps before closing and hard cuts to the item after incoming for both engines`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = harness(this, kind)
            startFade(h)
            val fading = h.engine
            h.clock.elapsed = 4_000L
            h.send(PlaybackService.ActionNext)
            assertSnapped(h, fading)
            assertTrue(fading.calls.indexOf("snapCrossfade") < fading.calls.indexOf("close"))
            assertEquals("c", h.flows.queueState.value.currentTrackId)
            assertEquals("c", (h.flows.state.value as PlaybackState.Playing).item.trackId)
            assertEquals("c", h.engine.prepareCalls.single().item.trackId)
            assertEquals(0L, h.engine.prepareCalls.single().startPositionMs)
            assertTrue(h.engine.crossfades.isEmpty())
        }
    }

    @Test
    fun `previous applies the three second boundary to incoming for both engines`() = runTest {
        EngineKind.entries.forEach { kind ->
            listOf(0L, 3_000L, 3_001L).forEach { incomingPosition ->
                val h = harness(this, kind)
                startFade(h)
                val fading = h.engine
                fading.position = incomingPosition
                h.clock.elapsed = 4_000L
                h.send(PlaybackService.ActionPrevious)
                assertSnapped(h, fading)
                val expected = if (incomingPosition > 3_000L) "b" else "a"
                assertEquals(expected, (h.flows.state.value as PlaybackState.Playing).item.trackId)
                assertEquals(expected, h.flows.queueState.value.currentTrackId)
                assertEquals(0L, h.engine.position)
                if (incomingPosition > 3_000L) {
                    assertEquals(1, h.engines.created.size)
                    assertEquals(listOf(0L), fading.seeks)
                    assertTrue(fading.calls.indexOf("snapCrossfade") < fading.calls.indexOf("seekTo"))
                } else {
                    assertTrue(fading.calls.indexOf("snapCrossfade") < fading.calls.indexOf("close"))
                    assertEquals("a", h.engine.prepareCalls.single().item.trackId)
                }
            }
        }
    }

    @Test
    fun `next at queue end during a fade snaps and flushes overlap before retaining incoming`() = runTest {
        val h = harness(this)
        startFade(h, tracks = listOf("a", "b"))
        val fading = h.engine
        h.clock.elapsed = 4_000L
        h.send(PlaybackService.ActionNext)
        assertSnapped(h, fading)
        val paused = h.flows.state.value as PlaybackState.Paused
        assertEquals("b", paused.item.trackId)
        assertEquals(0L, paused.positionMs)
    }

    @Test
    fun `mood radio refill leaves the busy slot alone and preloads the new successor after fade`() = runTest {
        fun candidate(id: String) = MoodRadioTrack(id, "album-$id", "artist-$id", 0.5, 0.5, 0.5, 120.0)
        val h = harness(this)
        h.coordinator.onPlaybackSettings(5, true)
        h.library.moodTracks = listOf(candidate("a"), candidate("b"))
        h.send(PlaybackService.ActionStartMoodRadio, trackIds = listOf("a"))
        assertEquals(listOf("a", "b"), h.queue.snapshot().activeTrackIds)
        h.library.moodTracks += candidate("c")
        val preloads = h.engine.preloadCalls.toList()
        val clears = h.engine.calls.count { it == "clearPreloaded" }
        h.engine.position = 175_000L
        h.tick()
        assertTrue(h.flows.moodRadioActive.value)
        assertTrue(h.engine.crossfading)
        assertEquals(listOf("a", "b", "c"), h.flows.queueState.value.activeTrackIds)
        assertEquals(preloads, h.engine.preloadCalls)
        assertEquals(clears, h.engine.calls.count { it == "clearPreloaded" })
        assertNull(h.engine.preloadedItem)
        h.tick()
        assertEquals(preloads, h.engine.preloadCalls)
        h.engine.crossfading = false
        h.tick()
        assertEquals(listOf("b", "c"), h.engine.preloadCalls.map { it.first.trackId })
        assertEquals("c", h.engine.preloadedItem?.trackId)
    }

    @Test
    fun `pause seek and stop explicitly snap and split overlap for either engine`() = runTest {
        EngineKind.entries.forEach { kind ->
            listOf(PlaybackService.ActionPause, PlaybackService.ActionSeek, PlaybackService.ActionStop).forEach { action ->
                val h = harness(this, kind)
                startFade(h)
                val fading = h.engine
                h.clock.elapsed = 4_000L
                h.send(action, positionMs = 120_000L)
                assertSnapped(h, fading)
                val operation = when (action) {
                    PlaybackService.ActionPause -> {
                        val paused = h.flows.state.value as PlaybackState.Paused
                        assertEquals("b", paused.item.trackId)
                        assertEquals(0L, paused.positionMs)
                        "pause"
                    }
                    PlaybackService.ActionSeek -> {
                        assertEquals(listOf(120_000L), fading.seeks)
                        assertEquals("b", (h.flows.state.value as PlaybackState.Playing).item.trackId)
                        "seekTo"
                    }
                    else -> {
                        assertEquals(PlaybackState.Idle, h.flows.state.value)
                        assertTrue(fading.closed)
                        "close"
                    }
                }
                assertTrue(fading.calls.indexOf("snapCrossfade") < fading.calls.indexOf(operation))
            }
        }
    }

    @Test
    fun `queue edits snap clear artwork split overlap and realign successor for either engine`() = runTest {
        val edits = listOf<suspend (PlaybackCoordinatorHarness) -> Unit>(
            { it.send(PlaybackService.ActionSetShuffle, enabled = true) },
            { it.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.One) },
            { it.send(PlaybackService.ActionPlayNext, trackIds = listOf("x")) },
            { it.send(PlaybackService.ActionAppend, trackIds = listOf("x")) },
            { it.send(PlaybackService.ActionRemove, trackIds = listOf("c")) },
            { it.send(PlaybackService.ActionReorder, trackIds = listOf("a", "b", "d", "c")) },
        )
        EngineKind.entries.forEach { kind ->
            edits.forEach { edit ->
                val h = harness(this, kind)
                startFade(h)
                h.clock.elapsed = 4_000L
                val before = h.engine.calls.size
                edit(h)
                assertSnapped(h)
                assertEquals("b", (h.flows.state.value as PlaybackState.Playing).item.trackId)
                assertEquals(h.queue.peekNext(), h.engine.preloadedItem?.trackId)
                assertEquals("snapCrossfade", h.engine.calls.drop(before).first())
            }
        }
    }

    @Test
    fun `overlap still uses engine fade duration when artwork blend is disabled mid fade`() = runTest {
        val h = harness(this)
        startFade(h)
        h.coordinator.onPlaybackSettings(5, false)
        assertNull(h.flows.artworkCrossfade.value)
        h.clock.elapsed = 8_000L
        h.engine.crossfading = false
        h.tick()
        assertOverlap(h, endedAt = 5_000L)
    }
}
