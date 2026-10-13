package com.amwolfstein.sistrum.lyrics

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** KuGou responses are faked; only the provider's handling of them is under test. */
class KugouLyricsProviderTest {

    private fun lrc(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun provider(candidatesJson: String, contentById: Map<String, String>) = KugouLyricsProvider { base, params ->
        when {
            base.endsWith("/search") -> """{"candidates":$candidatesJson}"""
            base.endsWith("/download") -> contentById[params["id"]]?.let { """{"content":"${lrc(it)}"}""" }
            else -> null
        }
    }

    @Test fun `search results show each candidate's own title and artist, not the query`() = runBlocking {
        val kugou = provider(
            """[{"id":"1","accesskey":"a","song":"Lose Yourself","singer":"Eminem","duration":326000},
               {"id":"2","accesskey":"b","song":"Lose Yourself (Live)","singer":"Eminem","duration":301000}]""",
            mapOf("1" to "[00:01.00]one", "2" to "[00:01.00]two"),
        )
        val results = kugou.search("lose yourself", "eminem", 326)
        assertEquals(listOf("Lose Yourself", "Lose Yourself (Live)"), results.map { it.trackName })
        assertEquals(listOf("Eminem", "Eminem"), results.map { it.artistName })
        assertEquals(listOf(326, 301), results.map { it.duration })
    }

    @Test fun `a candidate without a title or artist falls back to the query`() = runBlocking {
        val kugou = provider("""[{"id":"1","accesskey":"a","duration":200000}]""", mapOf("1" to "[00:01.00]one"))
        val result = kugou.search("typed title", "typed artist", 200).single()
        assertEquals("typed title", result.trackName)
        assertEquals("typed artist", result.artistName)
    }

    @Test fun `auto-fetched lyrics are HTML-decoded the same way as search results`() = runBlocking {
        val raw = "[00:01.00]It&#39;s you &amp; me\n[00:05.00]&quot;Hello&quot; &lt;3"
        val kugou = provider("""[{"id":"1","accesskey":"a","song":"Song","singer":"Artist","duration":200000}]""", mapOf("1" to raw))
        val fetched = kugou.fetch(LyricsTrack(title = "Song", artist = "Artist", album = "", duration = 200))
        assertEquals("[00:01.00]It's you & me\n[00:05.00]\"Hello\" <3", fetched?.content)
        assertEquals("kugou-synced", fetched?.source)
        assertEquals(fetched?.content, kugou.search("Song", "Artist", 200).single().content)
    }
}
