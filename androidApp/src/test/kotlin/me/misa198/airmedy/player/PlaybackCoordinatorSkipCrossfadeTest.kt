package me.misa198.airmedy.player

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.fakes.FakePlayerEngine
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.*
import org.junit.Test

/** FR-031a host specification. No real time, player threads, or readiness sleeps. */
class PlaybackCoordinatorSkipCrossfadeTest {
    private val tracks = listOf("a", "b", "c", "d", "e", "f", "g")

    private suspend fun playing(
        scope: TestScope,
        seconds: Int = 4,
        skipEnabled: Boolean = true,
        kind: EngineKind = EngineKind.Media3,
    ): PlaybackCoordinatorHarness = PlaybackCoordinatorHarness(scope).also {
        it.engines.kind = kind
        it.engines.emitTransitionOnCrossfade = true
        it.engines.crossfadeIncomingPosition = 0L
        // Nonzero monotonic time also avoids ListeningTracker's inactive sentinel.
        it.clock.elapsed = 1_000L
        it.clock.now = 100_000L
        it.coordinator.onPlaybackSettings(seconds, true, true, skipEnabled)
        it.send(PlaybackService.ActionPlay, trackIds = tracks, startIndex = 1)
        assertEquals("b", it.flows.queueState.value.currentTrackId)
        assertTrue(it.flows.state.value is PlaybackState.Playing)
        it.engine.position = 1_000L
    }

    @Test
    fun nextCrossfadesOnceAndPublishesQueueStateArtworkListeningAndScrobble() = runTest {
        val h = playing(this)
        val outgoing = h.engine
        val target = h.resolver.resolve("c")!!
        val publications = h.nowPlaying.published.size
        val notifications = h.nowPlaying.notifications.size
        h.clock.elapsed = 5_000L
        h.clock.now = 104_000L
        outgoing.position = 4_000L
        h.send(PlaybackService.ActionNext)

        assertFade(h, outgoing, target, 2_000L)
        assertEquals(1, h.engines.created.size)
        assertEquals(listOf(target to TransportState.Playing), h.nowPlaying.published.drop(publications))
        assertEquals(listOf(0L), h.nowPlaying.publishedPositions.drop(publications))
        assertTrue(h.nowPlaying.notifications.size > notifications)
        assertTrue(h.nowPlaying.notifications.drop(notifications).all { it == target })
        assertEquals(listOf("b" to 0L, "c" to 0L), h.scrobble.starts)
        val finishes = h.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>()
        assertEquals(1, finishes.size)
        assertEquals("b", finishes.single().value.trackId)
        assertEquals(PlaybackEndReason.SKIPPED, finishes.single().value.endReason)
        assertEquals(104_000L, finishes.single().value.endedAt)
        assertEquals(4, finishes.single().value.listenedSeconds)
        val starts = h.listening.writes.filterIsInstance<ListeningWrite.AttemptStarted>()
        assertEquals(listOf("b", "c"), starts.map { it.value.trackId })
        assertEquals(0L, starts.last().value.startPositionMs)
        assertEquals(104_000L, starts.last().value.startedAt)
        assertArtwork(h, 1L, "b", "c", 2_000L)
        assertTrue(h.listening.writes.filterIsInstance<ListeningWrite.Session>().isEmpty())
        assertNull(outgoing.preloadedItem)
        val preloads = outgoing.preloadCalls.toList()
        h.tick()
        assertEquals(preloads, outgoing.preloadCalls)
        assertEquals(listOf(target to 2_000L), outgoing.crossfadeToCalls)
        assertEquals(1, h.nowPlaying.published.size - publications)

        h.clock.elapsed = 7_000L
        h.clock.now = 106_000L
        outgoing.position = 2_000L
        outgoing.crossfading = false
        h.tick()
        assertNull(h.flows.artworkCrossfade.value)
        val overlap = h.listening.writes.filterIsInstance<ListeningWrite.Session>()
        assertEquals(1, overlap.size)
        assertEquals("b", overlap.single().value.trackId)
        assertEquals(104_000L, overlap.single().value.startedAt)
        assertEquals(106_000L, overlap.single().value.endedAt)
        assertEquals(1, overlap.single().value.listenedSeconds)
        assertEquals("d", outgoing.preloadedItem?.trackId)
        h.tick()
        assertEquals(overlap, h.listening.writes.filterIsInstance<ListeningWrite.Session>())
        assertEquals(1, h.nowPlaying.published.size - publications)

        // Incoming played for 14 s; half the 2 s overlap is deducted exactly once.
        h.clock.elapsed = 19_000L
        h.clock.now = 118_000L
        outgoing.position = 14_000L
        h.send(PlaybackService.ActionPause)
        val incomingSessions = h.listening.writes.filterIsInstance<ListeningWrite.Session>()
            .filter { it.value.trackId == "c" }
        assertEquals(1, incomingSessions.size)
        assertEquals(13, incomingSessions.single().value.listenedSeconds)
    }

