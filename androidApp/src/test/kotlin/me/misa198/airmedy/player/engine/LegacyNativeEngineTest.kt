package me.misa198.airmedy.player.engine

import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.GlobalDspConfig
import me.misa198.airmedy.player.NativeTransition
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.equalizerDspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyNativeEngineTest {
    @Test
    fun `prepare applies config focus prepare seek play in order`() = runTest {
        val harness = EngineHarness()
        harness.nextDuration = 1_000L

        harness.engine.prepare(item("a"), ItemGain(2f), startPositionMs = 500L, startPaused = false)

        assertEquals(
            listOf("setGlobalDspConfig", "setFocusGain(1.0)", "prepare(a.mp3,2.0)", "durationMs", "seekTo(500)", "play"),
            harness.port.calls,
        )
    }

    @Test
    fun `prepare with startPaused pauses instead of playing`() = runTest {
        val harness = EngineHarness()

        harness.engine.prepare(item("a"), ItemGain(0f), startPositionMs = 0L, startPaused = true)

        assertEquals(
            listOf("setGlobalDspConfig", "setFocusGain(1.0)", "prepare(a.mp3,0.0)", "pause"),
            harness.port.calls,
        )
    }

    @Test
    fun `prepare without start position skips seek`() = runTest {
        val harness = EngineHarness()

        harness.engine.prepare(item("a"), ItemGain(0f), startPositionMs = 0L, startPaused = false)

        assertFalse(harness.port.calls.contains("durationMs"))
        assertTrue(harness.port.calls.none { it.startsWith("seekTo") })
    }

    @Test
    fun `prepare clamps start position to duration`() = runTest {
        val harness = EngineHarness()
        harness.nextDuration = 1_000L

        harness.engine.prepare(item("a"), ItemGain(0f), startPositionMs = 5_000L, startPaused = false)

        assertTrue(harness.port.calls.contains("seekTo(1000)"))
    }

    @Test
    fun `a second prepare closes the previous port first`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        val first = harness.ports[0]

        harness.engine.prepare(item("b"), ItemGain(0f), 0L, false)

        assertTrue(first.closed)
        assertEquals(2, harness.ports.size)
        assertTrue(harness.ports.last().calls.contains("prepare(b.mp3,0.0)"))
        assertEquals(false, harness.previousClosedAtCreation[0])
        assertEquals(true, harness.previousClosedAtCreation[1])
    }

    @Test
    fun `a failing prepare leaves no port and closes the previous one`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        val first = harness.ports[0]
        harness.nextPrepareError = IllegalStateException("boom")

        var thrown: Throwable? = null
        try {
            harness.engine.prepare(item("b"), ItemGain(0f), 0L, false)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalStateException)
        assertTrue(first.closed)
        assertTrue(harness.ports[1].closed)
        assertEquals(0L, harness.engine.positionMs())

        val firstPlays = first.calls.count { it == "play" }
        val secondPlays = harness.ports[1].calls.count { it == "play" }
        harness.engine.play()
        assertEquals(firstPlays, first.calls.count { it == "play" })
        assertEquals(secondPlays, harness.ports[1].calls.count { it == "play" })
    }

    @Test
    fun `events drained before a failed prepare are still returned`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.engine.preloadNext(item("b"), ItemGain(0f))
        harness.port.pending = NativeTransition.GaplessPromoted
        harness.nextPrepareError = IllegalStateException("boom")

        var thrown: Throwable? = null
        try {
            harness.engine.prepare(item("c"), ItemGain(0f), 0L, false)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalStateException)
        assertEquals(listOf(EngineEvent.GaplessAdvanced(item("b"))), harness.engine.pollEvents())
        assertTrue(harness.engine.pollEvents().isEmpty())
    }

    @Test
    fun `prepare failure closes the new port and leaves the engine without a port`() = runTest {
        val harness = EngineHarness()
        harness.nextPrepareError = IllegalStateException("boom")

        var thrown: Throwable? = null
        try {
            harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalStateException)
        assertTrue(harness.ports.single().closed)
        assertEquals(0L, harness.engine.positionMs())
        assertTrue(harness.engine.pollEvents().isEmpty())
    }

    @Test
    fun `gapless promotion surfaces the preloaded item once`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.engine.preloadNext(item("b"), ItemGain(0f))

        harness.port.pending = NativeTransition.GaplessPromoted

        assertEquals(listOf(EngineEvent.GaplessAdvanced(item("b"))), harness.engine.pollEvents())
        assertTrue(harness.engine.pollEvents().isEmpty())
        assertFalse(harness.engine.hasPreloaded())
    }

    @Test
    fun `crossfade promotion surfaces the preloaded item with the fade duration`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.engine.preloadNext(item("b"), ItemGain(0f))

        assertTrue(harness.engine.beginCrossfade(5_000L))
        harness.port.pending = NativeTransition.CrossfadeStarted

        assertEquals(listOf(EngineEvent.TransitionStarted(item("b"), 5_000L)), harness.engine.pollEvents())
    }

    @Test
    fun `beginCrossfade is a no-op without a preloaded item`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)

        assertFalse(harness.engine.beginCrossfade(5_000L))
        assertTrue(harness.port.calls.none { it.startsWith("beginCrossfade") })
    }

    @Test
    fun `beginCrossfade is a no-op while already crossfading`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.engine.preloadNext(item("b"), ItemGain(0f))
        assertTrue(harness.engine.beginCrossfade(3_000L))
        val startsBefore = harness.port.calls.count { it.startsWith("beginCrossfade") }

        assertFalse(harness.engine.beginCrossfade(3_000L))
        assertEquals(startsBefore, harness.port.calls.count { it.startsWith("beginCrossfade") })
    }

    @Test
    fun `two transitions within one poll are delivered in order`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.engine.preloadNext(item("b"), ItemGain(0f))
        harness.port.pending = NativeTransition.GaplessPromoted
        harness.engine.preloadNext(item("c"), ItemGain(0f))
        harness.engine.beginCrossfade(3_000L)
        harness.port.pending = NativeTransition.CrossfadeStarted

        assertEquals(
            listOf(EngineEvent.GaplessAdvanced(item("b")), EngineEvent.TransitionStarted(item("c"), 3_000L)),
            harness.engine.pollEvents(),
        )
    }

    @Test
    fun `ended is reported once per rising edge`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)

        harness.port.finished = true
        assertEquals(listOf(EngineEvent.Ended), harness.engine.pollEvents())
        assertTrue(harness.engine.pollEvents().isEmpty())

        harness.port.finished = false
        assertTrue(harness.engine.pollEvents().isEmpty())
        harness.port.finished = true
        assertEquals(listOf(EngineEvent.Ended), harness.engine.pollEvents())

        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.port.finished = true
        assertEquals(listOf(EngineEvent.Ended), harness.engine.pollEvents())
    }

    @Test
    fun `output disconnected is reported once per rising edge`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)

        harness.port.disconnected = true
        assertEquals(listOf(EngineEvent.OutputDisconnected), harness.engine.pollEvents())
        assertTrue(harness.engine.pollEvents().isEmpty())

        harness.port.disconnected = false
        assertTrue(harness.engine.pollEvents().isEmpty())
        harness.port.disconnected = true
        assertEquals(listOf(EngineEvent.OutputDisconnected), harness.engine.pollEvents())

        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.port.disconnected = true
        assertEquals(listOf(EngineEvent.OutputDisconnected), harness.engine.pollEvents())
    }

    @Test
    fun `one poll orders transition disconnected then ended`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.engine.preloadNext(item("b"), ItemGain(0f))
        harness.port.pending = NativeTransition.GaplessPromoted
        harness.port.disconnected = true
        harness.port.finished = true

        assertEquals(
            listOf(EngineEvent.GaplessAdvanced(item("b")), EngineEvent.OutputDisconnected, EngineEvent.Ended),
            harness.engine.pollEvents(),
        )
        assertTrue(harness.engine.pollEvents().isEmpty())
    }

    @Test
    fun `a transition without a preloaded item is consumed silently`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.port.pending = NativeTransition.GaplessPromoted

        assertTrue(harness.engine.pollEvents().isEmpty())
        assertEquals(null, harness.port.pending)
        assertTrue(harness.engine.pollEvents().isEmpty())
    }

    @Test
    fun `a failed preload stores nothing and its promotion is ignored`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.port.preloadResult = false

        harness.engine.preloadNext(item("b"), ItemGain(0f))

        assertFalse(harness.engine.hasPreloaded())
        harness.port.pending = NativeTransition.GaplessPromoted
        assertTrue(harness.engine.pollEvents().isEmpty())
    }

    @Test
    fun `a throwing preload rethrows and stores nothing`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        harness.port.preloadError = IllegalStateException("preload")

        var thrown: Throwable? = null
        try {
            harness.engine.preloadNext(item("b"), ItemGain(0f))
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalStateException)
        assertFalse(harness.engine.hasPreloaded())
        harness.port.pending = NativeTransition.GaplessPromoted
        assertTrue(harness.engine.pollEvents().isEmpty())
    }

    @Test
    fun `setGains forwards the analysis gains`() = runTest {
        val harness = EngineHarness()
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)

        harness.engine.setGains(ItemGain(-3f), ItemGain(2f))
        assertEquals("setNormalizationGains(-3.0,2.0)", harness.port.calls.last())

        harness.engine.setGains(ItemGain(1f), null)
        assertEquals("setNormalizationGains(1.0,0.0)", harness.port.calls.last())
    }

    @Test
    fun `setDsp and setFocusGain reach the live port and the next prepare`() = runTest {
        val harness = EngineHarness()
        val settings = EqualizerSettings(enabled = true, presetKey = "rock")
        val expected = equalizerDspConfig(settings)
        assertTrue(expected.eqBandGainsDb.any { it != 0f })
        harness.engine.prepare(item("a"), ItemGain(0f), 0L, false)
        val live = harness.port

        harness.engine.setFocusGain(0.2f)
        harness.engine.setDsp(settings)

        assertEquals(0.2f, live.focusGains.last())
        assertEquals(2, live.dspConfigs.size)
        assertTrue(live.dspConfigs.last().eqBandGainsDb.contentEquals(expected.eqBandGainsDb))
        assertEquals(expected.preampGainDb, live.dspConfigs.last().preampGainDb, 0f)
        assertEquals(expected.stereoWidth, live.dspConfigs.last().stereoWidth, 0f)

        val callsBeforeNormalization = live.calls.size
        harness.engine.setNormalization(NormalizationSettings())
        assertEquals(callsBeforeNormalization, live.calls.size)

        harness.engine.prepare(item("b"), ItemGain(0f), 0L, false)
        val next = harness.ports.last()

        assertEquals(
            listOf("setGlobalDspConfig", "setFocusGain(0.2)", "prepare(b.mp3,0.0)", "play"),
            next.calls,
        )
        assertEquals(1, next.dspConfigs.size)
        assertTrue(next.dspConfigs.single().eqBandGainsDb.contentEquals(expected.eqBandGainsDb))
        assertFalse(next.dspConfigs.single().eqBandGainsDb.contentEquals(GlobalDspConfig().eqBandGainsDb))
        assertEquals(0.2f, next.focusGains.first())
    }

    @Test
    fun `calls without a prepared port are safe no-ops`() {
        val harness = EngineHarness()

        harness.engine.play()
        harness.engine.pause()
        harness.engine.seekTo(100L)
        harness.engine.snapCrossfade()

        assertEquals(0L, harness.engine.positionMs())
        assertEquals(0L, harness.engine.durationMs())
        assertTrue(harness.engine.pollEvents().isEmpty())
        assertTrue(harness.ports.isEmpty())
    }

    @Test
    fun `kind is native`() {
        assertEquals(EngineKind.Native, EngineHarness().engine.kind)
    }

    private fun item(id: String) = PlaybackItem(trackId = id, title = id, artist = "artist", audioPath = "$id.mp3")
}

