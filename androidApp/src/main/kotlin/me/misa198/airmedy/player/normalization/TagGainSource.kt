package me.misa198.airmedy.player.normalization

import me.misa198.airmedy.player.PlaybackItem

/** T049 stub; T051 ports Rhythm's ReplayGainUtil. */
internal class TagGainSource : GainSource {
    override fun gainFor(item: PlaybackItem, format: androidx.media3.common.Format?): GainInfo? = null
}
