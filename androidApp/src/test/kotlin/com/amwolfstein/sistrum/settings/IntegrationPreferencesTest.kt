package com.amwolfstein.sistrum.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IntegrationPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun defaultsMatchTheIntegrationContract() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            temporary.root.resolve("integrations.preferences_pb")
        }
        try {
            val preferences = IntegrationPreferences(store)
            assertEquals(true, preferences.wikipediaEnabled.first())
            assertEquals(false, preferences.deezerEnabled.first())
            assertEquals(false, preferences.motionEnabled.first())
            assertEquals(MotionNetworkMode.WIFI_ONLY, preferences.motionNetworkMode.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unknownNetworkModeUsesWifiOnlyAndCanBeReplaced() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            temporary.root.resolve("integrations.preferences_pb")
        }
        try {
            val preferences = IntegrationPreferences(store)
            for (unknown in listOf("FUTURE_MODE", "", "wifi_only")) {
                store.edit { it[stringPreferencesKey("motion_network_mode")] = unknown }
                assertEquals(MotionNetworkMode.WIFI_ONLY, preferences.motionNetworkMode.first())
            }
            preferences.setMotionNetworkMode(MotionNetworkMode.WIFI_AND_CELLULAR)
            assertEquals(MotionNetworkMode.WIFI_AND_CELLULAR, preferences.motionNetworkMode.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun allSettingsPersistIndependentlyAcrossStoreReopening() = runTest {
        val file = temporary.root.resolve("integrations.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val firstStore = PreferenceDataStoreFactory.create(scope = firstScope) { file }
        try {
            val preferences = IntegrationPreferences(firstStore)
            preferences.setWikipediaEnabled(false)
            preferences.setDeezerEnabled(true)
            assertEquals(false, preferences.wikipediaEnabled.first())
            assertEquals(true, preferences.deezerEnabled.first())
            assertEquals(false, preferences.motionEnabled.first())
            assertEquals(MotionNetworkMode.WIFI_ONLY, preferences.motionNetworkMode.first())
            preferences.setMotionEnabled(true)
            preferences.setMotionNetworkMode(MotionNetworkMode.WIFI_AND_CELLULAR)
        } finally {
            firstScope.cancel()
        }
        testScheduler.runCurrent()
        val secondScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val secondStore = PreferenceDataStoreFactory.create(scope = secondScope) { file }
        try {
            val preferences = IntegrationPreferences(secondStore)
            assertEquals(false, preferences.wikipediaEnabled.first())
            assertEquals(true, preferences.deezerEnabled.first())
            assertEquals(true, preferences.motionEnabled.first())
            assertEquals(MotionNetworkMode.WIFI_AND_CELLULAR, preferences.motionNetworkMode.first())
            assertEquals("WIFI_AND_CELLULAR", secondStore.data.first()[stringPreferencesKey("motion_network_mode")])
            preferences.setWikipediaEnabled(true)
            preferences.setDeezerEnabled(false)
            preferences.setMotionEnabled(false)
            preferences.setMotionNetworkMode(MotionNetworkMode.WIFI_ONLY)
            assertEquals(true, preferences.wikipediaEnabled.first())
            assertEquals(false, preferences.deezerEnabled.first())
            assertEquals(false, preferences.motionEnabled.first())
            assertEquals(MotionNetworkMode.WIFI_ONLY, preferences.motionNetworkMode.first())
        } finally {
            secondScope.cancel()
        }
    }
}
