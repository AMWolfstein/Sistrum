package com.amwolfstein.sistrum.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.integrationDataStore by preferencesDataStore(name = "integrations")
private val WikipediaEnabledKey = booleanPreferencesKey("wikipedia_enabled")
private val DeezerEnabledKey = booleanPreferencesKey("deezer_enabled")
private val MotionEnabledKey = booleanPreferencesKey("motion_enabled")
private val MotionNetworkModeKey = stringPreferencesKey("motion_network_mode")

enum class MotionNetworkMode {
    WIFI_ONLY,
    WIFI_AND_CELLULAR,
    ;

    companion object {
        fun fromStorage(value: String?): MotionNetworkMode =
            entries.firstOrNull { it.name == value } ?: WIFI_ONLY
    }
}

class IntegrationPreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.integrationDataStore)

    val wikipediaEnabled: Flow<Boolean> = store.data.map { it[WikipediaEnabledKey] ?: true }
    val deezerEnabled: Flow<Boolean> = store.data.map { it[DeezerEnabledKey] ?: false }
    val motionEnabled: Flow<Boolean> = store.data.map { it[MotionEnabledKey] ?: false }
    val motionNetworkMode: Flow<MotionNetworkMode> = store.data.map {
        MotionNetworkMode.fromStorage(it[MotionNetworkModeKey])
    }

    suspend fun setWikipediaEnabled(enabled: Boolean) {
        store.edit { it[WikipediaEnabledKey] = enabled }
    }

    suspend fun setDeezerEnabled(enabled: Boolean) {
        store.edit { it[DeezerEnabledKey] = enabled }
    }

    suspend fun setMotionEnabled(enabled: Boolean) {
        store.edit { it[MotionEnabledKey] = enabled }
    }

    suspend fun setMotionNetworkMode(mode: MotionNetworkMode) {
        store.edit { it[MotionNetworkModeKey] = mode.name }
    }
}
