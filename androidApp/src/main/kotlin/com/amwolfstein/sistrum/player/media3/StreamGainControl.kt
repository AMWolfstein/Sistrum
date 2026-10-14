package com.amwolfstein.sistrum.player.media3

import com.amwolfstein.sistrum.player.NormalizationSettings
import com.amwolfstein.sistrum.player.NormalizationMode
import com.amwolfstein.sistrum.player.normalization.GainInfo
import com.amwolfstein.sistrum.player.normalization.itemGainDb
import com.amwolfstein.sistrum.player.normalization.resolveNormalizationMode

/** Gain source is frozen for the entering stream; settings may retarget it at any time. */
internal class StreamGainControl(
    private var settings: NormalizationSettings = NormalizationSettings(),
    private val queueSingleAlbum: () -> Boolean = { false },
) {
    private var currentInfo: GainInfo? = null
    private var resolvedMode = NormalizationMode.Track
    @Synchronized
    fun onStreamStart(info: GainInfo): Float {
        currentInfo = info
        resolvedMode = resolveNormalizationMode(settings.mode, queueSingleAlbum())
        return itemGainDb(info, settings.copy(mode = resolvedMode), settings.untaggedPreampDb)
    }

    @Synchronized
    fun setNormalization(settings: NormalizationSettings): Float? {
        if (settings.mode != NormalizationMode.Auto) {
            resolvedMode = settings.mode
        } else if (this.settings.mode != NormalizationMode.Auto) {
            resolvedMode = resolveNormalizationMode(settings.mode, queueSingleAlbum())
        }
        this.settings = settings
        return currentInfo?.let { itemGainDb(it, settings.copy(mode = resolvedMode), settings.untaggedPreampDb) }
    }
}
