package me.misa198.airmedy.player.normalization

import me.misa198.airmedy.player.PlaybackItem

/** The source a [GainInfo] came from. */
internal enum class GainForm { Measured, ReplayGain, R128, SoundCheck, None }

/**
 * Normalization values resolved for one item. Gains are in dB relative to -18 LUFS; peaks are
 * linear with 1.0 = full scale.
 */
internal data class GainInfo(
    val trackGainDb: Float?,
    val albumGainDb: Float?,
    val trackPeak: Float?,
    val albumPeak: Float?,
    val form: GainForm,
)

internal fun interface GainSource {
    fun gainFor(item: PlaybackItem, format: androidx.media3.common.Format?): GainInfo?
}
