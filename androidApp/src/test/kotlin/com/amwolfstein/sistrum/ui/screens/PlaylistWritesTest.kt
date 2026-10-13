package com.amwolfstein.sistrum.ui.screens

import android.database.sqlite.SQLiteFullException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/** Playlist writes used to run unguarded in viewModelScope, so a Room failure crashed the app. */
class PlaylistWritesTest {

    @Test fun `a successful write reports success and no failure`() = runTest {
        val failures = mutableListOf<Exception>()
        var wrote = false
        val saved = runPlaylistWrite(onFailure = { failures += it }) { wrote = true }
        assertTrue(saved)
        assertTrue(wrote)
        assertEquals(emptyList(), failures)
    }

    @Test fun `a database failure is reported instead of escaping`() = runTest {
        val failures = mutableListOf<Exception>()
        val diskFull = SQLiteFullException("database or disk is full")
        val saved = runPlaylistWrite(onFailure = { failures += it }) { throw diskFull }
        assertFalse(saved)
        assertSame(diskFull, failures.single())
    }

    @Test fun `a rejected mutation is reported instead of escaping`() = runTest {
        val failures = mutableListOf<Exception>()
        val saved = runPlaylistWrite(onFailure = { failures += it }) { require(false) { "Invalid playlist mutation" } }
        assertFalse(saved)
        assertTrue(failures.single() is IllegalArgumentException)
    }

    @Test fun `cancellation still propagates`() = runTest {
        val failures = mutableListOf<Exception>()
        assertFailsWith<CancellationException> {
            runPlaylistWrite(onFailure = { failures += it }) { throw CancellationException("left the screen") }
        }
        assertEquals(emptyList(), failures)
    }

    @Test fun `work after a failed write can be skipped`() = runTest {
        var lovedOnLastFm = false
        val saved = runPlaylistWrite(onFailure = {}) { throw SQLiteFullException() }
        if (saved) lovedOnLastFm = true
        assertFalse(lovedOnLastFm)
    }
}
