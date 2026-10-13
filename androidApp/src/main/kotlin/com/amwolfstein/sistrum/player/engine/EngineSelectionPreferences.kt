package com.amwolfstein.sistrum.player.engine

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.enginePreferencesDataStore by preferencesDataStore(name = "engine_preferences")

private val EngineKey = stringPreferencesKey("engine")
private val DeveloperUnlockedKey = booleanPreferencesKey("developer_unlocked")

/** Exact enum names keep the saved choice; null, blank, unknown or wrong case defaults to [EngineKind.Media3]. */
internal fun parseEngineKind(value: String?): EngineKind =
    EngineKind.values().firstOrNull { it.name == value } ?: EngineKind.Media3

/** Persisted engine choice; the coordinator reads it once per engine creation. */
internal class EngineSelectionPreferences(private val context: Context) {
    val engine: Flow<EngineKind> = context.enginePreferencesDataStore.data.map { preferences ->
        parseEngineKind(preferences[EngineKey])
    }

    val developerUnlocked: Flow<Boolean> = context.enginePreferencesDataStore.data.map { preferences ->
        preferences[DeveloperUnlockedKey] ?: false
    }

    suspend fun setEngine(kind: EngineKind) {
        context.enginePreferencesDataStore.edit { it[EngineKey] = kind.name }
    }

    suspend fun setDeveloperUnlocked(unlocked: Boolean) {
        context.enginePreferencesDataStore.edit { it[DeveloperUnlockedKey] = unlocked }
    }
}
