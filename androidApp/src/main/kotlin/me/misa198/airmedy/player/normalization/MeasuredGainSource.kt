package me.misa198.airmedy.player.normalization

import androidx.media3.common.Format
import kotlin.math.pow
import me.misa198.airmedy.player.PlaybackItem

/** Loudness measured for a whole album, in LUFS and dBTP. */
data class AlbumLoudness(val loudnessLufs: Float, val truePeakDbtp: Float)

/** Looks up the measured loudness of an album by its id. */
fun interface AlbumLoudnessLookup {
    fun albumLoudness(albumId: String): AlbumLoudness?
}

/** Produces the measured gain for an item from its [PlaybackItem.analysis] and the album snapshot. */
internal class MeasuredGainSource(private val albums: AlbumLoudnessLookup) : GainSource {
    override fun gainFor(item: PlaybackItem, format: Format?): GainInfo? {
        val analysis = item.analysis ?: return null
        val loudness = analysis.loudnessLufs
        val truePeak = analysis.truePeak
        if (!loudness.isFinite() || !truePeak.isFinite()) return null

        val trackGainDb = -18f - loudness
        val trackPeak = 10.0.pow(truePeak / 20.0).toFloat()

        val albumId = item.albumId
        if (albumId.isBlank()) return GainInfo(trackGainDb, null, trackPeak, null, GainForm.Measured)

        val album = albums.albumLoudness(albumId)
        if (album == null) return GainInfo(trackGainDb, null, trackPeak, null, GainForm.Measured)

        val albumLoudness = album.loudnessLufs
        val albumTruePeak = album.truePeakDbtp
        if (!albumLoudness.isFinite() || !albumTruePeak.isFinite()) {
            return GainInfo(trackGainDb, null, trackPeak, null, GainForm.Measured)
        }

        return GainInfo(
            trackGainDb = trackGainDb,
            albumGainDb = -18f - albumLoudness,
            trackPeak = trackPeak,
            albumPeak = 10.0.pow(albumTruePeak / 20.0).toFloat(),
            form = GainForm.Measured,
        )
    }
}