private class EngineHarness {
    val ports = mutableListOf<FakeNativeDecoderPort>()
    val previousClosedAtCreation = mutableListOf<Boolean>()
    var nextDuration = 0L
    var nextPrepareError: Throwable? = null
    val engine = LegacyNativeEngine {
        val previous = ports.lastOrNull()
        FakeNativeDecoderPort().also {
            it.duration = nextDuration
            it.prepareError = nextPrepareError
            ports += it
            previousClosedAtCreation += previous?.closed == true
        }
    }
    val port: FakeNativeDecoderPort get() = ports.last()
}

private class FakeNativeDecoderPort : NativeDecoderPort {
    val calls = mutableListOf<String>()
    val dspConfigs = mutableListOf<GlobalDspConfig>()
    val focusGains = mutableListOf<Float>()
    var duration = 0L
    var position = 0L
    var preloadedFlag = false
    var crossfading = false
    var preloadResult = true
    var preloadError: Throwable? = null
    var prepareError: Throwable? = null
    var finished = false
    var disconnected = false
    var pending: NativeTransition? = null
    var closed = false

    override fun prepare(path: String, normalizationGainDb: Float) {
        calls += "prepare($path,$normalizationGainDb)"
        prepareError?.let { throw it }
        preloadedFlag = false
    }

