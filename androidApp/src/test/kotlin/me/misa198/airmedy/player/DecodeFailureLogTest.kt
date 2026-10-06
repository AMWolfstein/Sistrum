package me.misa198.airmedy.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.fakes.FakeClock
import me.misa198.airmedy.player.fakes.FakeEngineFactory
import me.misa198.airmedy.player.fakes.FakeFocus
import me.misa198.airmedy.player.fakes.FakeLibrary
import me.misa198.airmedy.player.fakes.FakeListening
import me.misa198.airmedy.player.fakes.FakeLog
import me.misa198.airmedy.player.fakes.FakeNowPlaying
import me.misa198.airmedy.player.fakes.FakeResolver
import me.misa198.airmedy.player.fakes.FakeScrobble
import me.misa198.airmedy.player.fakes.FakeSessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** FR-064a: the bounded decode-failure log, its export format and its coordinator wiring. */
class DecodeFailureLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(
        timeMs: Long,
        fileName: String = "a.flac",
        format: String = "flac",
        codec: String = "flac",
        provider: String = "platform",
        error: String = "RuntimeException: bad frame",
    ) = DecodeFailureEntry(timeMs, fileName, format, codec, provider, error)

    @Test
    fun `record round-trips every field`() {
        val log = DecodeFailureLog(folder.newFile("log.jsonl"))
        val entry = entry(123L, "song.mp3", "mp3", "mp3", "media3", "IllegalStateException: nope")

        log.record(entry)

        assertEquals(listOf(entry), log.entries())
    }

    @Test
    fun `record keeps only the newest entries and preserves order`() {
        val log = DecodeFailureLog(folder.newFile("log.jsonl"), maxEntries = 3)

        (0 until 5).forEach { log.record(entry(it.toLong(), "f$it.flac")) }

        assertEquals(listOf(2L, 3L, 4L), log.entries().map { it.timeMs })
    }

    @Test
    fun `a new instance on the same file reads the persisted entries`() {
        val file = folder.newFile("log.jsonl")
        DecodeFailureLog(file).record(entry(7L))

        assertEquals(listOf(7L), DecodeFailureLog(file).entries().map { it.timeMs })
    }

    @Test
    fun `exportText writes the header and the documented line format`() {
        val log = DecodeFailureLog(folder.newFile("log.jsonl"))
        log.record(entry(0L))

        assertEquals(
            "Sistrum decode-failure log\n" +
                "1970-01-01T00:00:00Z  a.flac  format=flac  codec=flac  provider=platform  " +
                "error=RuntimeException: bad frame\n",
            log.exportText(),
        )
    }

    @Test
    fun `clear empties both the log and the file`() {
        val file = folder.newFile("log.jsonl")
        val log = DecodeFailureLog(file)
        log.record(entry(1L))

        log.clear()

        assertTrue(log.entries().isEmpty())
        assertEquals("", file.readText())
    }

    @Test
    fun `an unreadable line is skipped`() {
        val file = folder.newFile("log.jsonl")
        val validLine = Json.encodeToString(DecodeFailureEntry.serializer(), entry(5L))
        file.writeText("this is not json\n$validLine\n")

        val entries = DecodeFailureLog(file).entries()

        assertEquals(1, entries.size)
        assertEquals(5L, entries.single().timeMs)
    }

    @Test
    fun `a decode failure through the coordinator is recorded`() = runTest {
        val sink = RecordingSink()
        val coordinator = coordinatorWith(sink)

        coordinator.dispatch(PlaybackService.ActionPlay, trackIds = listOf("a", "b")).join()
        testScheduler.advanceUntilIdle()

        engines.current.emit(EngineEvent.Error("platform", "flac", RuntimeException("bad frame")))
        coordinator.tick()
        testScheduler.advanceUntilIdle()

        val recorded = sink.entries.single()
        assertEquals("a.flac", recorded.fileName)
        assertEquals("flac", recorded.format)
        assertEquals("flac", recorded.codec)
        assertEquals("platform", recorded.provider)
        assertEquals("RuntimeException: bad frame", recorded.error)
        assertEquals(0L, recorded.timeMs)
    }

    private class RecordingSink : DecodeFailureSink {
        val entries = mutableListOf<DecodeFailureEntry>()
        override fun record(entry: DecodeFailureEntry) {
            entries += entry
        }
    }

    private lateinit var engines: FakeEngineFactory

    private fun TestScope.coordinatorWith(sink: DecodeFailureSink): PlaybackCoordinator {
        engines = FakeEngineFactory()
        return PlaybackCoordinator(
            scope = this,
            queue = PlaybackQueue(),
            flows = PlaybackFlows(
                state = MutableStateFlow<PlaybackState>(PlaybackState.Idle),
                queueState = MutableStateFlow(PlaybackQueueSnapshot()),
                crossfadeSeconds = MutableStateFlow(0),
                blendArtworkDuringCrossfade = MutableStateFlow(false),
                artworkCrossfade = MutableStateFlow<ArtworkCrossfadeTransition?>(null),
                moodRadioActive = MutableStateFlow(false),
            ),
            engineFactory = { engines() },
            resolver = FakeResolver(),
            library = FakeLibrary(),
            nowPlaying = FakeNowPlaying(),
            focus = FakeFocus(),
            listening = FakeListening(),
            scrobble = FakeScrobble(),
            sessionStore = FakeSessionStore(),
            clock = FakeClock(),
            log = FakeLog(),
            listeningTracker = ListeningTracker("test-device") { "id-1" },
            nextArtworkCrossfadeId = { 0L },
            decodeFailures = sink,
        ).also { it.markRestored() }
    }
}
