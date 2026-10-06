package me.misa198.airmedy.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")
private val Context.playbackPositionDataStore by preferencesDataStore(name = "playback_position")
private val QueueSnapshotKey = stringPreferencesKey("queue_snapshot")
private val PositionTrackIdKey = stringPreferencesKey("position_track_id")
private val PositionMsKey = longPreferencesKey("position_ms")

/** Everything needed to reopen the current item without resuming audio automatically. */
internal data class PlaybackSession(
    val queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    val positionMs: Long = 0L,
)

private val PlaybackSessionJson = Json { ignoreUnknownKeys = true }

/** Accept queue-only sessions written before position persistence was added. */
internal fun decodePlaybackSession(encoded: String): PlaybackSession {
    val root = PlaybackSessionJson.parseToJsonElement(encoded).jsonObject
    return if ("queue" in root) {
        PlaybackSession(
            queue = PlaybackSessionJson.decodeFromJsonElement(root.getValue("queue")),
            positionMs = root["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    } else {
        PlaybackSession(queue = PlaybackSessionJson.decodeFromString<PlaybackQueueSnapshot>(encoded))
    }
}

internal fun encodePlaybackSession(session: PlaybackSession): String = buildJsonObject {
    put("queue", PlaybackSessionJson.encodeToJsonElement(session.queue))
    put("positionMs", session.positionMs)
}.toString()

/**
 * Overlays a separately persisted position onto a restored session. The position is only
 * used when it belongs to the session's current track and is not negative; otherwise the
 * session's own (full-save) position is kept.
 */
internal fun mergeSavedPosition(session: PlaybackSession, position: SavedPosition?): PlaybackSession =
    if (position != null && position.trackId == session.queue.currentTrackId && position.positionMs >= 0) {
        session.copy(positionMs = position.positionMs)
    } else {
        session
    }

/** Android-private persistence adapter; queue semantics remain in sharedLogic. */
internal class PlaybackSessionStore(private val context: Context) {
    suspend fun load(): PlaybackSession? {
        val encoded = context.playbackDataStore.data.first()[QueueSnapshotKey] ?: return null
        val session = runCatching { decodePlaybackSession(encoded) }
            .getOrElse {
                // A corrupt or obsolete payload must not trap every subsequent
                // app launch in the same failed restoration attempt.
                clear()
                return null
            }
        // The position file is an optional overlay: if it can't be read, keep the session's own position.
        val position = try {
            val preferences = context.playbackPositionDataStore.data.first()
            val trackId = preferences[PositionTrackIdKey]
            val positionMs = preferences[PositionMsKey]
            if (trackId != null && positionMs != null) SavedPosition(trackId, positionMs) else null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        return mergeSavedPosition(session, position)
    }

    suspend fun save(session: PlaybackSession) {
        context.playbackDataStore.edit { preferences ->
            preferences[QueueSnapshotKey] = encodePlaybackSession(session)
        }
        val trackId = session.queue.currentTrackId
        if (trackId == null) {
            context.playbackPositionDataStore.edit { preferences ->
                preferences.remove(PositionTrackIdKey)
                preferences.remove(PositionMsKey)
            }
        } else {
            context.playbackPositionDataStore.edit { preferences ->
                preferences[PositionTrackIdKey] = trackId
                preferences[PositionMsKey] = session.positionMs
            }
        }
    }

    suspend fun savePosition(position: SavedPosition) {
        context.playbackPositionDataStore.edit { preferences ->
            preferences[PositionTrackIdKey] = position.trackId
            preferences[PositionMsKey] = position.positionMs
        }
    }

    suspend fun clear() {
        context.playbackDataStore.edit { preferences -> preferences.remove(QueueSnapshotKey) }
        context.playbackPositionDataStore.edit { preferences ->
            preferences.remove(PositionTrackIdKey)
            preferences.remove(PositionMsKey)
        }
    }
}
