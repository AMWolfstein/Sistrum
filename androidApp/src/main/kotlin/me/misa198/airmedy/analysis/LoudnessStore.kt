package me.misa198.airmedy.analysis

import androidx.room.withTransaction
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.misa198.airmedy.analysis.loudness.LoudnessHistogram
import me.misa198.airmedy.player.normalization.AlbumLoudness
import me.misa198.airmedy.player.normalization.AlbumLoudnessLookup
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.LoudnessPlannerTrackRow
import me.misa198.airmedy.sync.SyncDatabase

/**
 * Persists measured loudness into `sync_documents` of the active plan and reads back what is
 * still pending. The read side ([me.misa198.airmedy.sync.AndroidLibrarySyncStore.activeAnalyses])
 * consumes the `analysis` rows this store writes.
 */
internal class LoudnessStore(private val database: SyncDatabase) {

    private val dao = database.syncDao()

    suspend fun pendingTracks(analyzerVersion: String = ANALYZER_VERSION): List<PendingTrack> =
        database.withTransaction {
            dao.pendingTrackRows(analyzerVersion).map { row ->
                val json = runCatching { LibrarySyncProtocol.json.parseToJsonElement(row.rawJson).jsonObject }.getOrNull()
                PendingTrack(
                    trackId = row.trackId,
                    audioPath = row.audioPath,
                    fingerprint = row.fingerprint,
                    format = json?.string("format").orEmpty(),
                    codec = json?.string("codec").orEmpty(),
                    durationMs = json?.long("duration") ?: 0L,
                )
            }
        }

    suspend fun writeResult(result: TrackLoudnessResult) {
        database.withTransaction {
            val sha = dao.activeAudioSha(result.trackId) ?: return@withTransaction
            if (sha != result.fingerprint) return@withTransaction

            val analyzedAt = Instant.now().toString()
            val analysisJson = when (result) {
                is TrackLoudnessResult.Measured -> buildJsonObject {
                    put("loudness_lufs", result.integratedLufs)
                    put("true_peak", result.truePeakDbtp)
                    put("sample_peak", result.samplePeakDbfs)
                    put("status", "ok")
                    put("source_fingerprint", result.fingerprint)
                    put("analyzer_version", ANALYZER_VERSION)
                    put("analyzed_at", analyzedAt)
                }.toString()
                is TrackLoudnessResult.Silent -> buildJsonObject {
                    put("status", "silent")
                    put("source_fingerprint", result.fingerprint)
                    put("analyzer_version", ANALYZER_VERSION)
                    put("analyzed_at", analyzedAt)
                }.toString()
                is TrackLoudnessResult.Failed -> buildJsonObject {
                    put("status", "failed")
                    put("provider", result.provider)
                    put("reason", result.reason)
                    put("source_fingerprint", result.fingerprint)
                    put("analyzer_version", ANALYZER_VERSION)
                    put("analyzed_at", analyzedAt)
                }.toString()
            }
            dao.upsertActiveDocument("analysis", result.trackId, analysisJson)

            when (result) {
                is TrackLoudnessResult.Measured -> {
                    val histogramJson = buildJsonObject {
                        put("bin_lu", 0.1)
                        put("min_lufs", -70.0)
                        put("bins", result.histogram.encode())
                        put("blocks", result.histogram.blockCount)
                        put("source_fingerprint", result.fingerprint)
                        put("analyzer_version", ANALYZER_VERSION)
                    }.toString()
                    dao.upsertActiveDocument("loudness_histogram", result.trackId, histogramJson)
                }
                is TrackLoudnessResult.Silent -> {
                    val histogramJson = buildJsonObject {
                        put("bin_lu", 0.1)
                        put("min_lufs", -70.0)
                        put("bins", LoudnessHistogram().encode())
                        put("blocks", 0L)
                        put("source_fingerprint", result.fingerprint)
                        put("analyzer_version", ANALYZER_VERSION)
                    }.toString()
                    dao.upsertActiveDocument("loudness_histogram", result.trackId, histogramJson)
                }
                is TrackLoudnessResult.Failed -> dao.deleteActiveDocument("loudness_histogram", result.trackId)
            }

            dao.deleteActiveDocument("loudness_progress", result.trackId)
        }
    }

    suspend fun progress(): AnalysisProgress = database.withTransaction {
        AnalysisProgress(
            analyzed = dao.analyzedAudioTrackCount(ANALYZER_VERSION),
            total = dao.availableAudioTrackCount(),
        )
    }

