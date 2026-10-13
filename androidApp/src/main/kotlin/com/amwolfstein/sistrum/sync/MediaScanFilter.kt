/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 AMWolfstein <https://github.com/AMWolfstein>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Folder allow/deny-list scanning technique adapted from Rhythm's
 * MediaScanEngine (https://github.com/cromaguy/Rhythm,
 * app/src/main/java/chromahub/rhythm/app/core/domain/scan/MediaScanEngine.kt).
 */
package com.amwolfstein.sistrum.sync

internal enum class MediaScanMode { Blacklist, Whitelist }

/**
 * Folder-prefix scan filter applied while iterating the MediaStore cursor. In
 * [MediaScanMode.Whitelist] mode, only files under a whitelisted folder are scanned
 * (and an empty whitelist scans nothing); in [MediaScanMode.Blacklist] mode, every
 * file is scanned except those under a blacklisted folder.
 */
internal data class MediaScanFilter(
    val mode: MediaScanMode = MediaScanMode.Blacklist,
    val whitelistedFolders: Set<String> = emptySet(),
    val blacklistedFolders: Set<String> = emptySet(),
) {
    fun allows(path: String): Boolean {
        val normalized = path.lowercase()
        return when (mode) {
            MediaScanMode.Whitelist ->
                whitelistedFolders.isNotEmpty() && whitelistedFolders.any { normalized.isInFolder(it) }
            MediaScanMode.Blacklist ->
                blacklistedFolders.isEmpty() || blacklistedFolders.none { normalized.isInFolder(it) }
        }
    }
}

/**
 * True when this (lowercased) path is [folder] itself or lies under it. A plain prefix
 * check would also match sibling folders sharing the prefix (".../Music" matching
 * ".../MusicVideos"). The picker stores the storage root with a trailing slash
 * ("/storage/emulated/0/"), so that is trimmed before appending the separator.
 */
private fun String.isInFolder(folder: String): Boolean {
    val prefix = folder.lowercase().trimEnd('/')
    return this == prefix || startsWith("$prefix/")
}
