package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import me.misa198.airmedy.player.PlaybackItem

/** Loudness measured for a whole album, in LUFS and dBTP. */
data class AlbumLoudness(val loudnessLufs: Float, val truePeakDbtp: Float)

/** Looks up the measured loudness of an album by its id. */
fun interface AlbumLoudnessLookup {
    fun albumLoudness(albumId: String): AlbumLoudness?
}

/** T050 stub; T051 implements the per-item measured gain. */
internal class MeasuredGainSource(private val albums: AlbumLoudnessLookup) : GainSource {
    override fun gainFor(item: PlaybackItem, format: Format?): GainInfo? = null
}
