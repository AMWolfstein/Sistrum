package com.amwolfstein.sistrum.player.normalization

import kotlin.random.Random
import com.amwolfstein.sistrum.player.NormalizationMode
import com.amwolfstein.sistrum.player.NormalizationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoModeTest {
    private val info = GainInfo(-6f, -3f, 0.5f, 0.6f, GainForm.Measured)
    private val settings = NormalizationSettings(enabled = true, targetLufs = -18f)

    @Test
    fun singleAlbumInOrder() {
        assertTrue("All queue items belong to album x", queueIsSingleAlbum(listOf("x", "x", "x")))
        assertEquals(NormalizationMode.Album, resolveNormalizationMode(NormalizationMode.Auto, true))
    }

    @Test
    fun singleAlbumShuffled() {
        val items = listOf("a1" to "x", "a2" to "x", "a3" to "x").shuffled(Random(42))
        assertTrue("Shuffle preserves a single album", queueIsSingleAlbum(items.map { it.second }))
    }

    @Test
    fun oneItemQueueIsSingleAlbum() {
        assertTrue("A one-item queue is a single album", queueIsSingleAlbum(listOf("x")))
    }

    @Test
    fun autoWithAlbumGainUsesAlbumGain() {
        val auto = settings.copy(mode = resolveNormalizationMode(NormalizationMode.Auto, true))
        val album = settings.copy(mode = NormalizationMode.Album)
        assertEquals(itemGainDb(info, album, 0f), itemGainDb(info, auto, 0f), 0f)
    }
}

class AutoModeGuardTest {
    private val info = GainInfo(-6f, -3f, 0.5f, 0.6f, GainForm.Measured)
    private val settings = NormalizationSettings(enabled = true, targetLufs = -18f)

    @Test
    fun mixedAlbumsAreNotSingleAlbum() {
        assertFalse(queueIsSingleAlbum(listOf("x", "y")))
    }

    @Test
    fun adjacentSameAlbumItemsDoNotMakeMixedQueueSingleAlbum() {
        assertFalse(queueIsSingleAlbum(listOf("x", "x", "y")))
    }

    @Test
    fun blankAlbumIdsAreNotSingleAlbum() {
        assertFalse(queueIsSingleAlbum(listOf("", "")))
    }

    @Test
    fun emptyQueueIsNotSingleAlbum() {
        assertFalse(queueIsSingleAlbum(emptyList()))
    }

    @Test
    fun autoWithMixedQueueResolvesTrack() {
        assertEquals(NormalizationMode.Track, resolveNormalizationMode(NormalizationMode.Auto, false))
    }

    @Test
    fun trackIgnoresSingleAlbumQueue() {
        assertEquals(NormalizationMode.Track, resolveNormalizationMode(NormalizationMode.Track, true))
    }

    @Test
    fun albumIgnoresMixedQueue() {
        assertEquals(NormalizationMode.Album, resolveNormalizationMode(NormalizationMode.Album, false))
    }

    @Test
    fun autoWithoutAlbumGainFallsBackToTrackGain() {
        val auto = settings.copy(mode = resolveNormalizationMode(NormalizationMode.Auto, true))
        assertEquals(-6f, itemGainDb(info.copy(albumGainDb = null), auto, 0f), 0f)
    }

    @Test
    fun trackGainIsUnchanged() {
        assertEquals(-6f, itemGainDb(info, settings.copy(mode = NormalizationMode.Track), 0f), 0f)
    }

    @Test
    fun albumGainIsUnchanged() {
        assertEquals(-3f, itemGainDb(info, settings.copy(mode = NormalizationMode.Album), 0f), 0f)
    }
}
