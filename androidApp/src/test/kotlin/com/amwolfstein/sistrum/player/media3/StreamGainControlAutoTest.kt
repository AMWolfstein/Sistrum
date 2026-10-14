package com.amwolfstein.sistrum.player.media3

import com.amwolfstein.sistrum.player.NormalizationMode
import com.amwolfstein.sistrum.player.NormalizationSettings
import com.amwolfstein.sistrum.player.normalization.GainForm
import com.amwolfstein.sistrum.player.normalization.GainInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamGainControlAutoTest {
    private val info = GainInfo(-6f, -3f, 0.5f, 0.6f, GainForm.Measured)
    private val settings = NormalizationSettings(enabled = true, mode = NormalizationMode.Auto, targetLufs = -18f)

    @Test
    fun autoStreamStartInSingleAlbumQueueUsesAlbumGain() {
        val control = StreamGainControl(settings, queueSingleAlbum = { true })
        assertEquals(-3f, control.onStreamStart(info), 0f)
    }

    @Test
    fun queueChangeDoesNotRetargetPlayingStream() {
        var singleAlbum = true
        val control = StreamGainControl(settings, queueSingleAlbum = { singleAlbum })
        val playingGain = control.onStreamStart(info)
        singleAlbum = false
        // A queue edit alone makes no gain-control call; the caller keeps the last returned gain.
        assertEquals(-3f, playingGain, 0f)
        assertEquals(-6f, control.onStreamStart(info), 0f)
        singleAlbum = true
        assertEquals(-3f, control.onStreamStart(info), 0f)
    }

    @Test
    fun crossfadePlayersKeepOwnStartGains() {
        var singleAlbum = true
        val controlA = StreamGainControl(settings, queueSingleAlbum = { singleAlbum })
        val controlB = StreamGainControl(settings, queueSingleAlbum = { singleAlbum })
        val gainA = controlA.onStreamStart(info)
        singleAlbum = false
        val gainB = controlB.onStreamStart(info)
        val repeatedGainA = controlA.setNormalization(settings)
        assertEquals(-3f, gainA, 0f)
        assertEquals(-6f, gainB, 0f)
        assertEquals(-3f, repeatedGainA!!, 0f)
    }

    @Test
    fun settingsChangeReResolvesAutoImmediately() {
        val control = StreamGainControl(settings.copy(mode = NormalizationMode.Track), queueSingleAlbum = { true })
        assertEquals(-6f, control.onStreamStart(info), 0f)
        assertEquals(-3f, control.setNormalization(settings)!!, 0f)
    }
}

class StreamGainControlAutoGuardTest {
    private val info = GainInfo(-6f, -3f, 0.5f, 0.6f, GainForm.Measured)
    private val settings = NormalizationSettings(enabled = true, mode = NormalizationMode.Auto, targetLufs = -18f)

    @Test
    fun autoWithMixedQueueUsesTrackGain() {
        assertEquals(-6f, StreamGainControl(settings, queueSingleAlbum = { false }).onStreamStart(info), 0f)
    }

    @Test
    fun autoWithoutAlbumGainUsesTrackGain() {
        val control = StreamGainControl(settings, queueSingleAlbum = { true })
        assertEquals(-6f, control.onStreamStart(info.copy(albumGainDb = null)), 0f)
    }

    @Test
    fun trackIgnoresQueueFlag() {
        assertModeIgnoresQueueFlag(NormalizationMode.Track, -6f)
    }

    @Test
    fun albumIgnoresQueueFlag() {
        assertModeIgnoresQueueFlag(NormalizationMode.Album, -3f)
    }

    private fun assertModeIgnoresQueueFlag(mode: NormalizationMode, expectedGain: Float) {
        val fixed = settings.copy(mode = mode)
        val baseline = StreamGainControl(fixed).onStreamStart(info)
        assertEquals(expectedGain, baseline, 0f)
        listOf(false, true).forEach { flag ->
            val control = StreamGainControl(fixed, queueSingleAlbum = { flag })
            assertEquals(baseline, control.onStreamStart(info), 0f)
            assertEquals(baseline, control.setNormalization(fixed)!!, 0f)
        }
    }
}
