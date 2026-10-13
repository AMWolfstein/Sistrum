package com.amwolfstein.sistrum.player.media3

import com.amwolfstein.sistrum.player.NormalizationSettings
import com.amwolfstein.sistrum.player.normalization.GainInfo
import com.amwolfstein.sistrum.player.normalization.itemGainDb

/** Gain source is frozen for the entering stream; settings may retarget it at any time. */
internal class StreamGainControl(
    private var settings: NormalizationSettings = NormalizationSettings(),
) {
    private var currentInfo: GainInfo? = null
    @Synchronized
    fun onStreamStart(info: GainInfo): Float {
        currentInfo = info
        return itemGainDb(info, settings, settings.untaggedPreampDb)
    }

    @Synchronized
    fun setNormalization(settings: NormalizationSettings): Float? {
        this.settings = settings
        return currentInfo?.let { itemGainDb(it, settings, settings.untaggedPreampDb) }
    }
}
