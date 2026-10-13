package me.misa198.airmedy.player

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorRepeatOneCrossfadeTest {
    private suspend fun start(scope: TestScope, kind: EngineKind, repeatCrossfade: Boolean) =
        PlaybackCoordinatorHarness(scope).also { h ->
            h.engines.kind = kind
            h.coordinator.onPlaybackSettings(4, true, repeatCrossfade)
            h.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))
            h.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.One)
            assertEquals("a", h.engine.preloadedItem?.trackId)
        }

    @Test
    fun `repeat one off suppresses fade and artwork then repeats gaplessly on both engines`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, false)
            val engine = h.engine
            engine.position = 176_000L
            h.tick()
            assertTrue(engine.crossfades.isEmpty())
            assertFalse(engine.crossfading)
            assertNull(h.flows.artworkCrossfade.value)
            val incoming = engine.preloadedItem!!
            engine.preloadedItem = null
            engine.position = 0L
            engine.emit(EngineEvent.GaplessAdvanced(incoming))
            h.tick()
            assertEquals("a", (h.flows.state.value as PlaybackState.Playing).item.trackId)
            assertEquals(0L, (h.flows.state.value as PlaybackState.Playing).positionMs)
            assertEquals("a", h.flows.queueState.value.currentTrackId)
            assertEquals(1, h.engines.created.size)
            assertTrue(engine.crossfades.isEmpty())
            assertNull(h.flows.artworkCrossfade.value)
        }
    }

    @Test
    fun `repeat one off also follows normal ended fallback on both engines`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, false)
            val outgoing = h.engine
            outgoing.position = 180_000L
            outgoing.emit(EngineEvent.Ended)
            h.tick()
            assertTrue(outgoing.crossfades.isEmpty())
            assertTrue(outgoing.closed)
            assertEquals(2, h.engines.created.size)
            assertEquals("a", h.engine.preparedItem?.trackId)
            assertEquals("a", h.flows.queueState.value.currentTrackId)
            assertEquals(0L, h.engine.position)
            assertEquals(listOf(4_000L to false), h.engine.crossfadeSettingsCalls)
            assertNull(h.flows.artworkCrossfade.value)
        }
    }

    @Test
    fun `repeat one on starts the existing fade on both engines`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, true)
            h.engine.position = 176_000L
            h.tick()
            assertEquals(listOf(4_000L), h.engine.crossfades)
            assertNotNull(h.flows.artworkCrossfade.value)
            assertEquals("/art/a.jpg", h.flows.artworkCrossfade.value?.toArtworkPath)
        }
    }

    @Test
    fun `disabling repeat one crossfade preserves fades between different tracks`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, false)
            h.send(PlaybackService.ActionSetRepeat, repeat = RepeatMode.Off)
            h.engine.position = 176_000L
            h.tick()
            assertEquals(listOf(4_000L), h.engine.crossfades)
            assertEquals("/art/b.jpg", h.flows.artworkCrossfade.value?.toArtworkPath)
        }
    }

    @Test
    fun `prepare and length or repeat setting changes deliver crossfade policy to both engines`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, true)
            assertEquals(listOf(4_000L to true), h.engine.crossfadeSettingsCalls)
            h.coordinator.onPlaybackSettings(4, true, false)
            assertEquals(listOf(4_000L to true, 4_000L to false), h.engine.crossfadeSettingsCalls)
            h.engine.position = 176_000L
            h.tick()
            assertTrue(h.engine.crossfades.isEmpty())
            h.coordinator.onPlaybackSettings(6, true, false)
            h.coordinator.onPlaybackSettings(6, true, true)
            assertEquals(listOf(4_000L to true, 4_000L to false, 6_000L to false, 6_000L to true), h.engine.crossfadeSettingsCalls)
            h.tick()
            assertFalse(h.engine.crossfades.isEmpty())
        }
    }

    @Test
    fun `identical writes make no engine calls and a skip only change makes one setCrossfade call`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, false)
            val calls = h.engine.calls.toList()
            val settings = h.engine.crossfadeSettingsCalls.toList()
            repeat(2) { h.coordinator.onPlaybackSettings(4, true, false, false) }
            assertFalse(h.coordinator.crossfadeOnSkip)
            h.coordinator.onPlaybackSettings(4, true, false, true)
            assertTrue(h.coordinator.crossfadeOnSkip)
            repeat(2) { h.coordinator.onPlaybackSettings(4, true, false, true) }
            // T060b (contract): the skip flag reaches the engine through exactly one setCrossfade call.
            val setCrossfadeLog = if (kind == EngineKind.Native) emptyList() else listOf("setCrossfade")
            assertEquals(calls + setCrossfadeLog, h.engine.calls)
            assertEquals(settings + (4_000L to false), h.engine.crossfadeSettingsCalls)
            assertEquals(true, h.engine.crossfadeOnSkipCalls.last())
            assertNull(h.flows.artworkCrossfade.value)
        }
    }

    @Test
    fun `default policy preserves repeat fade and duplicate updates preserve running artwork`() = runTest {
        EngineKind.entries.forEach { kind ->
            val h = start(this, kind, true)
            h.coordinator.onPlaybackSettings(4, true)
            assertFalse(h.coordinator.crossfadeOnSkip)
            h.engine.position = 176_000L
            h.tick()
            val artwork = h.flows.artworkCrossfade.value
            assertNotNull(artwork)
            val calls = h.engine.calls.toList()
            val settings = h.engine.crossfadeSettingsCalls.toList()
            h.coordinator.onPlaybackSettings(4, true)
            h.coordinator.onPlaybackSettings(4, true, crossfadeOnSkip = true)
            // The running fade and its artwork stay; the skip flag is delivered for later skips (T060b contract).
            assertEquals(artwork, h.flows.artworkCrossfade.value)
            val setCrossfadeLog = if (kind == EngineKind.Native) emptyList() else listOf("setCrossfade")
            assertEquals(calls + setCrossfadeLog, h.engine.calls)
            assertEquals(settings + (4_000L to true), h.engine.crossfadeSettingsCalls)
        }
    }
}
