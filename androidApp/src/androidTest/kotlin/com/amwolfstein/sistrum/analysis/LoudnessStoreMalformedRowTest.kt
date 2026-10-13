package com.amwolfstein.sistrum.analysis

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.runBlocking
import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import com.amwolfstein.sistrum.library.LocalAlbumRef
import com.amwolfstein.sistrum.library.LocalArtistRef
import com.amwolfstein.sistrum.library.LocalLibrarySnapshot
import com.amwolfstein.sistrum.library.LocalTrack
import com.amwolfstein.sistrum.sync.AndroidLibrarySyncStore
import com.amwolfstein.sistrum.sync.LocalScanAudio
import com.amwolfstein.sistrum.sync.SyncDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T051c review 1: pins that a malformed `sync_documents.rawJson` never throws out of a rescan
 * (`copyLoudnessDocuments`), `pendingTracks()`/`progress()`, or `recomputeAlbums()`.
 */
class LoudnessStoreMalformedRowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore
    private lateinit var loudness: LoudnessStore

    private val album = LocalAlbumRef("local:album:one", "Album One")
    private val artist = LocalArtistRef("local:artist:one", "Artist One")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "loudness-malformed-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
        loudness = LoudnessStore(database)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    @Test fun rescanSucceedsWithAMalformedLoudnessRow() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))

        insertDocument("analysis", "A", "{not json")
        insertDocument("loudness_progress", "A", "{not json")

        assertTrue("rescan succeeds despite a malformed row", write(listOf(taggedTrack("A"), taggedTrack("B"))))
        assertEquals("both tracks survive", 2, activeTrackCount())
        assertEquals("B's analysis is copied over", setOf("B"), store.activeAnalyses().keys)
        assertNull("A's malformed analysis row is dropped", rawDocument("analysis", "A"))
    }

    @Test fun pendingAndProgressTolerateAMalformedRow() = runBlocking {
        write(listOf(taggedTrack("A")))
        insertDocument("analysis", "A", "{not json")

        assertEquals("a malformed row is pending", listOf("A"), loudness.pendingTracks().map { it.trackId })
        val progress = loudness.progress()
        assertEquals("a malformed row is not analyzed", 0, progress.analyzed)
        assertEquals("total is unchanged", 1, progress.total)
    }

    @Test fun recomputeAlbumsToleratesMalformedRows() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))
        loudness.recomputeAlbums()
        assertNotNull("complete album record before the corruption", loudness.albumLoudness(album.id))

        insertDocument("loudness_histogram", "A", "{not json")
        insertDocument("album_loudness", "other-album", "{not json")

        loudness.recomputeAlbums()
        assertNull("an incomplete album has no record", loudness.albumLoudness(album.id))
    }

    @Test fun aStaleHistogramRowIsNotUsed() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))
        loudness.recomputeAlbums()
        assertNotNull("complete album record before the stale histogram", loudness.albumLoudness(album.id))

        val bins = hist(-16.0 to 100).encode()
        val staleHistogram =
            """{"bin_lu":0.1,"min_lufs":-70.0,"bins":"$bins","blocks":100,"source_fingerprint":"other","analyzer_version":"$ANALYZER_VERSION"}"""
        insertDocument("loudness_histogram", "B", staleHistogram)

        loudness.recomputeAlbums()
        assertNull("a stale histogram blocks the record", loudness.albumLoudness(album.id))
    }

    // -----------------------------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------------------------

    private fun taggedTrack(id: String): LocalTrack = LocalTrack(
        id = id,
        title = "Title $id",
        artists = listOf(artist),
        album = album,
        durationMillis = 210_000L,
        format = "flac",
        codec = "flac",
        tagAlbum = album.title,
    )

    private suspend fun write(tracks: List<LocalTrack>, hashes: Map<String, String> = emptyMap()): Boolean {
        val audio = tracks.associate { track ->
            track.id to LocalScanAudio(
                trackId = track.id,
                absolutePath = "/music/${track.id}.flac",
                sha256 = hashes[track.id] ?: "hash-${track.id}",
                size = 100L,
            )
        }
        return store.writeLocalLibrary(
            snapshot = LocalLibrarySnapshot(0L, tracks),
            audioRows = audio,
            artworkRows = emptyList(),
        )
    }

    private fun insertDocument(kind: String, key: String, rawJson: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO sync_documents(planId, kind, documentKey, rawJson) SELECT planId, ?, ?, ? FROM sync_plans WHERE active = 1",
            arrayOf(kind, key, rawJson),
        )
    }

    private fun activeTrackCount(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM sync_tracks t INNER JOIN sync_plans p ON p.planId = t.planId WHERE p.active = 1", emptyArray<Any?>())
        .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun rawDocument(kind: String, key: String): String? = database.openHelper.readableDatabase
        .query("SELECT rawJson FROM sync_documents WHERE kind = ? AND documentKey = ?", arrayOf<Any?>(kind, key))
        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun blockPower(loudnessLufs: Double): Double = 10.0.pow((loudnessLufs + 0.691) / 10.0)

    private fun hist(vararg blocks: Pair<Double, Int>): LoudnessHistogram {
        val histogram = LoudnessHistogram()
        blocks.forEach { (loudness, count) -> repeat(count) { histogram.addBlockPower(blockPower(loudness)) } }
        return histogram
    }
}
