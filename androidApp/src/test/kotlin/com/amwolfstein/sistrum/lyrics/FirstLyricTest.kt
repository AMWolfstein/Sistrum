package com.amwolfstein.sistrum.lyrics

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Automatic lyrics fetching used to race every provider and keep whichever answered first,
 * so the network providers were always queried and could beat local lyrics.
 */
class FirstLyricTest {

    private val track = LyricsTrack(title = "Title", artist = "Artist", album = "Album", duration = 200)

    private class FakeProvider(val name: String, val fetch: suspend () -> FetchedLyric?) : LyricsProvider() {
        var calls = 0
        override fun enabled(settings: LyricsSettings) = true
        override suspend fun fetch(track: LyricsTrack): FetchedLyric? { calls++; return fetch() }
        override suspend fun search(title: String, artist: String, duration: Int) = emptyList<LyricsSearchResult>()
    }

    private fun found(source: String) = FetchedLyric("lyrics from $source", source)

    @Test fun `the highest-priority hit wins and later providers are never called`() = runBlocking {
        val embedded = FakeProvider("embedded") { found("embedded-plain") }
        val lrclib = FakeProvider("lrclib") { found("lrclib-synced") }
        assertEquals("embedded-plain", firstLyric(listOf(embedded, lrclib), track)?.source)
        assertEquals(0, lrclib.calls)
    }

    @Test fun `providers without lyrics fall through in order`() = runBlocking {
        val order = mutableListOf<String>()
        val providers = listOf("embedded", "sidecar", "lrclib", "kugou").map { name ->
            FakeProvider(name) { order += name; if (name == "lrclib") found("lrclib-synced") else null }
        }
        assertEquals("lrclib-synced", firstLyric(providers, track)?.source)
        assertEquals(listOf("embedded", "sidecar", "lrclib"), order)
    }

    @Test fun `a failing provider is skipped`() = runBlocking {
        val lrclib = FakeProvider("lrclib") { throw IOException("unreachable") }
        val kugou = FakeProvider("kugou") { found("kugou-synced") }
        assertEquals("kugou-synced", firstLyric(listOf(lrclib, kugou), track)?.source)
    }

    @Test fun `no provider with lyrics returns null`() = runBlocking {
        assertNull(firstLyric(listOf(FakeProvider("embedded") { null }, FakeProvider("sidecar") { null }), track))
    }

    @Test fun `cancellation is not treated as a provider failure`() {
        val later = FakeProvider("kugou") { found("kugou-synced") }
        assertFailsWith<CancellationException> {
            runBlocking { firstLyric(listOf(FakeProvider("lrclib") { throw CancellationException("track changed") }, later), track) }
        }
        assertEquals(0, later.calls)
    }

    @Test fun `cancelling the fetch stops before the next provider`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val slow = FakeProvider("lrclib") { started.complete(Unit); CompletableDeferred<FetchedLyric?>().await() }
        val later = FakeProvider("kugou") { found("kugou-synced") }
        val fetch = launch { firstLyric(listOf(slow, later), track) }
        started.await()
        fetch.cancelAndJoin()
        assertEquals(0, later.calls)
    }
}
