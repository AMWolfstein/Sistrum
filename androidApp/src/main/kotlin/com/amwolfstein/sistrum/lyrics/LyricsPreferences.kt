package com.amwolfstein.sistrum.lyrics

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.lyricsDataStore by preferencesDataStore("lyrics")
private val LrclibKey = booleanPreferencesKey("enable_lrclib")
private val KugouKey = booleanPreferencesKey("enable_kugou")
private val EmbeddedKey = booleanPreferencesKey("enable_embedded")
private val SidecarKey = booleanPreferencesKey("enable_sidecar")

internal data class LyricsSettings(
    val lrclib: Boolean = true,
    val kugou: Boolean = true,
    val embedded: Boolean = true,
    val sidecar: Boolean = true,
)

internal class LyricsPreferences(private val context: Context) {
    val settings = context.lyricsDataStore.data.map {
        LyricsSettings(
            lrclib = it[LrclibKey] ?: true,
            kugou = it[KugouKey] ?: true,
            embedded = it[EmbeddedKey] ?: true,
            sidecar = it[SidecarKey] ?: true,
        )
    }
    suspend fun setLrclib(enabled: Boolean) = context.lyricsDataStore.edit { it[LrclibKey] = enabled }
    suspend fun setKugou(enabled: Boolean) = context.lyricsDataStore.edit { it[KugouKey] = enabled }
    suspend fun setEmbedded(enabled: Boolean) = context.lyricsDataStore.edit { it[EmbeddedKey] = enabled }
    suspend fun setSidecar(enabled: Boolean) = context.lyricsDataStore.edit { it[SidecarKey] = enabled }
}