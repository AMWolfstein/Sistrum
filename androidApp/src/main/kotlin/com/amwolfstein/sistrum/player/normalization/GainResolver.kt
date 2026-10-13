package com.amwolfstein.sistrum.player.normalization

import androidx.media3.common.Format
import com.amwolfstein.sistrum.player.PlaybackItem

/** Resolves the gain for an item: measured loudness first, tags as fallback, then [GainForm.None]. */
internal class GainResolver(private val measured: GainSource, private val tags: GainSource) {
    fun resolve(item: PlaybackItem, format: Format?): GainInfo =
        measured.gainFor(item, format)
            ?: tags.gainFor(item, format)
            ?: GainInfo(null, null, null, null, GainForm.None)
}
