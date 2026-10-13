package com.amwolfstein.sistrum.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import com.amwolfstein.sistrum.library.LocalAlbumRef
import com.amwolfstein.sistrum.library.LocalArtistRef
import com.amwolfstein.sistrum.library.LocalLibrarySnapshot
import com.amwolfstein.sistrum.library.LocalTrack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A scan that finds no tracks (most often an empty whitelist) used to replace the whole
 * library and delete its album covers. It must now leave a non-empty library untouched.
 */
class EmptyScanGuardTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "empty-scan-guard-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    private val artworkKey = albumArtworkKey("a")
    private val coverPath = "artwork/a.jpg"

    private fun snapshot(vararg ids: String): LocalLibrarySnapshot {
        val album = LocalAlbumRef("local:album:a", "Album", artworkKey = artworkKey)
        val artist = LocalArtistRef("local:artist:x", "Artist")
        return LocalLibrarySnapshot(0L, ids.map { LocalTrack(it, it, listOf(artist), album, artworkKey = artworkKey) })
    }

    private suspend fun writeWithCover(snapshot: LocalLibrarySnapshot): Boolean {
        File(filesDir, coverPath).apply { parentFile?.mkdirs(); if (!exists()) writeText("cover") }
        return store.writeLocalLibrary(
            snapshot = snapshot,
            audioRows = emptyMap(),
            artworkRows = listOf(LocalScanArtwork(artworkKey, coverPath, "0".repeat(64), 5)),
        )
    }

    private suspend fun activeTrackCount() = database.syncDao().activeTrackCount()

    @Test fun emptyScanOnAnEmptyLibraryProceeds() = runBlocking {
        assertTrue(store.writeLocalLibrary(snapshot(), audioRows = emptyMap(), artworkRows = emptyList()))
        assertEquals(0, activeTrackCount())
    }

    @Test fun emptyScanOnANonEmptyLibraryIsRefusedAndKeepsEverything() = runBlocking {
        assertTrue(writeWithCover(snapshot("t1", "t2", "t3")))
        assertEquals(3, activeTrackCount())

        assertFalse("an empty scan must not replace a non-empty library", store.writeLocalLibrary(snapshot(), emptyMap(), emptyList()))
        assertEquals("existing tracks are kept", 3, activeTrackCount())
        assertTrue("existing album cover file is kept", File(filesDir, coverPath).exists())
        assertEquals(setOf("t1", "t2", "t3"), database.syncDao().activeTrackScanState().map { it.trackId }.toSet())
    }

    @Test fun nonEmptyRescanStillReplacesTheLibrary() = runBlocking {
        assertTrue(writeWithCover(snapshot("t1", "t2", "t3")))
        assertTrue(writeWithCover(snapshot("t4")))
        assertEquals(1, activeTrackCount())
    }
}