    @Test
    fun previousAtZeroCrossfadesToPreviousItem() = assertPreviousFade(0L)

    @Test
    fun previousAtThreeSecondBoundaryCrossfadesToPreviousItem() = assertPreviousFade(3_000L)

    @Test
    fun previousPastThreeSecondsSeeksToZeroWithoutCrossfadeOrPrepare() = runTest {
        val h = playing(this)
        val engine = h.engine
        val published = h.nowPlaying.published.size
        engine.position = 3_001L
        h.send(PlaybackService.ActionPrevious)
        assertEquals(listOf(0L), engine.seeks)
        assertTrue(allSkipCalls(h).isEmpty())
        assertSame(engine, h.engine)
        assertEquals(1, totalPrepares(h))
        assertEquals("b", h.flows.queueState.value.currentTrackId)
        assertEquals("b", (h.flows.state.value as PlaybackState.Playing).item.trackId)
        assertEquals(0L, engine.position)
        assertEquals(listOf(engine.preparedItem to TransportState.Playing), h.nowPlaying.published.drop(published))
        assertEquals(listOf(0L), h.nowPlaying.publishedPositions.drop(published))
        assertNull(h.flows.artworkCrossfade.value)
        assertTrue(h.listening.writes.filterIsInstance<ListeningWrite.AttemptFinished>().isEmpty())
    }

    @Test
    fun selectQueueItemWhilePlayingCrossfadesToSelectedItem() = runTest {
        val h = playing(this)
        val engine = h.engine
        val target = h.resolver.resolve("f")!!
        h.send(PlaybackService.ActionSelect, trackIds = listOf("f"))
        assertFade(h, engine, target, 2_000L)
        assertEquals(5, h.flows.queueState.value.currentIndex)
    }

    @Test
    fun oneSecondSettingUsesOneSecondSkipFade() = assertCoordinatorClamp(1, 1_000L)

    @Test
    fun twelveSecondSettingClampsSkipFadeToTwoSeconds() = assertCoordinatorClamp(12, 2_000L)

