package com.amwolfstein.sistrum.player.normalization

import com.amwolfstein.sistrum.player.NormalizationMode

/** Resolve Auto once for each entering stream. */
internal fun resolveNormalizationMode(mode: NormalizationMode, queueSingleAlbum: Boolean): NormalizationMode =
    if (mode == NormalizationMode.Album || (mode == NormalizationMode.Auto && queueSingleAlbum)) {
        NormalizationMode.Album
    } else NormalizationMode.Track

/** Empty queues and unknown album ids cannot identify a single album. */
internal fun queueIsSingleAlbum(albumIds: List<String>): Boolean {
    val albumId = albumIds.firstOrNull()?.takeIf { it.isNotBlank() } ?: return false
    return albumIds.all { it == albumId }
}