    suspend fun recomputeAlbums() {
        database.withTransaction {
            val tracks = dao.loudnessPlannerTrackRows().map { it.toPlannerTrack() }
            val existing = dao.activeAlbumLoudnessRows().mapNotNull { row ->
                val json = runCatching { LibrarySyncProtocol.json.parseToJsonElement(row.rawJson).jsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val loudness = json.double("loudness_lufs") ?: return@mapNotNull null
                val peak = json.double("true_peak") ?: return@mapNotNull null
                val members = json.long("members").toInt()
                val fingerprint = json.string("members_fingerprint").orEmpty()
                if (fingerprint.isEmpty()) return@mapNotNull null
                row.albumId to AlbumRecord(loudness, peak, members, fingerprint)
            }.toMap()
            val plan = AlbumLoudnessPlanner.plan(tracks, existing)
            plan.write.forEach { (albumId, record) ->
                val json = buildJsonObject {
                    put("loudness_lufs", record.loudnessLufs)
                    put("true_peak", record.truePeakDbtp)
                    put("members", record.members)
                    put("members_fingerprint", record.membersFingerprint)
                    put("analyzer_version", ANALYZER_VERSION)
                }.toString()
                dao.upsertActiveDocument("album_loudness", albumId, json)
            }
            plan.delete.forEach { albumId -> dao.deleteActiveDocument("album_loudness", albumId) }
        }
    }

    suspend fun albumLoudness(albumId: String): AlbumLoudness? = database.withTransaction {
        val raw = dao.activeDocumentJson("album_loudness", albumId) ?: return@withTransaction null
        val json = runCatching { LibrarySyncProtocol.json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return@withTransaction null
        val loudness = json.double("loudness_lufs") ?: return@withTransaction null
        val peak = json.double("true_peak") ?: return@withTransaction null
        AlbumLoudness(loudness.toFloat(), peak.toFloat())
    }

    /** One-shot snapshot of every active `album_loudness` row, for playback resolution. */
    suspend fun albumLoudnessSnapshot(): AlbumLoudnessLookup = database.withTransaction {
        val map = dao.activeAlbumLoudnessRows().mapNotNull { row ->
            val json = runCatching { LibrarySyncProtocol.json.parseToJsonElement(row.rawJson).jsonObject }.getOrNull()
                ?: return@mapNotNull null
            val loudness = json.double("loudness_lufs") ?: return@mapNotNull null
            val peak = json.double("true_peak") ?: return@mapNotNull null
            row.albumId to AlbumLoudness(loudness.toFloat(), peak.toFloat())
        }.toMap()
        AlbumLoudnessLookup { albumId -> map[albumId] }
    }

    suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray) {
        database.withTransaction {
            val json = buildJsonObject {
                put("source_fingerprint", fingerprint)
                put("analyzer_version", ANALYZER_VERSION)
                put("state", Base64.getEncoder().encodeToString(state))
            }.toString()
            dao.upsertActiveDocument("loudness_progress", trackId, json)
        }
    }

    suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray? = database.withTransaction {
        val raw = dao.activeDocumentJson("loudness_progress", trackId) ?: return@withTransaction null
        val json = runCatching { LibrarySyncProtocol.json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return@withTransaction null
        if (json.string("source_fingerprint") != fingerprint) return@withTransaction null
        if (json.string("analyzer_version") != ANALYZER_VERSION) return@withTransaction null
        val state = json.string("state") ?: return@withTransaction null
        runCatching { Base64.getDecoder().decode(state) }.getOrNull()
    }

    suspend fun clearCheckpoint(trackId: String) {
        database.withTransaction { dao.deleteActiveDocument("loudness_progress", trackId) }
    }

    private fun LoudnessPlannerTrackRow.toPlannerTrack(): PlannerTrack {
        val trackJson = runCatching { LibrarySyncProtocol.json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
        val fingerprint = fingerprint.orEmpty()
        val analysisJson = analysisJson?.let { runCatching { LibrarySyncProtocol.json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val current = analysisJson?.takeIf { it.string("source_fingerprint") == fingerprint }
        val histogram = if (current == null) {
            null
        } else {
            histogramJson
                ?.let { runCatching { LibrarySyncProtocol.json.parseToJsonElement(it).jsonObject }.getOrNull() }
                ?.takeIf {
                    it.string("source_fingerprint") == fingerprint &&
                        it.string("analyzer_version") == current.string("analyzer_version")
                }
                ?.let { runCatching { it["bins"]?.jsonPrimitive?.contentOrNull }.getOrNull() }
        }
        return PlannerTrack(
            trackId = trackId,
            albumId = albumId,
            album = album,
            audioPath = audioPath.orEmpty(),
            tagAlbum = trackJson?.string("tag_album").orEmpty(),
            available = unavailableReason == null,
            fingerprint = fingerprint,
            status = current?.string("status"),
            analyzerVersion = current?.string("analyzer_version"),
            histogram = histogram,
            truePeakDbtp = current?.double("true_peak"),
        )
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.long(name: String): Long = (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
