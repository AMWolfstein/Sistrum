package com.amwolfstein.sistrum.player.normalization

import com.amwolfstein.sistrum.player.NormalizationMode

/** FR-047j seam: Auto retains Track behavior until queue-based resolution is implemented. */
internal fun resolveNormalizationMode(mode: NormalizationMode, queueSingleAlbum: Boolean): NormalizationMode =
    if (mode == NormalizationMode.Album) NormalizationMode.Album else NormalizationMode.Track

/** FR-047j seam: queue album classification is implemented in the following task. */
internal fun queueIsSingleAlbum(albumIds: List<String>): Boolean = false
