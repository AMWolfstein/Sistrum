package me.misa198.airmedy.player

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val Context.playbackPreferencesDataStore by preferencesDataStore(name = "playback_preferences")
private val CrossfadeSecondsKey = intPreferencesKey("crossfade_seconds")
private val LastEnabledCrossfadeSecondsKey = intPreferencesKey("last_enabled_crossfade_seconds")
private val BlendArtworkDuringCrossfadeKey = booleanPreferencesKey("blend_artwork_during_crossfade")
private val CrossfadeRepeatOneKey = booleanPreferencesKey("crossfade_repeat_one")
private val CrossfadeOnSkipKey = booleanPreferencesKey("crossfade_on_skip")
private val ShowFullscreenQualityBadgeKey = booleanPreferencesKey("show_fullscreen_quality_badge")

internal data class CrossfadeSettings(
    val seconds: Int,
    val lastEnabledSeconds: Int,
    val blendArtworkDuringCrossfade: Boolean,
    val repeatOneCrossfade: Boolean = true,
    val crossfadeOnSkip: Boolean = false,
)

/** Persistent playback options, deliberately separate from the resumable queue session. */
internal class PlaybackPreferences(private val context: Context) {
    val settings: Flow<CrossfadeSettings> = context.playbackPreferencesDataStore.data
        .map(::crossfadeSettingsOf).distinctUntilChanged()

    val crossfadeSeconds: Flow<Int> = settings.map { it.seconds }
    val showFullscreenQualityBadge: Flow<Boolean> = context.playbackPreferencesDataStore.data.map {
        it[ShowFullscreenQualityBadgeKey] ?: true
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.playbackPreferencesDataStore.edit { preferences ->
            preferences.applyCrossfadeSeconds(seconds)
        }
    }

    suspend fun setBlendArtworkDuringCrossfade(enabled: Boolean) {
        context.playbackPreferencesDataStore.edit { preferences ->
            preferences[BlendArtworkDuringCrossfadeKey] = enabled
        }
    }

    suspend fun setRepeatOneCrossfade(enabled: Boolean) {
        context.playbackPreferencesDataStore.edit { it.applyRepeatOneCrossfade(enabled) }
    }

    suspend fun setCrossfadeOnSkip(enabled: Boolean) {
        context.playbackPreferencesDataStore.edit { it.applyCrossfadeOnSkip(enabled) }
    }

    suspend fun setShowFullscreenQualityBadge(enabled: Boolean) {
        context.playbackPreferencesDataStore.edit { it[ShowFullscreenQualityBadgeKey] = enabled }
    }
}

internal const val CrossfadeDisabledSeconds = 0
internal const val CrossfadeDefaultSeconds = 4
internal const val CrossfadeMaxSeconds = 12

internal fun clampCrossfadeSeconds(seconds: Int): Int = seconds.coerceIn(CrossfadeDisabledSeconds, CrossfadeMaxSeconds)
internal fun clampEnabledCrossfadeSeconds(seconds: Int): Int = seconds.coerceIn(1, CrossfadeMaxSeconds)

internal fun crossfadeSettingsOf(preferences: Preferences): CrossfadeSettings = CrossfadeSettings(
    seconds = clampCrossfadeSeconds(preferences[CrossfadeSecondsKey] ?: CrossfadeDisabledSeconds),
    lastEnabledSeconds = clampEnabledCrossfadeSeconds(
        preferences[LastEnabledCrossfadeSecondsKey] ?: CrossfadeDefaultSeconds,
    ),
    blendArtworkDuringCrossfade = preferences[BlendArtworkDuringCrossfadeKey] ?: true,
    repeatOneCrossfade = preferences[CrossfadeRepeatOneKey] ?: true,
    crossfadeOnSkip = preferences[CrossfadeOnSkipKey] ?: false,
)

internal fun MutablePreferences.applyCrossfadeSeconds(seconds: Int) {
    val clamped = clampCrossfadeSeconds(seconds)
    this[CrossfadeSecondsKey] = clamped
    if (clamped > CrossfadeDisabledSeconds) this[LastEnabledCrossfadeSecondsKey] = clamped
}

internal fun MutablePreferences.applyRepeatOneCrossfade(enabled: Boolean) {
    this[CrossfadeRepeatOneKey] = enabled
}

internal fun MutablePreferences.applyCrossfadeOnSkip(enabled: Boolean) {
    this[CrossfadeOnSkipKey] = enabled
}
