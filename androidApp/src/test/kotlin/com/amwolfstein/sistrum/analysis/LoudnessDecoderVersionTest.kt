package com.amwolfstein.sistrum.analysis

import androidx.room.InvalidationTracker
import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import com.amwolfstein.sistrum.sync.AnalysisDocumentRow
import com.amwolfstein.sistrum.sync.AndroidLibrarySyncStore
import com.amwolfstein.sistrum.sync.SyncDao
import com.amwolfstein.sistrum.sync.SyncDatabase
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LoudnessDecoderVersionTest {
    private val measured = TrackLoudnessResult.Measured("A", "hash-A", -14.2, -0.5, -0.9, LoudnessHistogram())
    private val failed = TrackLoudnessResult.Failed("A", "hash-A", "kotlin-wma", "decode error")
    // An independently authored stored document, not output from the builder under test.
    private val previous = Json.parseToJsonElement("""{
        "loudness_lufs": -14.2, "true_peak": -0.5, "sample_peak": -0.9, "status": "ok",
        "source_fingerprint": "hash-A", "analyzer_version": "sistrum-loudness-1/waxflow-446ca31",
        "decoder_set_version": "sistrum-decoders-002-m1", "analyzed_at": "2026-10-13T12:00:00Z",
        "energy": 0.7
    }""").jsonObject

    @Test fun everyAnalysisOutcomeRecordsDecoderVersionWithoutChangingAnalyzerVersion() {
        for (result in listOf(measured, failed, TrackLoudnessResult.Silent("A", "hash-A"))) {
            val document = analysisDocument(result, "2026-10-14T12:00:00Z")
            assertEquals(DECODER_SET_VERSION, document["decoder_set_version"]!!.jsonPrimitive.content)
            assertEquals("sistrum-loudness-1/waxflow-446ca31", document["analyzer_version"]!!.jsonPrimitive.content)
        }
    }

    @Test fun failedReanalysisKeepsAllMeasurementFieldsAndRemainsReadable() = runBlocking {
        val kept = analysisDocument(failed, "2026-10-14T12:00:00Z", previous)
        for ((key, value) in previous) assertEquals("kept $key", value, kept[key])
        assertEquals(buildJsonObject { put("provider", "kotlin-wma"); put("reason", "decode error") }, kept["last_failure"])
        assertEquals("2026-10-14T12:00:00Z", kept["last_failed_at"]!!.jsonPrimitive.content)
        val dao = Proxy.newProxyInstance(SyncDao::class.java.classLoader, arrayOf(SyncDao::class.java)) { _, method, _ ->
            when {
                method.name == "activeAnalysisDocuments" -> listOf(AnalysisDocumentRow("A", kept.toString()))
                method.name.startsWith("observe") -> emptyFlow<Nothing>()
                else -> throw AssertionError("unexpected DAO call ${method.name}")
            }
        } as SyncDao
        val database = object : SyncDatabase() {
            override fun syncDao() = dao
            override fun clearAllTables() = Unit
            override fun createInvalidationTracker(): InvalidationTracker = throw AssertionError("no database I/O")
        }
        val analysis = AndroidLibrarySyncStore(database, File("unused")).activeAnalyses().getValue("A")
        assertEquals(-14.2f, analysis.loudnessLufs, 0f)
        assertEquals(-0.5f, analysis.truePeak, 0f)
    }

    @Test fun failureUpdatesDecoderVersionWhileKeepingOriginalMeasurementProvenance() {
        val legacy = JsonObject(previous +
            ("decoder_set_version" to JsonPrimitive("old-decoders")) +
            ("analyzer_version" to JsonPrimitive("old-analyzer")))
        val document = analysisDocument(failed, "new-time", legacy)
        assertEquals(DECODER_SET_VERSION, document["decoder_set_version"]!!.jsonPrimitive.content)
        assertEquals("2026-10-13T12:00:00Z", document["analyzed_at"]!!.jsonPrimitive.content)
        assertEquals("old-analyzer", document["analyzer_version"]!!.jsonPrimitive.content)
        assertEquals("ok", document["status"]!!.jsonPrimitive.content)
        assertEquals(previous["loudness_lufs"], document["loudness_lufs"])
    }

    @Test fun changedFingerprintOrNonOkStatusDoesNotRetainMeasurement() {
        val stale = JsonObject(previous + ("source_fingerprint" to JsonPrimitive("old-hash")))
        val document = analysisDocument(failed, "new", stale)
        assertEquals("failed", document["status"]!!.jsonPrimitive.content)
        assertFalse("stale values removed", "loudness_lufs" in document)
        for (status in listOf("silent", "failed")) {
            val nonOk = JsonObject(previous + ("status" to JsonPrimitive(status)) + ("source_fingerprint" to JsonPrimitive("hash-A")))
            assertEquals("failed", analysisDocument(failed, "new", nonOk)["status"]!!.jsonPrimitive.content)
        }
    }

    @Test fun pendingDaoRetriesOnlyFailuresFromAnotherDecoderSet() {
        // Execute the actual Room SQL with host SQLite's JSON support; no Android database stubs.
        val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "androidApp/src/main/kotlin/com/amwolfstein/sistrum/sync/SyncDatabase.kt").isFile }
        val source = File(root, "androidApp/src/main/kotlin/com/amwolfstein/sistrum/sync/SyncDatabase.kt").readText()
        val end = source.indexOf("suspend fun pendingTrackRows")
        val sql = source.substring(source.lastIndexOf("@Query(\"\"\"", end) + 10, source.lastIndexOf("\"\"\")", end))
        val process = ProcessBuilder("python3", "-c", """
            import sqlite3, json, sys
            db = sqlite3.connect(':memory:')
            db.executescript('''
            CREATE TABLE sync_plans(planId TEXT, active INTEGER);
            CREATE TABLE sync_tracks(planId TEXT, trackId TEXT, unavailableReason TEXT, syncOrder INTEGER, rawJson TEXT);
            CREATE TABLE sync_assets(planId TEXT, assetId TEXT, relativePath TEXT, sha256 TEXT);
            CREATE TABLE sync_documents(planId TEXT, kind TEXT, documentKey TEXT, rawJson TEXT);
            INSERT INTO sync_plans VALUES ('p', 1);
            ''')
            sql = sys.stdin.read()
            for i, (name, status, decoder, fingerprint, analyzer) in enumerate([
                ('failed-old', 'failed', 'old', 'hash', sys.argv[1]),
                ('failed-missing', 'failed', None, 'hash', sys.argv[1]),
                ('failed-current', 'failed', sys.argv[2], 'hash', sys.argv[1]),
                ('ok-old', 'ok', 'old', 'hash', sys.argv[1]),
                ('silent-old', 'silent', 'old', 'hash', sys.argv[1]),
                ('changed-source', 'ok', sys.argv[2], 'old-hash', sys.argv[1]),
                ('changed-analyzer', 'ok', sys.argv[2], 'hash', 'old'),
                ('missing', None, None, 'hash', sys.argv[1]),
                ('malformed', None, None, 'hash', sys.argv[1]),
            ]):
                db.execute('INSERT INTO sync_tracks VALUES (?, ?, NULL, ?, ?)', ('p', name, i, '{}'))
                db.execute('INSERT INTO sync_assets VALUES (?, ?, ?, ?)', ('p', 'audio:' + name, name, 'hash'))
                if name != 'missing':
                    raw = json.dumps(dict(status=status, decoder_set_version=decoder, source_fingerprint=fingerprint, analyzer_version=analyzer))
                    db.execute('INSERT INTO sync_documents VALUES (?, ?, ?, ?)', ('p', 'analysis', name, '{bad' if name == 'malformed' else raw))
            print(','.join(row[0] for row in db.execute(sql, dict(analyzerVersion=sys.argv[1], decoderSetVersion=sys.argv[2]))))
        """.trimIndent(), ANALYZER_VERSION, DECODER_SET_VERSION).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(sql) }
        val output = process.inputStream.bufferedReader().readText().trim()
        assertEquals(output, 0, process.waitFor())
        assertEquals("failed-old,failed-missing,changed-source,changed-analyzer,missing,malformed", output)
    }
}
