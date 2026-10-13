package com.amwolfstein.sistrum.player

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import com.amwolfstein.sistrum.player.engine.EngineDecodeException
import com.amwolfstein.sistrum.player.fakes.PlaybackCoordinatorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-064: decode failures thrown during prepare must follow the engine-event error path. */
class PlaybackCoordinatorPrepareFailureTest {

    private fun decodeFailure() = EngineDecodeException("none", "wma", IOException("unrecognized"))

    private fun failFirstPrepare(harness: PlaybackCoordinatorHarness) {
        harness.engines.failPrepareFor = { item ->
            if (item.trackId == "a") decodeFailure() else null
        }
    }

    @Test
    fun `a prepare decode failure logs provider format and track id`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        failFirstPrepare(harness)

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        val line = harness.log.lines.lastOrNull { it.contains("Decode failure") }
        assertNotNull("prepare decode failure must emit a Decode failure log line", line)
        assertTrue(line!!.contains("provider=none"))
        assertTrue(line.contains("format=wma"))
        assertTrue(line.contains("id=a"))
    }

    @Test
    fun `a prepare decode failure records provider codec file format and cause`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        failFirstPrepare(harness)

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        assertEquals("prepare decode failure must record exactly one entry", 1, harness.decodeFailures.entries.size)
        val entry = harness.decodeFailures.entries.single()
        val attemptedItem = harness.engines.created.first().prepareAttempts.single()
        assertEquals("none", entry.provider)
        assertEquals("wma", entry.codec)
        assertEquals(File(attemptedItem.audioPath).extension, entry.format)
        assertEquals(File(attemptedItem.audioPath).name, entry.fileName)
        assertTrue(entry.error.contains("unrecognized"))
    }

    @Test
    fun `a prepare decode failure skips to playing successor without retrying`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        failFirstPrepare(harness)

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a", "b"))

        assertEquals("prepare decode failure must skip to b", "b", harness.queue.snapshot().currentTrackId)
        val successor = PlaybackItem(
            trackId = "b",
            title = "Title b",
            artist = "Artist",
            audioPath = "/music/b.flac",
            artworkPath = "/art/b.jpg",
        )
        assertEquals(PlaybackState.Playing(successor, 0L, 180_000L), harness.flows.state.value)
        assertTrue(harness.nowPlaying.transportStates.contains(TransportState.Error))
        assertEquals(2, harness.engines.created.size)
        assertEquals(
            listOf("a", "b"),
            harness.engines.created.map { it.prepareAttempts.single().trackId },
        )
    }

    @Test
    fun `a long queue caps synchronous prepare decode skips`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        harness.engines.failPrepareFor = { decodeFailure() }
        val tracks = (0 until 50).map { "t$it" }

        harness.send(PlaybackService.ActionPlay, trackIds = tracks)

        assertTrue(harness.flows.state.value is PlaybackState.Failed)
        assertTrue("prepare decode failures must attempt successors before failing", harness.engines.created.size > 1)
        assertTrue("synchronous prepare decode skips must stop after at most 21 engines", harness.engines.created.size <= 21)
        assertEquals(harness.engines.created.size, harness.decodeFailures.entries.size)
        assertEquals(
            tracks.take(harness.engines.created.size),
            harness.engines.created.map { it.prepareAttempts.single().trackId },
        )
    }

    @Test
    fun `a single track prepare decode failure is recorded and fails without retry`() = runTest {
        val harness = PlaybackCoordinatorHarness(this)
        failFirstPrepare(harness)

        harness.send(PlaybackService.ActionPlay, trackIds = listOf("a"))

        val state = harness.flows.state.value
        assertTrue(state is PlaybackState.Failed)
        assertEquals("a", (state as PlaybackState.Failed).trackId)
        assertEquals(1, harness.engines.created.size)
        assertEquals(listOf("a"), harness.engine.prepareAttempts.map { it.trackId })
        assertEquals("last-track prepare decode failure must still be recorded", 1, harness.decodeFailures.entries.size)
    }
}