    override fun preload(path: String, normalizationGainDb: Float): Boolean {
        calls += "preload($path,$normalizationGainDb)"
        preloadError?.let { throw it }
        preloadedFlag = preloadResult
        return preloadResult
    }

    override fun setNormalizationGains(activeDb: Float, preloadedDb: Float) {
        calls += "setNormalizationGains($activeDb,$preloadedDb)"
    }

    override fun setFocusGain(gain: Float) {
        calls += "setFocusGain($gain)"
        focusGains += gain
    }

    override fun clearPreloaded() {
        calls += "clearPreloaded"
        preloadedFlag = false
    }

    override fun hasPreloaded(): Boolean {
        calls += "hasPreloaded"
        return preloadedFlag
    }

    override fun beginCrossfade(durationMs: Long) {
        calls += "beginCrossfade($durationMs)"
        crossfading = true
    }

    override fun snapCrossfade() {
        calls += "snapCrossfade"
        crossfading = false
    }

    override fun isCrossfading(): Boolean {
        calls += "isCrossfading"
        return crossfading
    }

    override fun consumeTransition(): NativeTransition? {
        calls += "consumeTransition"
        val transition = pending
        pending = null
        return transition
    }

    override fun setGlobalDspConfig(config: GlobalDspConfig) {
        calls += "setGlobalDspConfig"
        dspConfigs += config
    }

    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun seekTo(positionMs: Long) { calls += "seekTo($positionMs)" }
    override fun durationMs(): Long { calls += "durationMs"; return duration }
    override fun positionMs(): Long { calls += "positionMs"; return position }
    override fun isFinished(): Boolean { calls += "isFinished"; return finished }
    override fun isOutputDisconnected(): Boolean { calls += "isOutputDisconnected"; return disconnected }
    override fun close() { calls += "close"; closed = true }
}
