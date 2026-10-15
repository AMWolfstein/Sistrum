package com.amwolfstein.sistrum.player

import com.amwolfstein.sistrum.analysis.SilenceProfile
import com.amwolfstein.sistrum.player.engine.EngineKind
import com.amwolfstein.sistrum.player.engine.PlayerEngine
import com.amwolfstein.sistrum.player.fakes.PlaybackCoordinatorHarness
import com.amwolfstein.sistrum.player.media3.TransitionController
import com.amwolfstein.sistrum.player.media3.TransitionSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaybackCoordinatorSilenceTest {
    private class Fixture(scope: TestScope, loader: suspend (String) -> SilenceProfile?) {
        val h = PlaybackCoordinatorHarness(scope).also {
            it.engines.kind = EngineKind.Media3
            it.engines()
        }
        var skip = true
        var timingCalls = 0
        val engine = object : PlayerEngine by h.engine {
            override fun setCrossfadeSkipSilence(enabled: Boolean) { skip = enabled }
            override fun automaticFadeDurationMs(current: PlaybackItem, positionMs: Long, durationMs: Long,
                repeatOne: Boolean, crossfadeSeconds: Int): Long? {
                timingCalls++
                return TransitionController(TransitionSettings(crossfadeSeconds * 1000L, crossfadeSkipSilence = skip))
                    .automaticFadeDurationMs(current, positionMs, durationMs, repeatOne)
            }
        }
        val coordinator = PlaybackCoordinator(scope, h.queue, h.flows, { engine }, h.resolver, h.library,
            h.nowPlaying, h.focus, h.listening, h.scrobble, h.sessionStore, h.clock, h.log,
            h.listeningTracker, { 1L }, silenceProfile = loader)
        init { coordinator.markRestored() }
    }

    @Test fun profilesReachBothSlotsAndEngineDecidesFromProcessedPosition() = runTest {
        val f = Fixture(this) { if (it == "a") SilenceProfile(0, 3000) else SilenceProfile(2000, 0) }
        f.coordinator.onPlaybackSettings(6, true)
        f.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a", "b")).join()
        assertEquals(SilenceProfile(0, 3000), f.h.engine.preparedItem!!.silence)
        assertEquals(SilenceProfile(2000, 0), f.h.engine.preloadedItem!!.silence)
        f.h.engine.position = 170_999
        f.coordinator.tick()
        assertTrue(f.h.engine.crossfades.isEmpty())
        f.h.engine.position = 171_650
        f.h.engine.fadeLead = 600
        f.coordinator.tick()
        assertEquals(listOf(4750L), f.h.engine.crossfades)
        assertTrue(f.timingCalls >= 2)
    }

    @Test fun savedOffReachesNewAndExistingEngineAndRestoresLegacyTiming() = runTest {
        val f = Fixture(this) { SilenceProfile(2000, 3000) }
        f.coordinator.onPlaybackSettings(6, true, crossfadeSkipSilence = false)
        f.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a", "b")).join()
        assertFalse(f.skip)
        f.h.engine.position = 171_000
        f.coordinator.tick()
        assertTrue(f.h.engine.crossfades.isEmpty())
        f.coordinator.onPlaybackSettings(6, true, crossfadeSkipSilence = true)
        assertTrue(f.skip)
        f.coordinator.tick()
        assertEquals(listOf(6000L), f.h.engine.crossfades)
    }

    @Test fun repeatAllWithOneItemKeepsTheEnginesSameItemTimingRule() = runTest {
        val f = Fixture(this) { SilenceProfile(2000, 3000) }
        f.coordinator.onPlaybackSettings(6, true)
        f.coordinator.dispatch(PlaybackService.ActionSetRepeat, repeat = RepeatMode.All).join()
        f.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a")).join()
        assertEquals("a", f.h.engine.preloadedItem!!.trackId)
        f.h.engine.position = 171_000
        f.coordinator.tick()
        assertTrue(f.h.engine.crossfades.isEmpty())
        f.h.engine.position = 174_000
        f.coordinator.tick()
        assertEquals(listOf(6000L), f.h.engine.crossfades)
    }

    @Test fun failedProfileReadsKeepPlaybackAvailableWithLoggedCause() = runTest {
        val f = Fixture(this) { error("analysis storage unavailable") }
        f.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a", "b")).join()
        assertNull(f.h.engine.preparedItem!!.silence)
        assertNull(f.h.engine.preloadedItem!!.silence)
        assertTrue(f.h.log.lines.any { it.contains("silence", ignoreCase = true) })
    }

    @Test fun missingProfileDoesNotDelayPlaybackForAnalysis() = runTest {
        val f = Fixture(this) { null }
        f.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a", "b")).join()
        assertEquals("a", f.h.engine.preparedItem!!.trackId)
        assertEquals("b", f.h.engine.preloadedItem!!.trackId)
        assertNull(f.h.engine.preparedItem!!.silence)
        assertNull(f.h.engine.preloadedItem!!.silence)
    }

    @Test fun profileReadCancellationPropagates() = runTest {
        var readStarted = false
        val f = Fixture(this) {
            readStarted = true
            throw CancellationException("cancelled profile read")
        }
        f.coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a")).join()
        assertTrue(readStarted)
        assertTrue(f.h.engine.prepareAttempts.isEmpty())
        assertFalse(f.h.log.lines.any { it.contains("Unable to read silence") })
    }

    @Test fun nativeEngineDefaultTimingIgnoresProfiles() = runTest {
        val h = PlaybackCoordinatorHarness(this)
        h.engines.kind = EngineKind.Native
        h.engines()
        assertNull(h.engine.automaticFadeDurationMs(
            PlaybackItem("a", "a", "artist", "/a", silence = SilenceProfile(0, 3000)),
            171000, 180000, false, 6))
        assertEquals(6000L, h.engine.automaticFadeDurationMs(
            PlaybackItem("a", "a", "artist", "/a", silence = SilenceProfile(0, 3000)),
            174000, 180000, false, 6))
    }
}
