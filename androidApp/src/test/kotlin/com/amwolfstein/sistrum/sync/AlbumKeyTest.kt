package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Albums used to be keyed by MediaStore's ALBUM_KEY, which is the album name alone, so
 * same-named albums by different artists (e.g. two "القمر" albums on the CPH2307) merged.
 */
class AlbumKeyTest {

    @Test fun `same-named albums with different MediaStore album ids stay separate`() {
        val first = albumKey(7060977440053347243, "Samo Zaen", "القمر")
        val second = albumKey(712056836689051045, "Ramy Sabry", "القمر")
        assertNotEquals(first, second)
        assertEquals("7060977440053347243", first)
        assertEquals("712056836689051045", second)
    }

    @Test fun `the MediaStore album id decides grouping over the artist name`() {
        assertEquals(albumKey(42, "Hugel", "I Adore You"), albumKey(42, "Tamer Hosny", "I Adore You"))
    }

    @Test fun `without an album id the fallback separates album artists`() {
        assertNotEquals(albumKey(null, "Samo Zaen", "القمر"), albumKey(null, "Ramy Sabry", "القمر"))
        assertEquals(albumKey(null, "Samo Zaen", "القمر"), albumKey(0, "Samo Zaen", "القمر"))
        assertEquals(12, albumKey(null, "Samo Zaen", "القمر").length)
    }
}
