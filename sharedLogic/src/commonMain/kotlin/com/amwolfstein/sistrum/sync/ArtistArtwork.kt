package com.amwolfstein.sistrum.sync

/**
 * User-staged artist artwork, stored under the app's local directory.
 */
data class StagedArtistArtwork(
    val artistId: String,
    val sha256: String,
    val mime: String,
    val size: Long,
    val relativePath: String,
)