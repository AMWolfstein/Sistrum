package com.amwolfstein.sistrum.player

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.amwolfstein.sistrum.player.engine.EngineKind
import com.amwolfstein.sistrum.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T061a: automatic fades use the processed timeline; manual skip fades keep their own clamp. */
class PlaybackCoordinatorFadeLeadTest {
    private suspend fun playing(
        scope: TestScope,
        kind: EngineKind,
        leadMs: Long,
        durationMs: Long = 180_000L,
        crossfadeOnSkip: Boolean = false,
    ): PlaybackCoordinatorHarness = PlaybackCoordinatorHarness(scope, durationFor = { durationMs }).also {
        it.engines.kind = kind
        it.engines.fadeLead = leadMs
        it.engines.emitTransitionOnCrossfade = true
        it.engines.crossfadeIncomingPosition = 0L
        it.coordinator.onPlaybackSettings(4, true, crossfadeOnSkip = crossfadeOnSkip)
        it.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b", "c"))
        assertTrue(it.flows.state.value is PlaybackState.Playing)
        assertEquals(durationMs, (it.flows.state.value as PlaybackState.Playing).durationMs)
        assertEquals(leadMs, it.engine.fadeLeadMs())
        assertTrue(it.engine.hasPreloaded())
    }

    private fun assertNoFade(h: PlaybackCoordinatorHarness) {
        assertTrue("No beginCrossfade call expected", h.engine.crossfades.isEmpty())
        assertFalse(h.engine.crossfading)
        assertNull(h.flows.artworkCrossfade.value)
    }

    private suspend fun assertStartBoundary(scope: TestScope, kind: EngineKind) {
        val h = playing(scope, kind, leadMs = 600L)
        h.engine.position = 175_399L // Processed remaining: 4001 ms, outside the window.
        h.tick()
        assertNoFade(h)

        h.engine.position = 175_400L // Processed remaining: 4000 ms, inclusive upper bound.
        h.tick()
        assertEquals(listOf(4_000L), h.engine.crossfades)
        assertEquals(1, h.engine.calls.count { it == "beginCrossfade" })
        assertTrue(h.engine.crossfading)
        h.tick()
        assertEquals(listOf(4_000L), h.engine.crossfades)
    }

    private suspend fun assertDurationClamp(scope: TestScope, kind: EngineKind) {
        val h = playing(scope, kind, leadMs = 600L)
        h.engine.position = 176_100L // 180000 - (176100 + 600) = 3300 ms.
        h.tick()

        assertEquals(listOf(3_300L), h.engine.crossfades)
        val artwork = h.flows.artworkCrossfade.value
        assertNotNull(artwork)
        assertEquals(3_300L, artwork?.durationMs)
    }

    private suspend fun assertLowerBound(scope: TestScope, kind: EngineKind) {
        val h = playing(scope, kind, leadMs = 600L)
        h.engine.position = 179_000L // Processed remaining: 400 ms, below the 401 ms lower bound.
        h.tick()
        assertNoFade(h)
    }

    private suspend fun assertZeroLead(scope: TestScope, kind: EngineKind) {
        val h = playing(scope, kind, leadMs = 0L)
        h.engine.position = 175_999L
        h.tick()
        assertNoFade(h)

        h.engine.position = 176_000L
        h.tick()
        assertEquals(listOf(4_000L), h.engine.crossfades)
        assertEquals(1, h.engine.calls.count { it == "beginCrossfade" })
    }

    private suspend fun assertLargeLead(scope: TestScope, kind: EngineKind) {
        val h = playing(scope, kind, leadMs = 1_500L)
        h.engine.position = 174_500L // Use the full reported lead: processed remaining is 4000 ms.
        h.tick()
        assertEquals(listOf(4_000L), h.engine.crossfades)
    }

    private suspend fun assertShortTrack(scope: TestScope, kind: EngineKind) {
        // Includes points inside the fade window if the duration < 2000 guard were removed,
        // as well as both window boundaries and positions at/past the processed track end.
        for (lead in listOf(0L, 600L, 1_500L)) {
            val h = playing(scope, kind, leadMs = lead, durationMs = 1_900L)
            for (position in listOf(0L, 349L, 350L, 949L, 950L, 1_499L, 1_500L, 1_899L, 1_900L)) {
                h.engine.position = position
                h.tick()
                assertNoFade(h)
            }
        }
    }

    @Test
    fun media3StartsAtProcessedWindowBoundary() = runTest { assertStartBoundary(this, EngineKind.Media3) }

    @Test
    fun nativeKindStartsAtProcessedWindowBoundary() = runTest { assertStartBoundary(this, EngineKind.Native) }

    @Test
    fun media3ClampsFadeAndArtworkToProcessedRemainingTime() = runTest { assertDurationClamp(this, EngineKind.Media3) }

    @Test
    fun nativeKindClampsFadeAndArtworkToProcessedRemainingTime() = runTest { assertDurationClamp(this, EngineKind.Native) }

    @Test
    fun media3DoesNotFadeWithFourHundredProcessedMillisecondsRemaining() = runTest { assertLowerBound(this, EngineKind.Media3) }

    @Test
    fun nativeKindDoesNotFadeWithFourHundredProcessedMillisecondsRemaining() = runTest { assertLowerBound(this, EngineKind.Native) }

    @Test
    fun media3ZeroLeadKeepsExistingStartPoints() = runTest { assertZeroLead(this, EngineKind.Media3) }

    @Test
    fun nativeKindZeroLeadKeepsExistingStartPoints() = runTest { assertZeroLead(this, EngineKind.Native) }

    @Test
    fun media3UsesFullReportedFifteenHundredMillisecondLead() = runTest { assertLargeLead(this, EngineKind.Media3) }

    @Test
    fun nativeKindUsesFullReportedFifteenHundredMillisecondLead() = runTest { assertLargeLead(this, EngineKind.Native) }

    @Test
    fun media3ManualSkipKeepsTwoSecondClampWithLead() = runTest {
        // FR-031a is Media3-only; Native continues to hard-cut manual skips.
        val h = playing(this, EngineKind.Media3, leadMs = 600L, crossfadeOnSkip = true)
        val outgoing = h.engine
        val target = h.resolver.resolve("b")!!
        outgoing.position = 179_000L

        h.send(PlaybackService.ActionNext)

        assertEquals(listOf(target to 2_000L), outgoing.crossfadeToCalls)
        assertTrue(outgoing.crossfades.isEmpty())
        assertEquals("b", h.flows.queueState.value.currentTrackId)
        val artwork = h.flows.artworkCrossfade.value
        assertNotNull(artwork)
        assertEquals(2_000L, artwork?.durationMs)
    }

    @Test
    fun media3ShortTrackNeverFadesRegardlessOfLead() = runTest { assertShortTrack(this, EngineKind.Media3) }

    @Test
    fun nativeKindShortTrackNeverFadesRegardlessOfLead() = runTest { assertShortTrack(this, EngineKind.Native) }
}
