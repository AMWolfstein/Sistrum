package com.amwolfstein.sistrum.player

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPreferencesTest {
    @Test
    fun `empty preferences preserve existing defaults and default repeat on skip off`() {
        assertEquals(CrossfadeSettings(0, 4, true, true, false), crossfadeSettingsOf(preferencesOf()))
    }

    @Test
    fun `both toggles round trip independently using persistent keys`() {
        val preferences = mutablePreferencesOf()
        listOf(false, true).forEach { repeat ->
            listOf(true, false).forEach { skip ->
                preferences.applyRepeatOneCrossfade(repeat)
                preferences.applyCrossfadeOnSkip(skip)
                assertEquals(repeat, preferences[booleanPreferencesKey("crossfade_repeat_one")])
                assertEquals(skip, preferences[booleanPreferencesKey("crossfade_on_skip")])
                assertEquals(CrossfadeSettings(0, 4, true, repeat, skip), crossfadeSettingsOf(preferences))
            }
        }
        assertEquals(
            CrossfadeSettings(0, 4, true, false, true),
            crossfadeSettingsOf(preferencesOf(
                booleanPreferencesKey("crossfade_repeat_one") to false,
                booleanPreferencesKey("crossfade_on_skip") to true,
            )),
        )
    }

    @Test
    fun `disabling and restoring crossfade keeps stored toggle values and last length`() {
        val preferences = mutablePreferencesOf()
        preferences.applyRepeatOneCrossfade(false)
        preferences.applyCrossfadeOnSkip(true)
        preferences.applyCrossfadeSeconds(7)
        assertEquals(CrossfadeSettings(7, 7, true, false, true), crossfadeSettingsOf(preferences))
        preferences.applyCrossfadeSeconds(0)
        assertEquals(CrossfadeSettings(0, 7, true, false, true), crossfadeSettingsOf(preferences))
        preferences.applyCrossfadeSeconds(crossfadeSettingsOf(preferences).lastEnabledSeconds)
        assertEquals(CrossfadeSettings(7, 7, true, false, true), crossfadeSettingsOf(preferences))
    }

    @Test
    fun `existing fields still map stored values and clamp lengths`() {
        assertEquals(
            CrossfadeSettings(12, 1, false, true, false),
            crossfadeSettingsOf(preferencesOf(
                intPreferencesKey("crossfade_seconds") to 30,
                intPreferencesKey("last_enabled_crossfade_seconds") to -1,
                booleanPreferencesKey("blend_artwork_during_crossfade") to false,
            )),
        )
    }
}
