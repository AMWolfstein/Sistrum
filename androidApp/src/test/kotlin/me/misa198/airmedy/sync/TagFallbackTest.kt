package me.misa198.airmedy.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * MediaStore reports no artist/album-artist/album for some containers (seen with WAV
 * carrying an `id3 ` chunk). The scanner falls back to the file's own tags, but only
 * when MediaStore has no value. MediaStore's album for an untagged file is the parent
 * folder name, which the file's own album tag may override.
 */
class TagFallbackTest {

    @Test fun `media store values win over the embedded tags for every field`() {
        val fields = scanTagFields(
            mediaStoreArtist = "MS Artist",
            mediaStoreAlbumArtist = "MS Album Artist",
            mediaStoreAlbum = "MS Album",
            tagArtist = "Tag Artist",
            tagAlbumArtist = "Tag Album Artist",
            tagAlbum = "Tag Album",
            parentFolderName = "Music",
        )
        assertEquals("MS Artist", fields.artist)
        assertEquals("MS Album Artist", fields.albumArtist)
        assertEquals("MS Album", fields.album)
    }

    @Test fun `missing media store values fall back to the tags`() {
        val fields = scanTagFields(
            mediaStoreArtist = null,
            mediaStoreAlbumArtist = null,
            mediaStoreAlbum = null,
            tagArtist = "Tag Artist",
            tagAlbumArtist = "Tag Album Artist",
            tagAlbum = "Tag Album",
            parentFolderName = null,
        )
        assertEquals("Tag Artist", fields.artist)
        assertEquals("Tag Album Artist", fields.albumArtist)
        assertEquals("Tag Album", fields.album)
    }

    @Test fun `no media store value and no tag yields empty artist and album`() {
        val fields = scanTagFields(null, null, null, null, null, null, null)
        assertEquals("", fields.artist)
        assertEquals("", fields.album)
        assertEquals("", fields.albumArtist)
    }

    @Test fun `album artist falls back to the resolved artist`() {
        // The artist itself came from the tag.
        val fromTag = scanTagFields(null, null, null, "Tag Artist", null, null, null)
        assertEquals("Tag Artist", fromTag.albumArtist)

        // The artist came from MediaStore.
        val fromMediaStore = scanTagFields("MS Artist", null, null, null, null, null, null)
        assertEquals("MS Artist", fromMediaStore.albumArtist)
    }

    @Test fun `album artist prefers the embedded album artist over the artist`() {
        val fields = scanTagFields(null, null, null, "Tag Artist", "Tag Album Artist", "Tag Album", null)
        assertEquals("Tag Album Artist", fields.albumArtist)
    }

    @Test fun `blank tag values are ignored`() {
        val fields = scanTagFields(null, null, null, "   ", "  ", "\t", null)
        assertEquals("", fields.artist)
        assertEquals("", fields.album)
        assertEquals("", fields.albumArtist)
    }

    @Test fun `folder-name album is overridden by the file's own album tag`() {
        val fields = scanTagFields(
            mediaStoreArtist = null,
            mediaStoreAlbumArtist = null,
            mediaStoreAlbum = "Music",
            tagArtist = "Tag Artist",
            tagAlbumArtist = null,
            tagAlbum = "Hell: The Sequel",
            parentFolderName = "Music",
        )
        assertEquals("Hell: The Sequel", fields.album)
    }

    @Test fun `folder-name album is kept when the file has no album tag`() {
        val nullTag = scanTagFields(null, null, "Music", null, null, null, "Music")
        assertEquals("Music", nullTag.album)
        val blankTag = scanTagFields(null, null, "Music", null, null, "  ", "Music")
        assertEquals("Music", blankTag.album)
    }

    @Test fun `a real media store album is never overridden by the tag`() {
        val fields = scanTagFields(
            mediaStoreArtist = null,
            mediaStoreAlbumArtist = null,
            mediaStoreAlbum = "Real Album",
            tagArtist = null,
            tagAlbumArtist = null,
            tagAlbum = "Other",
            parentFolderName = "Music",
        )
        assertEquals("Real Album", fields.album)
    }

    @Test fun `only the parent folder name triggers the album override`() {
        val fields = scanTagFields(
            mediaStoreArtist = null,
            mediaStoreAlbumArtist = null,
            mediaStoreAlbum = "Music",
            tagArtist = null,
            tagAlbumArtist = null,
            tagAlbum = "Other",
            parentFolderName = "Downloads",
        )
        assertEquals("Music", fields.album)
    }

    @Test fun `album from tag flags the tag-fallback cases`() {
        // Folder-name override: the tag wins.
        assertTrue(scanTagFields(null, null, "Music", null, null, "Album", "Music").albumFromTag)
        // MediaStore has no album: the tag is used.
        assertTrue(scanTagFields(null, null, null, null, null, "Album", "Music").albumFromTag)
        // A real MediaStore album is kept.
        assertFalse(scanTagFields(null, null, "Real Album", null, null, "Other", "Music").albumFromTag)
        // Folder name with no tag is kept.
        assertFalse(scanTagFields(null, null, "Music", null, null, null, "Music").albumFromTag)
        // No album at all.
        assertFalse(scanTagFields(null, null, null, null, null, null, null).albumFromTag)
    }

    @Test fun `tagged tracks in one folder do not collapse onto MediaStore's album id`() {
        // Same folder, same MediaStore album and ALBUM_ID 42, but different real albums.
        val albumA = scanTagFields(null, null, "Music", null, null, "Album A", "Music")
        val albumB = scanTagFields(null, null, "Music", null, null, "Album B", "Music")
        // The scanner's key rule: a tag-derived album ignores the shared ALBUM_ID.
        fun key(fields: ScanTagFields) = albumKey(if (fields.albumFromTag) null else 42L, fields.albumArtist, fields.album)
        assertNotEquals(key(albumA), key(albumB))

        // Untagged tracks in the folder keep MediaStore's album id.
        val untagged = scanTagFields(null, null, "Music", null, null, null, "Music")
        assertFalse(untagged.albumFromTag)
        assertEquals("42", key(untagged))
    }
}
