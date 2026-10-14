package com.amwolfstein.sistrum.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val NormalizationEnabledKey = booleanPreferencesKey("normalization_enabled")
private val NormalizationModeKey = stringPreferencesKey("normalization_mode")
private val NormalizationTargetKey = floatPreferencesKey("normalization_target_lufs")
private val NormalizationPreventClipKey = booleanPreferencesKey("normalization_prevent_clip")

private val NormalizationUntaggedPreampKey = floatPreferencesKey("normalization_untagged_preamp_db")

/** FR-047j seam: preserves today's stored-mode mapping for both install types. */
internal fun normalizationModeFrom(stored: String?, existingInstall: Boolean): NormalizationMode =
    if (stored == NormalizationMode.Album.name) NormalizationMode.Album else NormalizationMode.Track

/** FR-047j seam: install detection is implemented in the following task. */
internal fun isExistingInstall(hasAnyPlaybackPreference: Boolean, libraryDatabaseExists: Boolean): Boolean = false

internal class NormalizationPreferences(private val context: Context) {
    val settings: Flow<NormalizationSettings> = context.playbackPreferencesDataStore.data.map { value ->
        NormalizationSettings(
            enabled = value[NormalizationEnabledKey] ?: false,
            mode = normalizationModeFrom(value[NormalizationModeKey], false),
            targetLufs = (value[NormalizationTargetKey] ?: -14f).coerceIn(-30f, -5f),
            preventClip = value[NormalizationPreventClipKey] ?: true,
            untaggedPreampDb = (value[NormalizationUntaggedPreampKey] ?: 0f).coerceIn(-15f, 15f),
        )
    }

    suspend fun update(transform: (NormalizationSettings) -> NormalizationSettings) {
        context.playbackPreferencesDataStore.edit { values ->
            val current = NormalizationSettings(
                values[NormalizationEnabledKey] ?: false,
                normalizationModeFrom(values[NormalizationModeKey], false),
                (values[NormalizationTargetKey] ?: -14f).coerceIn(-30f, -5f), values[NormalizationPreventClipKey] ?: true,
                (values[NormalizationUntaggedPreampKey] ?: 0f).coerceIn(-15f, 15f),
            )
            val next = transform(current)
            values[NormalizationEnabledKey] = next.enabled
            values[NormalizationModeKey] = next.mode.name
            values[NormalizationTargetKey] = next.targetLufs.coerceIn(-30f, -5f)
            values[NormalizationPreventClipKey] = next.preventClip
            values[NormalizationUntaggedPreampKey] = next.untaggedPreampDb.coerceIn(-15f, 15f)
        }
    }
    suspend fun disable() = update { it.copy(enabled = false) }
}
