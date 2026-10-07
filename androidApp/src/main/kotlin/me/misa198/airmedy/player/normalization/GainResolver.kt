package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import me.misa198.airmedy.player.PlaybackItem

/** T050 stub; T051 implements measured-then-tags precedence. */
internal class GainResolver(private val measured: GainSource, private val tags: GainSource) {
    fun resolve(item: PlaybackItem, format: Format?): GainInfo =
        GainInfo(null, null, null, null, GainForm.None)
}
