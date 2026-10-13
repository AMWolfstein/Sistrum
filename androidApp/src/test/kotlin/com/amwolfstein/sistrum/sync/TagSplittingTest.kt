package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scanner builds each track's artists, album artists and composers with
 * localArtistsOf/localComposersOf. They used to split on ";" only; they now use the
 * configured tag separators.
 */
class TagSplittingTest {

    private val defaults = TagSeparatorSettings()

    @Test fun `default separators split every offered delimiter`() {
        assertEquals(
            listOf("A", "B", "C", "عمرو دياب", "تامر حسني"),
            localArtistsOf("A; B/C، عمرو دياب، تامر حسني", defaults).map { it.name },
        )
        assertEquals(listOf("Amr Diab", "R3HAB"), localArtistsOf("Amr Diab, R3HAB", defaults).map { it.name })
        assertEquals(
            listOf("Skylar Grey", "Polo G", "Mozzy", "Eminem"),
            localArtistsOf("Skylar Grey, Polo G, Mozzy & Eminem", defaults).map { it.name },
        )
    }

    @Test fun `configured delimiters change what the scanner extracts`() {
        val semicolonOnly = TagSeparatorSettings(delimiters = listOf(";"))
        assertEquals(listOf("Amr Diab, R3HAB"), localArtistsOf("Amr Diab, R3HAB", semicolonOnly).map { it.name })

        val featured = TagSeparatorSettings(delimiters = listOf("feat."))
        assertEquals(listOf("Daft Punk", "Pharrell Williams"), localArtistsOf("Daft Punk feat. Pharrell Williams", featured).map { it.name })
    }

    @Test fun `turning separation off keeps the whole tag as one name`() {
        val off = TagSeparatorSettings(enabled = false)
        assertEquals(listOf("A; B/C"), localArtistsOf("A; B/C", off).map { it.name })
        assertEquals(listOf("A & B; C"), localComposersOf("A & B; C", off).map { it.name })
    }

    @Test fun `composers share the artist separators`() {
        assertEquals(
            listOf("Mostafa Elassal", "Amr Tayam", "Ahmed"),
            localComposersOf("Mostafa Elassal & Amr Tayam; Ahmed", defaults).map { it.name },
        )
        val semicolonOnly = TagSeparatorSettings(delimiters = listOf(";"))
        assertEquals(
            listOf("Mostafa Elassal & Amr Tayam", "Ahmed"),
            localComposersOf("Mostafa Elassal & Amr Tayam; Ahmed", semicolonOnly).map { it.name },
        )
    }

    @Test fun `ids are built per name after splitting, with diacritics folded`() {
        val refs = localArtistsOf("Björk/Bjork", defaults)
        assertEquals(listOf("Björk", "Bjork"), refs.map { it.name })
        assertEquals(artistId("Bjork"), refs[0].id)
        assertEquals(refs[0].id, refs[1].id)
        assertEquals(composerId("Hans Zimmer"), localComposersOf("Hans Zimmer; Lisa Gerrard", defaults).first().id)
    }

    @Test fun `escaped delimiters survive the scan`() {
        assertEquals(listOf("AC/DC", "Brian Johnson"), localArtistsOf("AC\\/DC; Brian Johnson", defaults).map { it.name })
    }

    @Test fun `a blank artist tag still yields one placeholder artist, a blank composer none`() {
        assertEquals(listOf(""), localArtistsOf("", defaults).map { it.name })
        assertEquals(emptyList(), localComposersOf("", defaults))
    }

    @Test fun `the settings signature changes with the delimiters and the switch`() {
        val base = TagSeparatorSettings()
        assertEquals(base.signature, TagSeparatorSettings(delimiters = ArtistSeparator.DEFAULT_TOKENS.toList()).signature)
        assert(base.signature != TagSeparatorSettings(delimiters = listOf(";")).signature)
        assert(base.signature != TagSeparatorSettings(enabled = false).signature)
    }

    // --- Genres ---

    private fun genres(vararg raw: String, settings: TagSeparatorSettings = defaults) = localGenresOf(raw.toList(), settings).map { it.name }

    @Test fun `genre defaults are semicolon and the Arabic comma only`() {
        assertEquals(listOf(";", "\u060C"), ArtistSeparator.DEFAULT_GENRE_TOKENS)
        assertEquals(ArtistSeparator.DEFAULT_GENRE_TOKENS, defaults.genreDelimiters)
    }

    @Test fun `combined genre entries from the test files resolve to real genres`() {
        // The three Genres-table entries Android left unsplit on the CPH2307.
        assertEquals(listOf("Soundtrack", "Hip-Hop/Rap"), genres("Soundtrack; Hip-Hop/Rap"))
        assertEquals(listOf("Hip-Hop/Rap", "Soundtrack"), genres("Hip-Hop/Rap; Soundtrack"))
        assertEquals(
            listOf("Hip-Hop/Rap", "Hardcore Rap", "Rap", "Underground Rap"),
            genres("Hip-Hop/Rap; Hardcore Rap; Rap; Underground Rap"),
        )
        assertEquals(listOf("Mix"), genres("Mix"))
    }

    @Test fun `slash and ampersand inside genre names are kept`() {
        assertEquals(listOf("Hip-Hop/Rap", "R&B/Soul", "Singer/Songwriter", "Drum & Bass"), genres("Hip-Hop/Rap", "R&B/Soul", "Singer/Songwriter", "Drum & Bass"))
        assertEquals(listOf("Pop", "Rock"), genres("Pop\u060C Rock"))
    }

    @Test fun `genres from several entries are merged by id`() {
        // "Rap" and "rap" fold to the same genre id; the first spelling wins.
        assertEquals(listOf("Soundtrack", "Hip-Hop/Rap", "Rap"), genres("Soundtrack; Hip-Hop/Rap", "Hip-Hop/Rap; Soundtrack; Rap", "rap"))
        assertEquals(genreId("Soundtrack"), localGenresOf(listOf("Soundtrack; Rap"), defaults).first().id)
    }

    @Test fun `genre delimiters are configured separately and share the switch`() {
        val slashToo = TagSeparatorSettings(genreDelimiters = listOf(";", "/"))
        assertEquals(listOf("Soundtrack", "Hip-Hop", "Rap"), genres("Soundtrack; Hip-Hop/Rap", settings = slashToo))
        assertEquals(listOf("Soundtrack; Hip-Hop/Rap"), genres("Soundtrack; Hip-Hop/Rap", settings = TagSeparatorSettings(enabled = false)))
        assertEquals(listOf("Hip-Hop/Rap"), genres("Hip-Hop\\/Rap", settings = slashToo))
    }

    @Test fun `blank genre entries are dropped`() {
        assertEquals(emptyList(), genres("", "  "))
    }

    @Test fun `the signature includes the genre delimiters`() {
        assert(TagSeparatorSettings().signature != TagSeparatorSettings(genreDelimiters = listOf(";")).signature)
    }
}