    @Test
    fun skipSettingOffHardCutsWithNewPrepare() = runTest {
        val h = playing(this, skipEnabled = false)
        val outgoing = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, outgoing, "c")
    }

    @Test
    fun defaultSkipSettingIsOffAndHardCuts() = runTest {
        val h = PlaybackCoordinatorHarness(this)
        h.engines.kind = EngineKind.Media3
        h.coordinator.onPlaybackSettings(4, true, true)
        assertFalse(h.coordinator.crossfadeOnSkip)
        h.send(PlaybackService.ActionPlay, trackIds = tracks, startIndex = 1)
        val outgoing = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, outgoing, "c")
    }

    @Test
    fun zeroSecondsWithSkipToggleStoredOnHardCuts() = runTest {
        val h = playing(this, seconds = 0)
        assertTrue(h.coordinator.crossfadeOnSkip)
        val outgoing = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, outgoing, "c")
    }

    @Test
    fun nativeEngineWithSkipEnabledHardCuts() = runTest {
        val h = playing(this, kind = EngineKind.Native)
        val outgoing = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, outgoing, "c")
    }

    @Test
    fun pausedNextHardCutsAndStaysPaused() = runTest {
        val h = playing(this)
        h.send(PlaybackService.ActionPause)
        val outgoing = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, outgoing, "c", paused = true)
    }

    @Test
    fun pausedPreviousHardCutsAndStaysPaused() = runTest {
        val h = playing(this)
        h.send(PlaybackService.ActionPause)
        val outgoing = h.engine
        h.send(PlaybackService.ActionPrevious)
        assertHardCut(h, outgoing, "a", paused = true)
    }

    @Test
    fun pausedSelectHardCutsAndStartsPlayingAsToday() = runTest {
        // Tapping a queue item while paused keeps today's behaviour: a hard cut that starts playback.
        val h = playing(this)
        h.send(PlaybackService.ActionPause)
        val outgoing = h.engine
        h.send(PlaybackService.ActionSelect, trackIds = listOf("f"))
        assertHardCut(h, outgoing, "f", paused = false)
    }

    @Test
    fun runningFadeSnapsBeforeHardCutWithoutAttemptingAnotherSkipFade() = runTest {
        // Both a manual skip fade and an automatic fade occupy the outgoing slot.
        for (manual in listOf(true, false)) {
            val h = playing(this)
            val fading = h.engine
            if (manual) {
                h.send(PlaybackService.ActionNext)
                assertFade(h, fading, h.resolver.resolve("c")!!, 2_000L)
            } else {
                fading.position = 176_000L
                h.tick()
                assertTrue(fading.crossfading)
                assertEquals("c", h.flows.queueState.value.currentTrackId)
            }
            val attempts = allSkipCalls(h).toList()
            val calls = fading.calls.size
            h.send(PlaybackService.ActionNext)
            assertEquals(attempts, allSkipCalls(h))
            assertEquals(listOf("snapCrossfade"), fading.calls.drop(calls).take(1))
            assertEquals(1, fading.calls.count { it == "snapCrossfade" })
            assertTrue(fading.calls.indexOf("snapCrossfade") < fading.calls.indexOf("close"))
            assertFalse(fading.crossfading)
            assertHardCut(h, fading, "d", noSkipAttempts = false)
        }
    }

    @Test
    fun nextAt599MillisecondsHardCutsAndAt600AfterThatSkipCrossfades() = runTest {
        val h = playing(this)
        h.clock.elapsed = 2_000L
        val first = h.engine
        h.send(PlaybackService.ActionNext)
        assertFade(h, first, h.resolver.resolve("c")!!, 2_000L)
        first.crossfading = false
        h.tick() // Retire the fade without advancing the coordinator clock.
        h.clock.elapsed = 2_599L
        h.clock.now = 1_000_000L // Wall time must not control debounce.
        val attempts = allSkipCalls(h).toList()
        h.send(PlaybackService.ActionNext)
        assertEquals(attempts, allSkipCalls(h))
        assertHardCut(h, first, "d", noSkipAttempts = false)
        val second = h.engine
        h.clock.elapsed = 3_199L // 600 ms from the hard-cut command, not the first fade.
        h.clock.now = 1L
        h.send(PlaybackService.ActionNext)
        assertFade(h, second, h.resolver.resolve("e")!!, 2_000L)
        assertEquals(listOf("c", "e"), allSkipCalls(h).map { it.first.trackId })
    }

    @Test
    fun nextAtOrPast600MillisecondsCrossfadesUsingElapsedClock() = runTest {
        for (delay in listOf(600L, 601L)) {
            val h = playing(this)
            val engine = h.engine
            h.clock.elapsed = 2_000L
            h.send(PlaybackService.ActionNext)
            assertFade(h, engine, h.resolver.resolve("c")!!, 2_000L)
            engine.crossfading = false
            h.tick()
            h.clock.elapsed = 2_000L + delay
            h.clock.now = 100_000L // No wall-clock advance.
            h.send(PlaybackService.ActionNext)
            assertFade(h, engine, h.resolver.resolve("d")!!, 2_000L, previousCalls = 1)
            assertEquals(1, h.engines.created.size)
        }
    }

    @Test
    fun firstSkipWithNoPreviousCommandCrossfadesAtElapsedZero() = runTest {
        val h = PlaybackCoordinatorHarness(this)
        h.engines.kind = EngineKind.Media3
        h.coordinator.onPlaybackSettings(4, true, true, true)
        h.send(PlaybackService.ActionPlay, trackIds = tracks, startIndex = 1)
        assertEquals(0L, h.clock.elapsed)
        val engine = h.engine
        h.send(PlaybackService.ActionNext)
        assertFade(h, engine, h.resolver.resolve("c")!!, 2_000L)
    }

    @Test
    fun previousRestartCommandStillStartsSkipDebounceWindow() = runTest {
        val h = playing(this)
        val engine = h.engine
        engine.position = 3_001L
        h.clock.elapsed = 2_000L
        h.send(PlaybackService.ActionPrevious)
        assertEquals(listOf(0L), engine.seeks)
        assertTrue(allSkipCalls(h).isEmpty())
        h.clock.elapsed = 2_599L
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, engine, "c")
        val eligible = h.engine
        h.clock.elapsed = 3_199L
        h.send(PlaybackService.ActionNext)
        assertFade(h, eligible, h.resolver.resolve("d")!!, 2_000L)
    }

    @Test
    fun hardCutSkipStillStartsDebounceWindowAndRapidHardCutResetsIt() = runTest {
        val h = playing(this, skipEnabled = false)
        h.clock.elapsed = 2_000L
        val original = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, original, "c")
        h.coordinator.onPlaybackSettings(4, true, true, true)
        h.clock.elapsed = 2_599L
        val firstHardCut = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, firstHardCut, "d")
        // 600 ms since the original command, but only 1 ms since the last skip.
        h.clock.elapsed = 2_600L
        val secondHardCut = h.engine
        h.send(PlaybackService.ActionNext)
        assertHardCut(h, secondHardCut, "e")
        h.clock.elapsed = 3_200L
        val eligible = h.engine
        h.send(PlaybackService.ActionNext)
        assertFade(h, eligible, h.resolver.resolve("f")!!, 2_000L)
    }

    @Test
    fun debounceIsSharedByNextPreviousAndSelectCommands() = runTest {
        val h = playing(this)
        val first = h.engine
        h.clock.elapsed = 2_000L
        h.send(PlaybackService.ActionNext)
        assertFade(h, first, h.resolver.resolve("c")!!, 2_000L)
        first.crossfading = false
        h.tick()
        h.clock.elapsed = 2_599L
        h.send(PlaybackService.ActionPrevious)
        assertHardCut(h, first, "b", noSkipAttempts = false)
        assertEquals(1, allSkipCalls(h).size)
        val second = h.engine
        h.clock.elapsed = 3_198L
        h.send(PlaybackService.ActionSelect, trackIds = listOf("e"))
        assertHardCut(h, second, "e", noSkipAttempts = false)
        assertEquals(1, allSkipCalls(h).size)
        val third = h.engine
        h.clock.elapsed = 3_798L
        h.send(PlaybackService.ActionPrevious)
        assertFade(h, third, h.resolver.resolve("d")!!, 2_000L)
    }

    @Test
    fun refusedCrossfadeAttemptsOnceThenHardCutsSameTargetWithoutArtworkOrOverlap() = runTest {
        val h = playing(this)
        val outgoing = h.engine
        outgoing.crossfadeToResult = false // Models readiness timeout or incoming preparation failure.
        val target = h.resolver.resolve("c")!!
        h.send(PlaybackService.ActionNext)
        assertEquals(listOf(target to 2_000L), outgoing.crossfadeToCalls)
        assertHardCut(h, outgoing, "c", noSkipAttempts = false)
        assertFalse(outgoing.crossfading)
        h.clock.elapsed = 5_000L
        h.clock.now = 104_000L
        h.tick()
        assertNull(h.flows.artworkCrossfade.value)
        assertTrue(h.listening.writes.filterIsInstance<ListeningWrite.Session>().isEmpty())
        assertEquals(listOf(target to 2_000L), allSkipCalls(h))
    }

    @Test
    fun prepareForwardsEnabledSkipSettingToEngine() = runTest {
        val h = playing(this)
        assertEquals(listOf(true), h.engine.crossfadeOnSkipCalls)
        assertEquals(listOf(4_000L to true), h.engine.crossfadeSettingsCalls)
        assertTrue(h.engine.calls.indexOf("setCrossfade") < h.engine.calls.indexOf("prepare"))
    }

    @Test
    fun skipOnlySettingChangeMakesExactlyOneEngineCallAndIdenticalWriteMakesNone() = runTest {
        val h = playing(this, skipEnabled = false)
        val engine = h.engine
        assertEquals(listOf(false), engine.crossfadeOnSkipCalls)
        val calls = engine.calls.toList()
        val preloads = engine.preloadCalls.toList()
        val state = h.flows.state.value
        h.coordinator.onPlaybackSettings(4, true, true, true)
        assertEquals(listOf(false, true), engine.crossfadeOnSkipCalls)
        assertEquals(listOf(4_000L to true, 4_000L to true), engine.crossfadeSettingsCalls)
        assertEquals(listOf("setCrossfade"), engine.calls.drop(calls.size))
        assertEquals(preloads, engine.preloadCalls)
        assertEquals(state, h.flows.state.value)
        val enabledCalls = engine.calls.toList()
        h.coordinator.onPlaybackSettings(4, true, true, true)
        assertEquals(enabledCalls, engine.calls)
        assertEquals(listOf(false, true), engine.crossfadeOnSkipCalls)
        h.coordinator.onPlaybackSettings(4, true, true, false)
        assertEquals(listOf(false, true, false), engine.crossfadeOnSkipCalls)
        assertEquals(listOf("setCrossfade"), engine.calls.drop(enabledCalls.size))
    }

    @Test
    fun settingsChangesPreserveRunningSkipFadeArtworkAndCapturedOverlapDuration() = runTest {
        val h = playing(this)
        val engine = h.engine
        h.clock.elapsed = 2_000L
        h.clock.now = 101_000L
        h.send(PlaybackService.ActionNext)
        assertFade(h, engine, h.resolver.resolve("c")!!, 2_000L)
        val artwork = h.flows.artworkCrossfade.value
        val attempts = engine.crossfadeToCalls.toList()
        val preloads = engine.preloadCalls.toList()
        for (seconds in listOf(12, 0)) {
            h.coordinator.onPlaybackSettings(seconds, true, false, false)
            assertEquals(artwork, h.flows.artworkCrossfade.value)
            assertTrue(engine.crossfading)
            assertEquals(attempts, engine.crossfadeToCalls)
            assertEquals(preloads, engine.preloadCalls)
            assertFalse(engine.calls.contains("snapCrossfade"))
            assertEquals(1, totalPrepares(h))
        }
        h.clock.elapsed = 5_000L // Late ticker still caps overlap to the original 2 s.
        h.clock.now = 104_000L
        engine.position = 3_000L
        engine.crossfading = false
        h.tick()
        val overlap = h.listening.writes.filterIsInstance<ListeningWrite.Session>()
        assertEquals(1, overlap.size)
        assertEquals("b", overlap.single().value.trackId)
        assertEquals(1, overlap.single().value.listenedSeconds)
        assertEquals(101_000L, overlap.single().value.startedAt)
        assertEquals(103_000L, overlap.single().value.endedAt)
        assertNull(h.flows.artworkCrossfade.value)
    }

    @Test
    fun successiveSkipFadesUseFreshArtworkIdsAndCurrentArtworkAsSource() = runTest {
        val h = playing(this)
        val engine = h.engine
        h.send(PlaybackService.ActionNext)
        assertFade(h, engine, h.resolver.resolve("c")!!, 2_000L)
        assertArtwork(h, 1L, "b", "c", 2_000L)
        engine.crossfading = false
        h.clock.elapsed += 2_000L
        h.clock.now += 2_000L
        h.tick()
        h.send(PlaybackService.ActionNext)
        assertFade(h, engine, h.resolver.resolve("d")!!, 2_000L, previousCalls = 1)
        assertArtwork(h, 2L, "c", "d", 2_000L)
    }

    private fun assertPreviousFade(position: Long) = runTest {
        val h = playing(this)
        val engine = h.engine
        engine.position = position
        h.send(PlaybackService.ActionPrevious)
        assertFade(h, engine, h.resolver.resolve("a")!!, 2_000L)
        assertTrue(engine.seeks.isEmpty())
    }

    private fun assertCoordinatorClamp(seconds: Int, expected: Long) = runTest {
        val h = playing(this, seconds = seconds)
        val engine = h.engine
        h.send(PlaybackService.ActionNext)
        assertFade(h, engine, h.resolver.resolve("c")!!, expected)
        assertArtwork(h, 1L, "b", "c", expected)
    }

    private fun assertFade(
        h: PlaybackCoordinatorHarness,
        engine: FakePlayerEngine,
        target: PlaybackItem,
        duration: Long,
        previousCalls: Int = 0,
    ) {
        assertEquals(listOf(target to duration), engine.crossfadeToCalls.drop(previousCalls))
        assertSame(engine, h.engine)
        assertFalse(engine.closed)
        assertEquals(1, engine.prepareCalls.size)
        assertTrue(engine.crossfading)
        assertTrue(engine.playing)
        assertEquals(target, engine.preparedItem)
        assertEquals(0L, engine.position)
        assertEquals(target.trackId, h.queue.snapshot().currentTrackId)
        assertEquals(target.trackId, h.flows.queueState.value.currentTrackId)
        assertEquals(PlaybackState.Playing(target, 0L, FakePlayerEngine.DefaultDurationMs), h.flows.state.value)
        assertTrue(engine.crossfades.isEmpty()) // Manual skip never invokes the automatic fade entry point.
    }

    private fun assertHardCut(
        h: PlaybackCoordinatorHarness,
        outgoing: FakePlayerEngine,
        target: String,
        paused: Boolean = false,
        noSkipAttempts: Boolean = true,
    ) {
        if (noSkipAttempts) assertTrue(allSkipCalls(h).isEmpty())
        assertTrue(outgoing.closed)
        assertNotSame(outgoing, h.engine)
        assertEquals(1, h.engine.prepareCalls.size)
        val prepare = h.engine.prepareCalls.single()
        assertEquals(target, prepare.item.trackId)
        assertEquals(0L, prepare.startPositionMs)
        assertEquals(paused, prepare.startPaused)
        assertEquals(target, h.queue.snapshot().currentTrackId)
        assertEquals(target, h.flows.queueState.value.currentTrackId)
        val expected = if (paused) PlaybackState.Paused(prepare.item, 0L, FakePlayerEngine.DefaultDurationMs)
            else PlaybackState.Playing(prepare.item, 0L, FakePlayerEngine.DefaultDurationMs)
        assertEquals(expected, h.flows.state.value)
        assertNull(h.flows.artworkCrossfade.value)
        assertFalse(h.engine.crossfading)
    }

    private fun assertArtwork(h: PlaybackCoordinatorHarness, id: Long, from: String, to: String, duration: Long) {
        assertEquals(ArtworkCrossfadeTransition(id, "/art/$from.jpg", "/art/$to.jpg", duration), h.flows.artworkCrossfade.value)
    }

    private fun allSkipCalls(h: PlaybackCoordinatorHarness) = h.engines.created.flatMap { it.crossfadeToCalls }

    private fun totalPrepares(h: PlaybackCoordinatorHarness) = h.engines.created.sumOf { it.prepareCalls.size }
}
