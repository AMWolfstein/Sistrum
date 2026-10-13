package me.misa198.airmedy.player.media3

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import me.misa198.airmedy.player.NormalizationMode
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.normalization.GainForm
import me.misa198.airmedy.player.normalization.GainInfo
import me.misa198.airmedy.player.normalization.itemGainDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamGainControlTest {
    private val info = GainInfo(-6f, -3f, 0.5f, 0.6f, GainForm.Measured)
    private val settings = NormalizationSettings(enabled = true, targetLufs = -18f)

    @Test
    fun untaggedStreamUsesPreampFromSettings() {
        val untagged = GainInfo(null, null, null, null, GainForm.None)
        val withPreamp = settings.copy(untaggedPreampDb = 3f)
        assertEquals(3f, StreamGainControl(withPreamp).onStreamStart(untagged), 0f)
    }

    @Test
    fun preampOnlySettingsChangeRetargetsCurrentStream() {
        val untagged = GainInfo(null, null, null, null, GainForm.None)
        val control = StreamGainControl(settings)
        assertEquals(0f, control.onStreamStart(untagged), 0f)
        assertEquals(3f, control.setNormalization(settings.copy(untaggedPreampDb = 3f))!!, 0f)
    }

    @Test
    fun streamStartUsesItemGainMath() {
        val control = StreamGainControl(settings)
        assertEquals(itemGainDb(info, settings, 0f), control.onStreamStart(info), 0f)
    }

    @Test
    fun settingsBeforeAnyStreamApplyAtItsStart() {
        val control = StreamGainControl()
        assertNull(control.setNormalization(settings))
        assertEquals(itemGainDb(info, settings, 0f), control.onStreamStart(info), 0f)
    }

    @Test
    fun settingsAfterStreamStartRetargetItsInfo() {
        val control = StreamGainControl(settings)
        control.onStreamStart(info)
        val album = settings.copy(mode = NormalizationMode.Album)
        assertEquals(itemGainDb(info, album, 0f), control.setNormalization(album)!!, 0f)
    }

    @Test
    fun repeatedSettingsKeepTheStoredSource() {
        val control = StreamGainControl(settings)
        control.onStreamStart(info)
        control.setNormalization(settings.copy(targetLufs = -14f))
        val album = settings.copy(mode = NormalizationMode.Album, targetLufs = -16f)
        assertEquals(itemGainDb(info, album, 0f), control.setNormalization(album)!!, 0f)
        assertEquals(GainForm.Measured, info.form)
    }

    @Test
    fun nextStreamReplacesTheInfo() {
        val control = StreamGainControl(settings)
        control.onStreamStart(info)
        val next = GainInfo(2f, 1f, null, null, GainForm.ReplayGain)
        assertEquals(itemGainDb(next, settings, 0f), control.onStreamStart(next), 0f)
        val album = settings.copy(mode = NormalizationMode.Album)
        assertEquals(itemGainDb(next, album, 0f), control.setNormalization(album)!!, 0f)
    }

    @Test
    fun disabledNormalizationIsUnity() {
        val control = StreamGainControl(settings.copy(enabled = false))
        assertEquals(0f, control.onStreamStart(info), 0f)
        control.setNormalization(settings)
        assertEquals(0f, control.setNormalization(settings.copy(enabled = false))!!, 0f)
    }

    @Test
    fun disablingClipPreventionRemovesPeakCap() {
        val control = StreamGainControl(settings)
        val positive = GainInfo(6f, null, 1f, null, GainForm.ReplayGain)
        assertEquals(0f, control.onStreamStart(positive), 0f)
        assertEquals(6f, control.setNormalization(settings.copy(preventClip = false))!!, 0f)
    }

    @Test(timeout = 10_000)
    fun concurrentStreamStartsAndSettingsStayConsistent() {
        val control = StreamGainControl()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val finalInfo = GainInfo(-8f, -2f, null, null, GainForm.R128)
        val finalSettings = settings.copy(mode = NormalizationMode.Album)
        fun worker(block: () -> Unit) = Thread {
            ready.countDown()
            try {
                start.await()
                block()
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }.apply { start() }
        val streams = worker {
            repeat(2_000) { control.onStreamStart(if (it % 2 == 0) info else finalInfo) }
            control.onStreamStart(finalInfo)
        }
        val changes = worker {
            repeat(2_000) { control.setNormalization(settings.copy(enabled = it % 2 == 0)) }
            control.setNormalization(finalSettings)
        }
        ready.await()
        start.countDown()
        streams.join()
        changes.join()
        failure.get()?.let { throw AssertionError("Concurrent gain control failed", it) }
        // Both threads leave known final values, regardless of which one finished first.
        assertEquals(itemGainDb(finalInfo, finalSettings, 0f), control.setNormalization(finalSettings)!!, 0f)
        assertEquals(itemGainDb(finalInfo, finalSettings, 0f), control.onStreamStart(finalInfo), 0f)
    }
}
