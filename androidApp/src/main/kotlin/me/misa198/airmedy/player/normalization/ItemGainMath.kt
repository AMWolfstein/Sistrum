package me.misa198.airmedy.player.normalization

import kotlin.math.log10
import me.misa198.airmedy.player.NormalizationMode
import me.misa198.airmedy.player.NormalizationSettings

/**
 * The single-item normalization gain in dB. [GainInfo] gains are relative to -18 LUFS, so a gain is
 * shifted by `targetLufs + 18` before the untagged pre-amp and the clip cap are applied.
 */
internal fun itemGainDb(info: GainInfo, settings: NormalizationSettings, untaggedPreampDb: Float): Float {
    if (!settings.enabled) return 0f

    val trackGain = info.trackGainDb?.takeIf { it.isFinite() }
    val albumGain = info.albumGainDb?.takeIf { it.isFinite() }

    val base = when {
        settings.mode == NormalizationMode.Album && albumGain != null -> albumGain
        trackGain != null -> trackGain
        // Track mode with only an album gain: not None, so no pre-amp (contracts/gain-source.md).
        albumGain != null -> 0f
        // No finite gain: the info behaves as form None, whose only contribution is the pre-amp.
        else -> untaggedPreampDb
    }

    var gain = base + (settings.targetLufs + 18f)

    if (settings.preventClip) {
        val peak = when (settings.mode) {
            NormalizationMode.Album -> usablePeak(info.albumPeak) ?: usablePeak(info.trackPeak)
            NormalizationMode.Track -> usablePeak(info.trackPeak)
        }
        if (peak != null) {
            gain = minOf(gain, -20f * log10(peak))
        }
    }

    return gain.coerceIn(-30f, 20f)
}

private fun usablePeak(peak: Float?): Float? = peak?.takeIf { it.isFinite() && it > 0f }
