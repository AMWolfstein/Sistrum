package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Folder filters used a plain startsWith, so a folder also matched every sibling whose
 * name merely begins with it (".../Telegram" also caught ".../TelegramBackup").
 */
class MediaScanFilterTest {

    private val root = "/storage/emulated/0"

    private fun blacklist(vararg folders: String) = MediaScanFilter(MediaScanMode.Blacklist, blacklistedFolders = folders.toSet())
    private fun whitelist(vararg folders: String) = MediaScanFilter(MediaScanMode.Whitelist, whitelistedFolders = folders.toSet())

    @Test fun `blacklisting a folder does not exclude a sibling sharing its prefix`() {
        val filter = blacklist("$root/Telegram")
        assertFalse(filter.allows("$root/Telegram/Telegram Audio/voice.ogg"))
        assertTrue(filter.allows("$root/TelegramBackup/song.mp3"))
    }

    @Test fun `whitelisting a folder does not include a sibling sharing its prefix`() {
        val filter = whitelist("$root/Music")
        assertTrue(filter.allows("$root/Music/song.mp3"))
        assertFalse(filter.allows("$root/MusicVideos/clip.mp3"))
    }

    @Test fun `genuine subfolders at any depth still match`() {
        assertTrue(whitelist("$root/Music").allows("$root/Music/Artist/Album/01 Track.flac"))
        assertFalse(blacklist("$root/Music/Podcasts").allows("$root/Music/Podcasts/2026/ep1.mp3"))
        assertTrue(blacklist("$root/Music/Podcasts").allows("$root/Music/PodcastsArchive/ep1.mp3"))
    }

    @Test fun `matching stays case-insensitive`() {
        assertTrue(whitelist("$root/music").allows("$root/Music/song.mp3"))
        assertFalse(whitelist("$root/music").allows("$root/MusicVideos/clip.mp3"))
    }

    @Test fun `a folder stored with a trailing slash, like the storage root, still matches`() {
        // The folder picker stores the storage root as "/storage/emulated/0/".
        assertTrue(whitelist("$root/").allows("$root/Music/song.mp3"))
        assertTrue(whitelist("$root/Music/").allows("$root/Music/song.mp3"))
        assertFalse(whitelist("$root/Music/").allows("$root/MusicVideos/clip.mp3"))
    }

    @Test fun `empty lists keep their existing meaning`() {
        assertFalse(whitelist().allows("$root/Music/song.mp3"))
        assertTrue(blacklist().allows("$root/Music/song.mp3"))
    }
}
