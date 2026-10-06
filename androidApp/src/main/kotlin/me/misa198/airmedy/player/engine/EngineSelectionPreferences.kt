package me.misa198.airmedy.player.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.enginePreferencesDataStore by preferencesDataStore(name = "engine_preferences")

private val EngineKey = stringPreferencesKey("engine")

/** Exact enum name maps to its kind; null or anything unknown falls back to [EngineKind.Native]. */
internal fun parseEngineKind(value: String?): EngineKind =
    EngineKind.values().firstOrNull { it.name == value } ?: EngineKind.Native

/** Persisted engine choice; the coordinator reads it once per engine creation. */
internal class EngineSelectionPreferences(private val context: Context) {
    val engine: Flow<EngineKind> = context.enginePreferencesDataStore.data.map { preferences ->
        parseEngineKind(preferences[EngineKey])
    }

    suspend fun setEngine(kind: EngineKind) {
        context.enginePreferencesDataStore.edit { it[EngineKey] = kind.name }
    }
}
