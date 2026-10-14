package com.amwolfstein.sistrum.player

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizationModeMigrationTest {
    @Test
    fun freshInstallDefaultsToAuto() {
        val migrated = migrateNormalizationMode(emptyPreferences(), libraryDatabaseExists = false)
        assertEquals("Auto", migrated[NormalizationModeKey])
    }

    @Test
    fun playbackPreferencesKeepExistingInstallOnTrack() {
        val preferences = preferencesOf(booleanPreferencesKey("blend_artwork_during_crossfade") to false)
        val migrated = migrateNormalizationMode(preferences, libraryDatabaseExists = false)
        assertEquals("Track", migrated[NormalizationModeKey])
        assertEquals(false, migrated[booleanPreferencesKey("blend_artwork_during_crossfade")])
    }

    @Test
    fun libraryDatabaseKeepsExistingInstallOnTrack() {
        val migrated = migrateNormalizationMode(emptyPreferences(), libraryDatabaseExists = true)
        assertEquals("Track", migrated[NormalizationModeKey])
    }

    @Test
    fun storedModeIsUnchanged() {
        listOf("Auto", "Track", "Album", "unknown").forEach { mode ->
            listOf(false, true).forEach { databaseExists ->
                val preferences = preferencesOf(NormalizationModeKey to mode)
                assertEquals(preferences, migrateNormalizationMode(preferences, databaseExists))
            }
        }
    }
}
