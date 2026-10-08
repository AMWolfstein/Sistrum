package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import me.misa198.airmedy.player.PlaybackItem

/** Resolves the gain for an item: measured loudness first, tags as fallback, then [GainForm.None]. */
internal class GainResolver(private val measured: GainSource, private val tags: GainSource) {
    fun resolve(item: PlaybackItem, format: Format?): GainInfo =
        measured.gainFor(item, format)
            ?: tags.gainFor(item, format)
            ?: GainInfo(null, null, null, null, GainForm.None)
}
