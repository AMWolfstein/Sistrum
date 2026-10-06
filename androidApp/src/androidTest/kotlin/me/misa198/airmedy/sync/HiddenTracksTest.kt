package me.misa198.airmedy.sync

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.library.LocalAlbumRef
import me.misa198.airmedy.library.LocalArtistRef
import me.misa198.airmedy.library.LocalLibrarySnapshot
import me.misa198.airmedy.library.LocalTrack
import me.misa198.airmedy.ui.screens.isFavorite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FR-065a: a file the decoder registry skips must be hidden, not deleted. Its row (and its
 * play count, lyrics and prior scan state) is kept, but it is left out of every library view,
 * lookup and search. When a decoder becomes available it reappears with its history intact.
 */
class HiddenTracksTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    private val visibleAlbum = LocalAlbumRef("local:album:visible", "Visible Album")
    private val hiddenAlbum = LocalAlbumRef("local:album:hidden", "Hidden Album")
    private val visibleArtist = LocalArtistRef("local:artist:visible", "Visible Artist")
    private val hiddenArtist = LocalArtistRef("local:artist:hidden", "Hidden Artist")

    private val trackA = LocalTrack("A", "Visible Title", listOf(visibleArtist), visibleAlbum)
    private val trackB = LocalTrack("B", "Hidden Title", listOf(hiddenArtist), hiddenAlbum)
    private val hiddenTrackB = trackB.copy(decoderUnavailable = "unsupported format (WMA)")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "hidden-tracks-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    private suspend fun write(vararg tracks: LocalTrack): Boolean {
        val audio = tracks.associate { track ->
            track.id to LocalScanAudio(track.id, "/music/${track.id}.wma", "hash-${track.id}", 100L)
        }
        return store.writeLocalLibrary(
            snapshot = LocalLibrarySnapshot(0L, tracks.toList()),
            audioRows = audio,
            artworkRows = emptyList(),
        )
    }

    @Test fun hiddenTrackIsLeftOutOfEveryLibraryView() = runBlocking {
        assertTrue(write(trackA, hiddenTrackB))

        assertEquals(listOf("A"), store.tracks.first().map { it.id })
        assertTrue("hidden album must not be listed", store.albums.first().none { it.title == "Hidden Album" })
        assertTrue("hidden artist must not be listed", store.artists.first().none { it.name == "Hidden Artist" })
    }

    @Test fun hiddenTrackIsAbsentFromLookup() = runBlocking {
        write(trackA, hiddenTrackB)

        assertNull(store.trackById("B"))
        assertNotNull(store.trackById("A"))
    }

    @Test fun hiddenTrackIsAbsentFromSearch() = runBlocking {
        // Positive controls: with B admitted, the single word "Hidden" finds its track, album and
        // artist documents, proving a miss below is the hiding and not the query. (Single words on
        // purpose: the FTS match joins words with AND, which the platform's FTS4 query syntax may not support.)
        write(trackA, trackB)
        assertTrue(
            "control: an admitted track is found by its title",
            store.searchCandidates("Hidden").first().any { it.entityType == "track" && it.entityId == "B" },
        )
        assertTrue(
            "control: an admitted album is found by its title",
            store.searchCandidates("Hidden").first().any { it.entityType == "album" && it.entityId == hiddenAlbum.id },
        )
        assertTrue(
            "control: an admitted artist is found by its name",
            store.searchCandidates("Hidden").first().any { it.entityType == "artist" && it.entityId == hiddenArtist.id },
        )

        // Hidden: every document for B disappears from search.
        write(trackA, hiddenTrackB)
        assertTrue(
            "hidden track B must not be a search candidate",
            store.searchCandidates("Hidden").first().none { it.entityType == "track" && it.entityId == "B" },
        )
        assertTrue(
            "hidden album ${hiddenAlbum.id} must not be a search candidate",
            store.searchCandidates("Hidden").first().none { it.entityType == "album" && it.entityId == hiddenAlbum.id },
        )
        assertTrue(
            "hidden artist ${hiddenArtist.id} must not be a search candidate",
            store.searchCandidates("Hidden").first().none { it.entityType == "artist" && it.entityId == hiddenArtist.id },
        )
        assertTrue(
            "visible track A is still a search candidate",
            store.searchCandidates("Visible").first().any { it.entityType == "track" && it.entityId == "A" },
        )
    }

    @Test fun hiddenTrackInAVisibleAlbumLeavesTheAlbumVisible() = runBlocking {
        val sharedAlbum = LocalAlbumRef("local:album:shared", "Shared Album")
        val sharedArtist = LocalArtistRef("local:artist:shared", "Shared Artist")
        val trackWithSharedAlbum = LocalTrack("A", "Visible Title", listOf(sharedArtist), sharedAlbum)
        val hiddenSibling = LocalTrack(
            "C",
            "Hidden Sibling",
            listOf(sharedArtist),
            sharedAlbum,
            decoderUnavailable = "unsupported format (WMA)",
        )

        write(trackWithSharedAlbum, hiddenSibling)

        val shared = store.albums.first().singleOrNull { it.title == "Shared Album" }
        assertNotNull("an album with a visible member stays listed", shared)
        assertEquals(
            "only the visible sibling is listed as a track",
            listOf("A"),
            store.tracks.first().map { it.id },
        )
        // LibraryAlbum exposes no track count or member list; the app derives an album's
        // tracks by filtering the visible tracks on the album id.
        val members = store.tracks.first().filter { it.albumId == shared!!.id }
        assertTrue("hidden sibling C is not a member of the visible album", members.none { it.id == "C" })
    }

    @Test fun favoriteSurvivesHideAndReappear() = runBlocking {
        write(trackA, trackB)
        store.setFavorite("B", true)
        assertTrue("B is a favorite while visible", store.tracks.first().single { it.id == "B" }.isFavorite())

        write(trackA, hiddenTrackB)
        write(trackA, trackB)

        assertTrue(
            "B is still a favorite after being hidden and reappearing",
            store.tracks.first().single { it.id == "B" }.isFavorite(),
        )
    }

    @Test fun hiddenTrackIsNotCountedAsActive() = runBlocking {
        write(trackA, hiddenTrackB)

        assertEquals(1, database.syncDao().activeTrackCount())
    }

    @Test fun hiddenTrackKeepsItsPlayCount() = runBlocking {
        write(trackA, trackB)
        repeat(3) { database.syncDao().incrementActiveTrackPlayCount("B") }
        write(trackA, hiddenTrackB)
        write(trackA, trackB)

        assertEquals(3, store.tracks.first().single { it.id == "B" }.playCount)
    }

    @Test fun hiddenTrackIsRetainedInThePriorScanState() = runBlocking {
        write(trackA, hiddenTrackB)

        assertTrue(store.priorScanState().tracksByTrackId.containsKey("B"))
    }

    @Test fun hiddenTrackKeepsItsLyrics() = runBlocking {
        write(trackA, trackB)
        store.saveProviderLyrics("B", "kept lyrics", "test")
        write(trackA, hiddenTrackB)

        assertNotNull(store.providerLyrics("B").first())
    }

    @Test fun migration13To14AddsNullableUnavailableReason() {
        val db = createV13Database()
        try {
            db.execSQL(
                "CREATE TABLE sync_tracks (planId TEXT NOT NULL, trackId TEXT NOT NULL, title TEXT NOT NULL, " +
                    "artists TEXT NOT NULL, album TEXT NOT NULL DEFAULT '', albumId TEXT NOT NULL DEFAULT '', " +
                    "artworkKey TEXT, playCount INTEGER NOT NULL DEFAULT 0, createdAt TEXT NOT NULL DEFAULT '', " +
                    "discNumber INTEGER NOT NULL DEFAULT 0, trackNumber INTEGER NOT NULL DEFAULT 0, " +
                    "syncOrder INTEGER NOT NULL DEFAULT 0, rawJson TEXT NOT NULL, PRIMARY KEY(planId, trackId))",
            )
            db.execSQL(
                "INSERT INTO sync_tracks (planId, trackId, title, artists, album, albumId, artworkKey, playCount, " +
                    "createdAt, discNumber, trackNumber, syncOrder, rawJson) " +
                    "VALUES ('p', 't', 'T', 'A', 'Alb', 'a', 'k', 4, '', 1, 2, 3, '{}')",
            )

            SyncDatabase.Migration13To14.migrate(db)

            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info(sync_tracks)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertTrue("unavailableReason column is added", columns.contains("unavailableReason"))

            db.query("SELECT unavailableReason, title, playCount FROM sync_tracks WHERE trackId = 't'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue("existing row keeps a NULL reason", cursor.isNull(0))
                assertEquals("T", cursor.getString(1))
                assertEquals(4, cursor.getInt(2))
            }
        } finally {
            db.close()
        }
    }

    @Test fun allMigrationsContainsTheThirteenToFourteenStep() {
        assertTrue(SyncDatabase.AllMigrations.any { it.startVersion == 13 && it.endVersion == 14 })
    }

    private fun createV13Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }
}
