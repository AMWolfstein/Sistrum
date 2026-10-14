package com.amwolfstein.sistrum.player

import kotlinx.coroutines.test.runTest
import com.amwolfstein.sistrum.player.engine.EngineKind
import com.amwolfstein.sistrum.player.engine.ItemGain
import com.amwolfstein.sistrum.player.fakes.PlaybackCoordinatorHarness
import com.amwolfstein.sistrum.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorAutoModeTest {
    @Test
    fun publishesSingleAlbumForOneAlbumQueue() = runTest {
        val harness = PlaybackCoordinatorHarness(this).withAlbumFixtures(EngineKind.Media3)
        var callsAtPrepare = emptyList<String>()
        var flagsAtPrepare = emptyList<Boolean>()
        harness.engines.onPrepare = {
            callsAtPrepare = harness.engine.calls.toList()
            flagsAtPrepare = harness.engine.queueSingleAlbumCalls.toList()
        }
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a1", "a2", "a3"))
        assertQueueFlag(harness, true)
        assertEquals(listOf(true), flagsAtPrepare.takeLast(1))
        assertTrue("setQueueSingleAlbum must precede prepare", callsAtPrepare.indexOf("setQueueSingleAlbum") < callsAtPrepare.indexOf("prepare"))
    }

    @Test
    fun publishesMixedForTwoAlbums() = runTest {
        val harness = PlaybackCoordinatorHarness(this).withAlbumFixtures(EngineKind.Media3)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a1", "b1"))
        assertQueueFlag(harness, false)
    }

    @Test
    fun addingAnotherAlbumsTrackPublishesFalse() = runTest {
        val harness = PlaybackCoordinatorHarness(this).withAlbumFixtures(EngineKind.Media3)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a1", "a2", "a3"))
        val beforeAdd = harness.engine.queueSingleAlbumCalls.size
        harness.send(PlaybackService.ActionAppend, trackIds = listOf("b1"))
        assertTrue("Adding a track must publish the queue album flag", harness.engine.queueSingleAlbumCalls.size > beforeAdd)
        assertQueueFlag(harness, false)
        val beforeRemove = harness.engine.queueSingleAlbumCalls.size
        harness.send(PlaybackService.ActionRemove, trackIds = listOf("b1"))
        assertTrue("Removing a track must publish the queue album flag", harness.engine.queueSingleAlbumCalls.size > beforeRemove)
        assertQueueFlag(harness, true)
    }

    @Test
    fun shufflePreservesSingleAlbum() = runTest {
        val harness = PlaybackCoordinatorHarness(this).withAlbumFixtures(EngineKind.Media3)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a1", "a2", "a3"))
        val beforeShuffle = harness.engine.queueSingleAlbumCalls.size
        harness.send(PlaybackService.ActionSetShuffle, enabled = true)
        assertTrue("Shuffle must publish the queue album flag", harness.engine.queueSingleAlbumCalls.size > beforeShuffle)
        assertQueueFlag(harness, true)
    }

    private fun assertQueueFlag(harness: PlaybackCoordinatorHarness, expected: Boolean) {
        assertTrue("setQueueSingleAlbum must be called", harness.engine.queueSingleAlbumCalls.isNotEmpty())
        assertEquals(expected, harness.engine.queueSingleAlbumCalls.last())
    }
}

class PlaybackCoordinatorAutoModeGuardTest {
    @Test
    fun nativeTrackAndAlbumSettingsFlowIsUnchanged() = runTest {
        val harness = PlaybackCoordinatorHarness(this).withAlbumFixtures(EngineKind.Native)
        val track = NormalizationSettings(enabled = true, mode = NormalizationMode.Track, targetLufs = -18f)
        val album = track.copy(mode = NormalizationMode.Album)
        harness.coordinator.onNormalizationSettings(track)
        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a1", "a2", "a3"))
        assertTrue(harness.flows.state.value is PlaybackState.Playing)
        assertEquals(listOf(track), harness.engine.normalizationCalls)
        assertTrue(harness.engine.calls.indexOf("setNormalization") < harness.engine.calls.indexOf("prepare"))
        assertEquals(2, harness.library.activeAnalysesCalls)
        assertEquals(2, harness.library.disableNormalizationCalls)
        harness.coordinator.onNormalizationSettings(album)
        assertEquals(listOf(track, album), harness.engine.normalizationCalls)
        assertEquals(listOf(ItemGain.Unity to ItemGain.Unity), harness.engine.gainCalls)
        assertEquals(4, harness.library.activeAnalysesCalls)
        assertEquals(4, harness.library.disableNormalizationCalls)
    }
}

private fun PlaybackCoordinatorHarness.withAlbumFixtures(kind: EngineKind): PlaybackCoordinatorHarness = apply {
    engines.kind = kind
    val albumIds = mapOf("a1" to "A", "a2" to "A", "a3" to "A", "b1" to "B")
    resolver.albumIds.putAll(albumIds)
    library.libraryTracks = albumIds.map { (id, albumId) ->
        LibraryTrack(id = id, title = "Title $id", artists = "Artist", album = albumId, albumId = albumId)
    }
}
