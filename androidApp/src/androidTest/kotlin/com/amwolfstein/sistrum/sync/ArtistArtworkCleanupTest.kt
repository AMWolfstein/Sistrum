package com.amwolfstein.sistrum.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import com.amwolfstein.sistrum.library.LocalAlbumRef
import com.amwolfstein.sistrum.library.LocalArtistRef
import com.amwolfstein.sistrum.library.LocalLibrarySnapshot
import com.amwolfstein.sistrum.library.LocalTrack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Replacing a custom artist image used to leave the old file behind forever, and there
 * was no way to remove one. Runs against a real Room database and files directory.
 */
class ArtistArtworkCleanupTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    private val artistX = "local:artist:x"
    private val artistY = "local:artist:y"

    @Before fun setUp(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "artist-artwork-test-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
        val album = LocalAlbumRef("local:album:a", "Album")
        store.writeLocalLibrary(
            snapshot = LocalLibrarySnapshot(0L, listOf(
                LocalTrack("t1", "One", listOf(LocalArtistRef(artistX, "X")), album),
                LocalTrack("t2", "Two", listOf(LocalArtistRef(artistY, "Y")), album),
            )),
            audioRows = emptyMap(),
            artworkRows = emptyList(),
        )
        Unit
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    /** Writes an image file the way stageArtistArtwork does and returns its staging value. */
    private fun image(artistId: String, seed: Char): StagedArtistArtwork {
        val hash = seed.toString().repeat(64)
        val relativePath = "artist-artwork/$hash.jpg"
        File(filesDir, relativePath).apply { parentFile?.mkdirs(); writeText(hash) }
        return StagedArtistArtwork(artistId, hash, "image/jpeg", 64, relativePath)
    }

    private fun file(value: StagedArtistArtwork) = File(filesDir, value.relativePath)

    private suspend fun awaitCustomArtwork(artistId: String, expected: Boolean) {
        val matched = withTimeoutOrNull(5_000) { store.artists.first { artists -> artists.single { it.id == artistId }.hasCustomArtwork == expected } }
        assertNotNull("$artistId hasCustomArtwork=$expected", matched)
    }

    @Test fun replacingAnImageDeletesTheOldFile() = runBlocking {
        val first = image(artistX, '1')
        store.stageArtistArtwork(first)
        val second = image(artistX, '2')
        store.stageArtistArtwork(second)
        assertFalse("replaced image file is deleted", file(first).exists())
        assertTrue(file(second).exists())
    }

    @Test fun settingTheSameImageAgainKeepsItsFile() = runBlocking {
        val same = image(artistX, '1')
        store.stageArtistArtwork(same)
        store.stageArtistArtwork(image(artistX, '1'))
        assertTrue(file(same).exists())
    }

    @Test fun anImageSharedWithAnotherArtistSurvivesReplacement() = runBlocking {
        val shared = image(artistX, '1')
        store.stageArtistArtwork(shared)
        store.stageArtistArtwork(image(artistY, '1'))
        store.stageArtistArtwork(image(artistX, '2'))
        assertTrue("still used by the other artist", file(shared).exists())
        store.clearArtistArtwork(artistY)
        assertFalse("deleted once no artist uses it", file(shared).exists())
    }

    @Test fun clearingRemovesTheImageAndItsFile() = runBlocking {
        val value = image(artistX, '1')
        store.stageArtistArtwork(value)
        awaitCustomArtwork(artistX, true)
        store.clearArtistArtwork(artistX)
        assertFalse(file(value).exists())
        awaitCustomArtwork(artistX, false)
        assertEquals(null, store.artists.first().single { it.id == artistX }.artworkPath)
    }

    @Test fun clearingAnArtistWithoutAnImageIsHarmless() = runBlocking {
        store.clearArtistArtwork(artistY)
        awaitCustomArtwork(artistY, false)
    }
}
