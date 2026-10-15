package com.amwolfstein.sistrum.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SilencePlaybackPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun defaultOnAndSavedOffSurvivesReopenWithoutChangingOtherOptions() = runTest {
        val file = temporary.root.resolve("playback.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val preferences = PlaybackPreferences(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        try {
            assertTrue(preferences.settings.first().crossfadeSkipSilence)
            preferences.setCrossfadeSeconds(7)
            preferences.setNightMode(true)
            preferences.setCrossfadeSkipSilence(false)
            assertFalse(preferences.settings.first().crossfadeSkipSilence)
        } finally { firstScope.cancel() }
        testScheduler.runCurrent()
        val secondScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val reopened = PlaybackPreferences(PreferenceDataStoreFactory.create(scope = secondScope) { file })
        try {
            assertEquals(CrossfadeSettings(7, 7, true, true, false, false), reopened.settings.first())
            assertTrue(reopened.nightMode.first())
            reopened.setCrossfadeSkipSilence(true)
            assertTrue(reopened.settings.first().crossfadeSkipSilence)
        } finally { secondScope.cancel() }
    }
}
