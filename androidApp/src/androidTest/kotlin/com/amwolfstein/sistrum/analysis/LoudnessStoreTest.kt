package com.amwolfstein.sistrum.analysis

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import com.amwolfstein.sistrum.library.LocalAlbumRef
import com.amwolfstein.sistrum.library.LocalArtistRef
import com.amwolfstein.sistrum.library.LocalLibrarySnapshot
import com.amwolfstein.sistrum.library.LocalTrack
import com.amwolfstein.sistrum.sync.AndroidLibrarySyncStore
import com.amwolfstein.sistrum.sync.LocalScanAudio
import com.amwolfstein.sistrum.sync.SyncDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T051b: pins how measured loudness is stored in `sync_documents`, survives rescans, and how album
 * loudness is planned. Runs against a real in-memory Room database through the store's own read side,
 * using the same setup as HiddenTracksTest. Against the T051b stubs every positive test fails on an
 * assertion (never a crash); staleness/discard tests pass trivially because nothing is written yet.
 */
class LoudnessStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore
    private lateinit var loudness: LoudnessStore

    private val album = LocalAlbumRef("local:album:one", "Album One")
    private val artist = LocalArtistRef("local:artist:one", "Artist One")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "loudness-store-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
        loudness = LoudnessStore(database)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    // -----------------------------------------------------------------------------------------
    // S1 measured rows and the existing read side
    // -----------------------------------------------------------------------------------------

    @Test fun measuredResultIsReadBackByTheExistingReadSide() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))

        val analysis = store.activeAnalyses()["A"]
        assertNotNull("measured analysis is readable", analysis)
        assertEquals("loudness", -14.2f, analysis!!.loudnessLufs, 1e-3f)
        assertEquals("true peak", -0.5f, analysis.truePeak, 1e-3f)

        val row = rawDocument("analysis", "A")
        assertNotNull("analysis row", row)
        val keys = Json.parseToJsonElement(row!!).jsonObject.keys
        assertEquals(
            "exactly the loudness keys",
            setOf("loudness_lufs", "true_peak", "sample_peak", "status", "source_fingerprint", "analyzer_version", "decoder_set_version", "analyzed_at"),
            keys,
        )
        assertTrue(
            "no mood keys",
            listOf("energy", "danceability", "brightness", "tempo").none { it in keys },
        )
    }

    // -----------------------------------------------------------------------------------------
    // S2 silent / failed rows and histograms
    // -----------------------------------------------------------------------------------------

    @Test fun silentAndFailedRowsAreSkippedByTheReadSide() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Silent("A", "hash-A"))
        loudness.writeResult(TrackLoudnessResult.Failed("B", "hash-B", "ffmpeg", "decode error"))

        assertNull("the read side skips a silent track", store.activeAnalyses()["A"])

        val silentRow = rawDocument("analysis", "A")
        assertNotNull("silent analysis row", silentRow)
        val silent = Json.parseToJsonElement(silentRow!!).jsonObject
        assertEquals("silent status", "silent", silent["status"]?.jsonPrimitive?.content)
        assertTrue("silent has no loudness", "loudness_lufs" !in silent)
        assertTrue("silent has no true peak", "true_peak" !in silent)
        val silentHistogram = rawDocument("loudness_histogram", "A")
        assertNotNull("silent writes an empty histogram", silentHistogram)
        assertEquals(
            "silent histogram has no blocks",
            0L,
            Json.parseToJsonElement(silentHistogram!!).jsonObject["blocks"]?.jsonPrimitive?.content?.toLong(),
        )

        val failedRow = rawDocument("analysis", "B")
        assertNotNull("failed analysis row", failedRow)
        val failed = Json.parseToJsonElement(failedRow!!).jsonObject
        assertEquals("failed status", "failed", failed["status"]?.jsonPrimitive?.content)
        assertEquals("failed provider", "ffmpeg", failed["provider"]?.jsonPrimitive?.content)
        assertEquals("failed reason", "decode error", failed["reason"]?.jsonPrimitive?.content)
        assertTrue("failed has no loudness", "loudness_lufs" !in failed)
    }

    @Test fun measuredResultWritesItsHistogram() = runBlocking {
        write(listOf(taggedTrack("A")))
        val histogram = hist(-14.2 to 100, -22.0 to 40)
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, histogram))

        val row = rawDocument("loudness_histogram", "A")
        assertNotNull("measured histogram row", row)
        val document = Json.parseToJsonElement(row!!).jsonObject
        val bins = document["bins"]?.jsonPrimitive?.content
        assertNotNull("histogram bins", bins)
        assertEquals("source fingerprint", "hash-A", document["source_fingerprint"]?.jsonPrimitive?.content)

        val decoded = LoudnessHistogram.decode(bins!!)
        assertEquals("decoded histogram matches", histogram.encode(), decoded.encode())
        assertEquals("block count", histogram.blockCount, decoded.blockCount)
        assertEquals("blocks field", histogram.blockCount, document["blocks"]?.jsonPrimitive?.content?.toLong())
    }

    // -----------------------------------------------------------------------------------------
    // S3 pending tracks
    // -----------------------------------------------------------------------------------------

    @Test fun pendingTracksListsAvailableTracksWithoutADocument() = runBlocking {
        write(
            listOf(
                taggedTrack("A"),
                taggedTrack("B").copy(decoderUnavailable = "unsupported format (WMA)"),
            ),
        )

        val pending = loudness.pendingTracks()
        assertEquals("only available unanalysed tracks", listOf("A"), pending.map { it.trackId })

        val track = pending.firstOrNull()
        assertNotNull("pending entry", track)
        assertEquals("audio path", "/music/A.flac", track!!.audioPath)
        assertEquals("fingerprint is the audio sha", "hash-A", track.fingerprint)
        assertEquals("format", "flac", track.format)
        assertEquals("codec", "flac", track.codec)
        assertEquals("duration", 210_000L, track.durationMs)
    }

    @Test fun aMeasuredTrackIsNoLongerPending() = runBlocking {
        write(listOf(taggedTrack("A")))
        assertTrue("A is pending before analysis", loudness.pendingTracks().any { it.trackId == "A" })
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        assertTrue("a measured track is not pending", loudness.pendingTracks().none { it.trackId == "A" })
    }

    @Test fun aFailedTrackIsNotPendingForItsVersionButAnotherVersionListsItAgain() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.writeResult(TrackLoudnessResult.Failed("A", "hash-A", "ffmpeg", "boom"))

        assertTrue("a failed result clears pending", loudness.pendingTracks().none { it.trackId == "A" })
        assertEquals(
            "another version lists it again",
            listOf("A"),
            loudness.pendingTracks("other-version").map { it.trackId },
        )
    }

    @Test fun failedReanalysisPreservesMeasurementHistogramAndPlaybackReadSide() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        val before = Json.parseToJsonElement(rawDocument("analysis", "A")!!).jsonObject
        val histogram = rawDocument("loudness_histogram", "A")
        loudness.writeResult(TrackLoudnessResult.Failed("A", "hash-A", "kotlin-wma", "boom"))
        val after = Json.parseToJsonElement(rawDocument("analysis", "A")!!).jsonObject
        before.forEach { (key, value) -> assertEquals("kept $key", value, after[key]) }
        assertEquals("kotlin-wma", after["last_failure"]!!.jsonObject["provider"]!!.jsonPrimitive.content)
        assertEquals("boom", after["last_failure"]!!.jsonObject["reason"]!!.jsonPrimitive.content)
        assertNotNull(after["last_failed_at"])
        assertEquals(histogram, rawDocument("loudness_histogram", "A"))
        assertEquals(-14.2f, store.activeAnalyses().getValue("A").loudnessLufs, 0f)
        assertEquals(-0.5f, store.activeAnalyses().getValue("A").truePeak, 0f)
        assertTrue(loudness.pendingTracks().isEmpty())
    }

    @Test fun decoderSetChangeRetriesFailedDocumentsOnly() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B"), taggedTrack("C")))
        loudness.writeResult(TrackLoudnessResult.Failed("A", "hash-A", "kotlin-wma", "boom"))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Silent("C", "hash-C"))
        for (id in listOf("A", "B", "C")) {
            val json = Json.parseToJsonElement(rawDocument("analysis", id)!!).jsonObject
            assertEquals(DECODER_SET_VERSION, json["decoder_set_version"]!!.jsonPrimitive.content)
            database.syncDao().upsertActiveDocument("analysis", id,
                kotlinx.serialization.json.JsonObject(json - "decoder_set_version").toString())
        }
        assertEquals(listOf("A"), loudness.pendingTracks().map { it.trackId })
        loudness.writeResult(TrackLoudnessResult.Failed("A", "hash-A", "kotlin-wma", "still refused"))
        assertTrue(loudness.pendingTracks().isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // S4 rescans
    // -----------------------------------------------------------------------------------------

    @Test fun rescanKeepsUnchangedRowsAndDropsChangedOnes() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))

        write(listOf(taggedTrack("A"), taggedTrack("B")))
        assertEquals("unchanged rows survive", setOf("A", "B"), store.activeAnalyses().keys)
        assertTrue("nothing is pending after an unchanged rescan", loudness.pendingTracks().isEmpty())

        write(listOf(taggedTrack("A"), taggedTrack("B")), hashes = mapOf("B" to "hash-B-changed"))
        assertEquals("A is kept", setOf("A"), store.activeAnalyses().keys)
        assertTrue("B is pending again", loudness.pendingTracks().any { it.trackId == "B" })
        assertNull("B's histogram is gone", rawDocument("loudness_histogram", "B"))
        assertNotNull("A's histogram is kept", rawDocument("loudness_histogram", "A"))

        write(listOf(taggedTrack("B")))
        assertTrue("A's rows are gone", store.activeAnalyses().isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // S5 discard stale or unknown results
    // -----------------------------------------------------------------------------------------

    @Test fun resultsForChangedOrUnknownTracksAreDiscarded() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "stale-hash", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("ghost", "hash-ghost", -14.2, -0.5, -0.9, hist(-14.2 to 100)))

        assertNull("a stale fingerprint is discarded", rawDocument("analysis", "A"))
        assertNull("an unknown track is discarded", rawDocument("analysis", "ghost"))
        assertTrue("no reads either", store.activeAnalyses().isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // S6 album records
    // -----------------------------------------------------------------------------------------

    @Test fun albumRecordNeedsEveryMemberMeasured() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.recomputeAlbums()
        assertNull("an incomplete album has no record", loudness.albumLoudness(album.id))

        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))
        loudness.recomputeAlbums()
        val record = loudness.albumLoudness(album.id)
        assertNotNull("a complete album has a record", record)

        val merged = LoudnessHistogram()
        merged.merge(hist(-14.2 to 100))
        merged.merge(hist(-16.0 to 100))
        val mergedIntegrated = merged.integratedLufs()
        assertNotNull("merged integrated", mergedIntegrated)
        assertEquals("album loudness is pooled", mergedIntegrated!!, record!!.loudnessLufs.toDouble(), 0.05)
        assertEquals("album true peak is the max", -0.4, record.truePeakDbtp.toDouble(), 1e-3)
    }

    @Test fun albumRecordDisappearsWhenAMemberIsAddedUntilItIsMeasured() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))
        loudness.recomputeAlbums()
        assertNotNull("complete album record", loudness.albumLoudness(album.id))

        write(listOf(taggedTrack("A"), taggedTrack("B"), taggedTrack("C")))
        loudness.recomputeAlbums()
        assertNull("a new unmeasured member clears the record", loudness.albumLoudness(album.id))
    }

    @Test fun recomputeAlbumsIsIdempotent() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))
        loudness.recomputeAlbums()
        val first = loudness.albumLoudness(album.id)
        loudness.recomputeAlbums()
        val second = loudness.albumLoudness(album.id)
        assertNotNull("first record", first)
        assertNotNull("second record", second)
        assertEquals("idempotent loudness", first!!.loudnessLufs, second!!.loudnessLufs, 1e-6f)
        assertEquals("idempotent true peak", first.truePeakDbtp, second.truePeakDbtp, 1e-6f)
    }

    // -----------------------------------------------------------------------------------------
    // S7 folder fallback
    // -----------------------------------------------------------------------------------------

    @Test fun folderFallbackAlbumGetsNoRecord() = runBlocking {
        val fallback = taggedTrack("A", LocalAlbumRef("local:album:music", "Music")).copy(tagAlbum = "")
        write(listOf(fallback), paths = mapOf("A" to "/sdcard/Music/A.flac"))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.recomputeAlbums()
        assertNull("a folder fallback is not an album", loudness.albumLoudness("local:album:music"))
    }

    // -----------------------------------------------------------------------------------------
    // S8 progress
    // -----------------------------------------------------------------------------------------

    @Test fun progressCountsAvailableTracksWithACurrentDocument() = runBlocking {
        write(
            listOf(
                taggedTrack("A"),
                taggedTrack("B"),
                taggedTrack("C").copy(decoderUnavailable = "unsupported format (WMA)"),
            ),
        )
        val before = loudness.progress()
        assertEquals("total counts available tracks", 2, before.total)
        assertEquals("nothing analyzed yet", 0, before.analyzed)

        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        val after = loudness.progress()
        assertEquals("analyzed counts the document", 1, after.analyzed)
        assertEquals("total is unchanged", 2, after.total)
    }

    // -----------------------------------------------------------------------------------------
    // S9 checkpoints
    // -----------------------------------------------------------------------------------------

    @Test fun checkpointsAreKeyedByTrackAndFingerprint() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.saveCheckpoint("A", "hash-A", byteArrayOf(1, 2, 3))

        assertArrayEquals("checkpoint round-trips", byteArrayOf(1, 2, 3), loudness.loadCheckpoint("A", "hash-A"))
        assertNull("another fingerprint has no checkpoint", loudness.loadCheckpoint("A", "other"))

        loudness.clearCheckpoint("A")
        assertNull("a cleared checkpoint is gone", loudness.loadCheckpoint("A", "hash-A"))
    }

    @Test fun finishingATrackClearsItsCheckpoint() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.saveCheckpoint("A", "hash-A", byteArrayOf(1))
        assertNotNull("checkpoint is stored", loudness.loadCheckpoint("A", "hash-A"))

        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        assertNull("finishing the track clears its checkpoint", loudness.loadCheckpoint("A", "hash-A"))
    }

    @Test fun checkpointSurvivesAnUnchangedRescan() = runBlocking {
        write(listOf(taggedTrack("A")))
        loudness.saveCheckpoint("A", "hash-A", byteArrayOf(9))
        write(listOf(taggedTrack("A")))
        assertArrayEquals("checkpoint survives", byteArrayOf(9), loudness.loadCheckpoint("A", "hash-A"))

        write(listOf(taggedTrack("A")), hashes = mapOf("A" to "hash-A-changed"))
        assertNull("a changed fingerprint drops the checkpoint", loudness.loadCheckpoint("A", "hash-A"))
    }

    // -----------------------------------------------------------------------------------------
    // S10 album records survive an unchanged rescan
    // -----------------------------------------------------------------------------------------

    @Test fun albumRecordSurvivesAnUnchangedRescan() = runBlocking {
        write(listOf(taggedTrack("A"), taggedTrack("B")))
        loudness.writeResult(TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, hist(-14.2 to 100)))
        loudness.writeResult(TrackLoudnessResult.Measured("B", "hash-B", -16.0, -0.4, -0.8, hist(-16.0 to 100)))
        loudness.recomputeAlbums()

        write(listOf(taggedTrack("A"), taggedTrack("B")))
        assertNotNull("album record survives an unchanged rescan", loudness.albumLoudness(album.id))
    }

    // -----------------------------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------------------------

    private fun taggedTrack(id: String, albumRef: LocalAlbumRef = album): LocalTrack = LocalTrack(
        id = id,
        title = "Title $id",
        artists = listOf(artist),
        album = albumRef,
        durationMillis = 210_000L,
        format = "flac",
        codec = "flac",
        tagAlbum = albumRef.title,
    )

    private suspend fun write(
        tracks: List<LocalTrack>,
        paths: Map<String, String> = emptyMap(),
        hashes: Map<String, String> = emptyMap(),
    ): Boolean {
        val audio = tracks.associate { track ->
            track.id to LocalScanAudio(
                trackId = track.id,
                absolutePath = paths[track.id] ?: "/music/${track.id}.flac",
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
