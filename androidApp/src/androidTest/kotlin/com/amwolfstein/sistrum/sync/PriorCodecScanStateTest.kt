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
import org.junit.Before
import org.junit.Test

/**
 * A rescan reuses each unchanged M4A's codec instead of reopening the file with
 * MediaExtractor, so the codec written by one scan must come back in the prior state.
 */
class PriorCodecScanStateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "prior-codec-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    @Test fun scannedCodecIsReadBackForTheNextScan() = runBlocking {
        val album = LocalAlbumRef("local:album:a", "Album")
        val artist = LocalArtistRef("local:artist:x", "Artist")
        val tracks = listOf(
            LocalTrack("t-alac", "ALAC", listOf(artist), album, format = "m4a", codec = "alac"),
            LocalTrack("t-aac", "AAC", listOf(artist), album, format = "m4a", codec = "aac"),
        )
        val audio = tracks.associate { it.id to LocalScanAudio(it.id, "/music/${it.id}.m4a", "hash-${it.id}", 10L) }
        store.writeLocalLibrary(LocalLibrarySnapshot(0L, tracks), audio, emptyList())

        val prior = store.priorScanState().tracksByTrackId
        assertEquals("alac", prior.getValue("t-alac").codec)
        assertEquals("aac", prior.getValue("t-aac").codec)
        assertEquals("alac", reusableCodec(prior.getValue("t-alac"), "hash-t-alac"))
    }
}
