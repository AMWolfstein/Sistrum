package me.misa198.airmedy.player

import android.media.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NowPlayingMetadataTest {
    @Test
    fun `full item maps every text key in order`() {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "Title",
            artist = "Artist",
            audioPath = "/audio/track.mp3",
            album = "Album",
            albumArtist = "Album Artist",
        )

        val expected = linkedMapOf(
            MediaMetadata.METADATA_KEY_MEDIA_ID to "track-1",
            MediaMetadata.METADATA_KEY_TITLE to "Title",
            MediaMetadata.METADATA_KEY_ARTIST to "Artist",
            MediaMetadata.METADATA_KEY_ALBUM to "Album",
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST to "Album Artist",
        )

        assertEquals(expected, nowPlayingMetadata(item))
        assertEquals(expected.keys.toList(), nowPlayingMetadata(item).keys.toList())
    }

    @Test
    fun `the media id is the track id unchanged`() {
        val item = PlaybackItem(trackId = " id ", title = "Title", artist = "Artist", audioPath = "/audio/track.mp3")

        assertEquals(" id ", nowPlayingMetadata(item)[MediaMetadata.METADATA_KEY_MEDIA_ID])
    }

    @Test
    fun `blank album and album artist are omitted`() {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "Title",
            artist = "Artist",
            audioPath = "/audio/track.mp3",
            album = "",
            albumArtist = "  ",
        )

        val metadata = nowPlayingMetadata(item)

        assertEquals("track-1", metadata[MediaMetadata.METADATA_KEY_MEDIA_ID])
        assertEquals("Title", metadata[MediaMetadata.METADATA_KEY_TITLE])
        assertEquals("Artist", metadata[MediaMetadata.METADATA_KEY_ARTIST])
        assertFalse(metadata.containsKey(MediaMetadata.METADATA_KEY_ALBUM))
        assertFalse(metadata.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))
    }

    @Test
    fun `multi-artist string is passed through unchanged apart from trim`() {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "Title",
            artist = "Skylar Grey, Polo G",
            audioPath = "/audio/track.mp3",
        )

        assertEquals("Skylar Grey, Polo G", nowPlayingMetadata(item)[MediaMetadata.METADATA_KEY_ARTIST])
    }

    @Test
    fun `album artist different from artist is kept separate`() {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "Title",
            artist = "Artist",
            audioPath = "/audio/track.mp3",
            album = "Album",
            albumArtist = "Album Artist",
        )

        val metadata = nowPlayingMetadata(item)

        assertEquals("Artist", metadata[MediaMetadata.METADATA_KEY_ARTIST])
        assertEquals("Album Artist", metadata[MediaMetadata.METADATA_KEY_ALBUM_ARTIST])
    }

    @Test
    fun `display values with surrounding spaces are trimmed`() {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "  Title  ",
            artist = "  Artist  ",
            audioPath = "/audio/track.mp3",
            album = "  Album  ",
            albumArtist = "  Album Artist  ",
        )

        val expected = linkedMapOf(
            MediaMetadata.METADATA_KEY_MEDIA_ID to "track-1",
            MediaMetadata.METADATA_KEY_TITLE to "Title",
            MediaMetadata.METADATA_KEY_ARTIST to "Artist",
            MediaMetadata.METADATA_KEY_ALBUM to "Album",
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST to "Album Artist",
        )

        assertEquals(expected, nowPlayingMetadata(item))
    }
}
