package com.amwolfstein.sistrum.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizationPreferencesAutoTest {
    @Test
    fun absentModeOnNewInstallDefaultsToAuto() {
        assertEquals(NormalizationMode.Auto, normalizationModeFrom(null, existingInstall = false))
    }

    @Test
    fun storedAutoIsPreserved() {
        listOf(false, true).forEach { existingInstall ->
            assertEquals(NormalizationMode.Auto, normalizationModeFrom("Auto", existingInstall))
        }
    }

    @Test
    fun playbackPreferenceIdentifiesExistingInstall() {
        assertTrue("Playback preferences identify an existing install", isExistingInstall(true, false))
    }

    @Test
    fun libraryDatabaseIdentifiesExistingInstall() {
        assertTrue("A library database identifies an existing install", isExistingInstall(false, true))
    }
}

class NormalizationPreferencesAutoGuardTest {
    @Test
    fun absentModeOnExistingInstallKeepsTrack() {
        assertEquals(NormalizationMode.Track, normalizationModeFrom(null, existingInstall = true))
    }

    @Test
    fun storedTrackIsPreservedRegardlessOfInstall() {
        listOf(false, true).forEach { existingInstall ->
            assertEquals(NormalizationMode.Track, normalizationModeFrom("Track", existingInstall))
        }
    }

    @Test
    fun storedAlbumIsPreservedRegardlessOfInstall() {
        listOf(false, true).forEach { existingInstall ->
            assertEquals(NormalizationMode.Album, normalizationModeFrom("Album", existingInstall))
        }
    }

    @Test
    fun noPreferencesOrDatabaseIsNewInstall() {
        assertFalse(isExistingInstall(false, false))
    }

    @Test
    fun unknownStoredModeFallsBackToTrack() {
        listOf(false, true).forEach { existingInstall ->
            assertEquals(NormalizationMode.Track, normalizationModeFrom("unknown", existingInstall))
        }
    }
}
