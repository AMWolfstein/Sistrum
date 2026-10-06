package me.misa198.airmedy.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.skippedFilesDataStore by preferencesDataStore(name = "skipped_files")
private val SummaryKey = stringPreferencesKey("summary")

private val SkippedFilesJson = Json { ignoreUnknownKeys = true }

/** JSON form of a [SkippedFilesSummary], stored under [SummaryKey]. */
internal fun encodeSkippedSummary(summary: SkippedFilesSummary): String =
    SkippedFilesJson.encodeToString(SkippedFilesSummary.serializer(), summary)

/** Decodes a stored summary; null on absent or unreadable input. */
internal fun decodeSkippedSummary(encoded: String): SkippedFilesSummary? =
    runCatching { SkippedFilesJson.decodeFromString(SkippedFilesSummary.serializer(), encoded) }.getOrNull()

/**
 * FR-066 storage: the last scan's [SkippedFilesSummary], in a Preferences DataStore.
 * The UI that shows it is a later task; this only saves it.
 */
internal class SkippedFilesSummaryStore(private val context: Context) {
    /** The stored summary, or null when absent or unreadable. */
    val summary: Flow<SkippedFilesSummary?> = context.skippedFilesDataStore.data.map { preferences ->
        preferences[SummaryKey]?.let(::decodeSkippedSummary)
    }

    suspend fun save(summary: SkippedFilesSummary) {
        context.skippedFilesDataStore.edit { preferences ->
            preferences[SummaryKey] = encodeSkippedSummary(summary)
        }
    }

    suspend fun clear() {
        context.skippedFilesDataStore.edit { preferences -> preferences.remove(SummaryKey) }
    }
}
