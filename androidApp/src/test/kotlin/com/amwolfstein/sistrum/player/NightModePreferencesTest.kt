package com.amwolfstein.sistrum.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NightModePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun defaultOffAndIndependentSettingsSurviveReopeningTheStore() = runTest {
        val file = temporary.root.resolve("playback.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = firstScope) { file }
        val preferences = PlaybackPreferences(store)
        try {
            assertFalse(preferences.nightMode.first())
            preferences.setCrossfadeSeconds(7)
            preferences.setCrossfadeOnSkip(true)
            preferences.setNightMode(true)
            assertTrue(preferences.nightMode.first())
            assertEquals(true, store.data.first()[booleanPreferencesKey("night_mode")])
            assertEquals(CrossfadeSettings(7, 7, true, true, true), preferences.settings.first())
        } finally {
            firstScope.cancel()
        }
        testScheduler.runCurrent()

        val secondScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val reopened = PlaybackPreferences(PreferenceDataStoreFactory.create(scope = secondScope) { file })
        try {
            assertTrue("saved ON is read by a new preference instance", reopened.nightMode.first())
            reopened.setNightMode(false)
            assertFalse(reopened.nightMode.first())
            assertEquals(CrossfadeSettings(7, 7, true, true, true), reopened.settings.first())
        } finally {
            secondScope.cancel()
        }
    }
}
