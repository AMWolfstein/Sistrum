package me.misa198.airmedy.player.media3

import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.equalizerDspConfig
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** T059: run only against the .qa target. Taps are before track volume, so tests may mute output.
 * Timing is reconstructed independently from PCM curve positions and the players' audible clocks,
 * rather than asserting against the engine's chosen delay. Every test reports an instrumentation row.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Media3CrossfadeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000)
    fun twoFadesEmitExactlyOneTransitionEachAndNoGaplessAdvance() = withRig("events") { r ->
        val first = File(CORPUS, "gapless_flac_1.flac")
        val second = File(CORPUS, "gapless_flac_2.flac")
        assumeTrue("missing second FLAC fixture", second.isFile)
        runBlocking { r.engine.prepare(item(first, "a"), ItemGain.Unity, 0L, true) }
        r.engine.seekTo((r.engine.durationMs() - 6_000L).coerceAtLeast(0L))
        r.engine.play()
        val items = listOf(item(second, "b"), item(first, "c"))
        for (next in items) {
            r.schedule(next)
            assertTrue(r.engine.beginCrossfade(FADE_MS))
            r.awaitComplete()
            assertFalse(r.engine.hasPreloaded())
            r.engine.seekTo((r.engine.durationMs() - 6_000L).coerceAtLeast(0L))
        }
        r.drain()
        assertEquals(items, r.events.filterIsInstance<EngineEvent.TransitionStarted>().map { it.incoming })
        assertEquals(listOf(FADE_MS, FADE_MS), r.events.filterIsInstance<EngineEvent.TransitionStarted>().map { it.fadeMs })
        assertEquals(0, r.events.count { it is EngineEvent.GaplessAdvanced })
        // OutputStarted belongs to the play() start only, never to a fade's swap to the incoming player.
        assertEquals(1, r.events.count { it is EngineEvent.OutputStarted })
        r.details = "transitions=2 gapless=0 outputStarted=1"
    }

    @Test(timeout = 120_000)
    fun crossfadeOffCreatesOnlyOnePlayerAcrossTwoAdvances() = withRig("off") { r ->
        r.engine.setCrossfade(0L, true)
        r.prepareTone(startPositionMs = 9_000L)
        for (id in listOf("b", "c")) {
            runBlocking { r.engine.preloadNext(r.toneItem(id), ItemGain.Unity) }
            val before = r.events.count { it is EngineEvent.GaplessAdvanced }
            await { r.drain(); r.events.count { it is EngineEvent.GaplessAdvanced } > before }
            r.engine.seekTo(9_000L)
        }
        assertEquals(1, r.factory.createdPlayersForTest)
        assertEquals(1, r.factory.livePlayers)
        assertEquals(0, r.events.count { it is EngineEvent.TransitionStarted })
        r.details = "created=1 advances=2"
    }

    @Test(timeout = 120_000)
    fun incomingExistsOnlyInWindowAndOutgoingIsReleasedAfterFade() = withRig("lifetime") { r ->
        r.prepareTone(startPositionMs = 0L, paused = true)
        runBlocking { r.engine.preloadNext(r.toneItem("b"), ItemGain.Unity) }
        assertEquals(1, r.factory.createdPlayersForTest)
        r.engine.play()
        SystemClock.sleep(500L)
        assertEquals("B outside the 2s + 5s window", 1, r.factory.livePlayers)
        r.engine.seekTo(4_000L)
        r.awaitIncoming()
        assertEquals(2, r.factory.createdPlayersForTest)
        val shared = r.engine.audioSessionIdForTest
        r.factory.call { r.engine.playersForTest().forEach { assertEquals(shared, it.audioSessionId) } }
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        assertEquals(2, r.factory.livePlayers)
        r.awaitComplete()
        assertEquals(1, r.factory.livePlayers)
        r.details = "created=2 liveAfterFade=1 sharedSession=$shared"
    }

    @Test(timeout = 120_000)
    fun gainStageTeeMatchesEqualPowerCurvesAndEnvelope() = withRig("curves") { r ->
        r.prepareTone()
        r.schedule(r.toneItem("b"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        r.awaitComplete()
        val out = fitCurve(r.gainTaps[0], incoming = false)
        val inc = fitCurve(r.gainTaps[1], incoming = true)
        assertTrue("outgoing cosine error=${out.maxError}", out.maxError < 0.015)
        assertTrue("incoming sine error=${inc.maxError}", inc.maxError < 0.015)
        // Compare measured envelopes at equal progress, independently fitting each PCM curve.
        var peakSum = 0.0
        for (step in 1..9) {
            val progress = step / 10.0
            val a = out.gainAt(progress)
            val b = inc.gainAt(progress)
            val power = a * a + b * b
            assertEquals("power envelope at $progress", 1.0, power, 0.04)
            assertTrue("amplitude envelope at $progress", a + b in 0.98..1.44)
            peakSum = maxOf(peakSum, a + b)
        }
        r.details = "outError=${out.maxError} inError=${inc.maxError} peakSum=$peakSum"
    }

    @Test(timeout = 120_000)
    fun audibleCurveStartsAreWithinFiftyMs() = withRig("timing") { r ->
        r.prepareTone()
        r.schedule(r.toneItem("b"))
        val outgoing = r.engine.playersForTest().first()
        // The tone was opened at 4000ms. Record the audible player clocks on the playback looper.
        val clocks = mutableListOf<Clock>()
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        val incoming = r.engine.playersForTest().first()
        val deadline = SystemClock.elapsedRealtime() + 1_800L
        while (SystemClock.elapsedRealtime() < deadline) {
            r.factory.call {
                clocks += Clock(SystemClock.elapsedRealtime(), outgoing.currentPosition, incoming.currentPosition)
            }
            SystemClock.sleep(5L)
        }
        r.awaitComplete()
        val a = fitCurve(r.gainTaps[0], incoming = false)
        val b = fitCurve(r.gainTaps[1], incoming = true)
        val audibleA = crossingTime(clocks, 4_000.0 + a.startFrame * 1000.0 / RATE, incoming = false)
        val audibleB = crossingTime(clocks, b.startFrame * 1000.0 / RATE, incoming = true)
        val offset = audibleB - audibleA
        r.audibleOffsetMs = offset
        r.details = "audibleA=$audibleA audibleB=$audibleB delayMs=${r.engine.incomingDelayMsForTest}"
        assertTrue("lead=${r.engine.outgoingLeadMsForTest}ms audible start offset=${offset}ms", abs(offset) <= 50.0)
    }

    @Test(timeout = 120_000)
    fun pauseAndSkipFlushEveryOldGainFrameDuringAndAfterFade() = withRig("snap") { r ->
        // Include a pause before B's delayed start, and a pause after it has started.
        for (pauseDelay in listOf(0L, 600L)) {
            r.prepareTone()
            r.schedule(r.toneItem("pause$pauseDelay"))
            assertTrue(r.engine.beginCrossfade(FADE_MS))
            val tap = r.gainTaps.last()
            SystemClock.sleep(pauseDelay)
            // Only flushes after the snap count: B's own start flush (curve armed) may land just before it.
            val snapAt = SystemClock.elapsedRealtimeNanos()
            r.engine.pause()
            assertFalse(r.engine.isCrossfading())
            val p = r.engine.playersForTest().single()
            assertFalse(r.factory.call { p.playWhenReady })
            r.engine.play()
            assertUnfadedAfterFlush(r, tap, snapAt)
        }
        // Skip during a fade: snap + prepare flushes incoming before opening i+2.
        r.prepareTone()
        r.schedule(r.toneItem("skipIncoming"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        SystemClock.sleep(600L)
        val incomingTap = r.gainTaps.last()
        val snapAt = SystemClock.elapsedRealtimeNanos()
        r.engine.snapCrossfade()
        assertUnfadedAfterFlush(r, incomingTap, snapAt)
        r.prepareTone(id = "skipDestination", startPositionMs = 0L)
        await { r.gainTaps.last().windows().size >= 20 }
        assertAllFull(r, r.gainTaps.last().windows().takeLast(20))
        // Skip after a completed fade: snap is harmless, the replacement still starts at full gain.
        r.engine.seekTo(4_000L)
        r.schedule(r.toneItem("afterFade"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        r.awaitComplete()
        r.engine.snapCrossfade()
        r.prepareTone(id = "afterFadeDestination", startPositionMs = 0L)
        await { r.gainTaps.last().windows().size >= 20 }
        assertAllFull(r, r.gainTaps.last().windows().takeLast(20))
        r.details = "pauseBeforeStart=true pauseDuring=true skipDuring=true skipAfter=true"
    }

    @Test(timeout = 120_000)
    fun focusDuckReachesBothPlayersAndSeedsNewIncoming() = withRig("focus") { r ->
        r.prepareTone()
        r.schedule(r.toneItem("b"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        r.engine.setFocusGain(0.2f)
        await {
            r.factory.call { r.engine.playersForTest().all { abs(it.volume - 0.2f) < 0.001f } }
        }
        assertEquals(2, r.engine.playersForTest().size)
        r.awaitComplete()
        r.engine.seekTo(4_000L)
        r.schedule(r.toneItem("c"))
        r.factory.call { r.engine.playersForTest().forEach { assertEquals(0.2f, it.volume, 0.001f) } }
        r.details = "overlapVolumes=0.2,0.2 newIncomingVolume=0.2"
    }

    @Test(timeout = 120_000)
    fun incomingInheritsCurrentEqualizerAndNormalizationAndBothRetarget() = withRig("settings") { r ->
        val eq = EqualizerSettings(enabled = true, legacyOverrideGainsDb = List(10) { if (it == 5) 6f else 0f })
        r.engine.setDsp(eq)
        r.factory.setDsp(equalizerDspConfig(eq).copy(preampGainDb = -3f, stereoWidth = 0.7f))
        val stereo = writeTone(File(context.cacheDir, "t059_settings.wav"), 10, rightHz = 3000)
        val normalization = NormalizationSettings(enabled = true, targetLufs = -18f, preventClip = false, untaggedPreampDb = -6f)
        r.engine.setNormalization(normalization)
        runBlocking { r.engine.prepare(item(stereo, "settingsA"), ItemGain.Unity, 4_000L, false) }
        await { r.gainTaps[0].windows().size >= 100 }
        val gainA = r.gainTaps[0].windows().takeLast(20).map { it.gain }.average()
        val dspA = r.dspTaps[0].windows().takeLast(20).map { it.gain }.average()
        assertEquals("A normalization", Math.pow(10.0, -6.0 / 20), gainA, 0.01)
        r.schedule(item(stereo, "settingsB"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        SystemClock.sleep(600L)
        r.engine.snapCrossfade()
        val b = r.gainTaps[1]
        await { b.windows().takeLast(20).all { abs(it.gain - gainA) < 0.01 } && b.windows().size >= 20 }
        val gainB = b.windows().takeLast(20).map { it.gain }.average()
        val dspB = r.dspTaps[1].windows().takeLast(20).map { it.gain }.average()
        assertEquals("B normalization", gainA, gainB, 0.01)
        assertEquals("B inherited EQ/preamp/width chain", dspA / gainA, dspB / gainB, 0.03)
        assertTrue("non-flat EQ actually applied", dspB / gainB > 1.1)
        // During another fade, retarget both normalization controls and inspect actual PCM curves.
        r.engine.seekTo(4_000L)
        r.schedule(item(stereo, "settingsC"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        r.engine.setNormalization(normalization.copy(untaggedPreampDb = -12f))
        SystemClock.sleep(1_100L)
        val out = r.gainTaps[1].windows().last()
        val inc = r.gainTaps[2].windows().last()
        val outStart = r.engine.gainProcessingPositionForTest(outgoing = true)!!.fadeStartFrame
        val inStart = r.engine.gainProcessingPositionForTest(outgoing = false)!!.fadeStartFrame
        val outPhase = (out.frame + WINDOW / 2.0 - outStart) / (FADE_MS * RATE / 1000.0) * PI / 2
        val inPhase = (inc.frame + WINDOW / 2.0 - inStart) / (FADE_MS * RATE / 1000.0) * PI / 2
        val retargetOut = out.gain / cos(outPhase)
        val retargetIn = inc.gain / sin(inPhase)
        val expectedRetarget = Math.pow(10.0, -12.0 / 20)
        assertEquals("outgoing normalization retarget", expectedRetarget, retargetOut, 0.01)
        assertEquals("incoming normalization retarget", expectedRetarget, retargetIn, 0.01)
        r.engine.snapCrossfade()
        await { r.gainTaps[2].windows().takeLast(20).all { abs(it.gain - Math.pow(10.0, -12.0 / 20)) < 0.01 } }
        r.details = "gainA=$gainA gainB=$gainB eqRatioA=${dspA / gainA} eqRatioB=${dspB / gainB} retargetOut=$retargetOut retargetIn=$retargetIn"
    }

    @Test(timeout = 120_000)
    fun failedIncomingFallsBackAndErrorDuringFadeSnapsWithAttribution() = withRig("errors") { r ->
        r.prepareTone(startPositionMs = 7_000L)
        val missing = PlaybackItem("missing", "missing", "test", File(context.cacheDir, "t059_missing.aifc").absolutePath)
        File(missing.audioPath).delete()
        runBlocking { r.engine.preloadNext(missing, ItemGain.Unity) }
        await { r.factory.createdPlayersForTest >= 2 && r.factory.livePlayers == 1 }
        assertFalse(r.engine.beginCrossfade(FADE_MS))
        assertTrue(r.engine.hasPreloaded())
        // Wait for the current player's normal source failure. Exactly one Error proves B's
        // earlier preparation failure was suppressed even if A prefetches the bad source early.
        await {
            r.drain()
            r.factory.call { r.engine.playersForTest().single().playerError != null } &&
                r.events.any { it is EngineEvent.Error }
        }
        val fallback = r.events.filterIsInstance<EngineEvent.Error>().single()
        assertEquals("error names the failed item, not A's WAV", "aifc", fallback.format)
        assertEquals(0, r.events.count { it is EngineEvent.TransitionStarted })
        r.events.clear()
        r.prepareTone()
        val flac = File(CORPUS, "gapless_flac_2.flac")
        assumeTrue("missing FLAC fixture", flac.isFile)
        r.schedule(item(flac, "flac"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        val outgoing = r.engine.playersForTest().last()
        val format = r.factory.call { outgoing.audioFormat?.sampleMimeType }
        val failure = PlaybackException("injected outgoing source failure", java.io.IOException("outgoing"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        r.engine.injectErrorForTest(outgoing = true, failure)
        assertFalse(r.engine.isCrossfading())
        assertEquals(1, r.factory.livePlayers)
        r.drain()
        val error = r.events.filterIsInstance<EngineEvent.Error>().single()
        assertEquals(format, error.format)
        assertSame(failure, error.cause)
        // Also exercise the incoming player error path during overlap.
        r.prepareTone()
        r.schedule(item(flac, "flacAgain"))
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        val incomingFormat = r.factory.call { r.engine.playersForTest().first().audioFormat?.sampleMimeType } ?: "flac"
        r.events.clear()
        r.engine.injectErrorForTest(outgoing = false, failure)
        r.drain()
        assertFalse(r.engine.isCrossfading())
        assertEquals(incomingFormat, r.events.filterIsInstance<EngineEvent.Error>().single().format)
        r.details = "fallbackFormat=${fallback.format} outgoingErrorFormat=$format incomingErrorFormat=$incomingFormat"
    }

    @Test(timeout = 120_000)
    fun positionAndDurationSwitchToIncomingAtFadeStart() = withRig("position") { r ->
        r.prepareTone()
        val longer = writeTone(File(context.cacheDir, "t059_long.wav"), seconds = 14)
        r.schedule(item(longer, "long"))
        val before = r.engine.positionMs()
        assertTrue(before > 4_000L)
        assertTrue(r.engine.beginCrossfade(FADE_MS))
        val position = r.engine.positionMs()
        assertTrue("incoming position from the moment of swap: $position", position < 100L)
        assertEquals(14_000L, r.engine.durationMs())
        r.awaitComplete()
        assertTrue(r.engine.positionMs() in 1_000L..5_000L)
        assertEquals(14_000L, r.engine.durationMs())
        r.details = "outgoingPosition=$before incomingPositionAtSwap=$position incomingDuration=14000"
    }

    @Test(timeout = 120_000)
    fun readyIncomingSurvivesLongPauseAndStartsWhenFadeBegunAtTheEnd() = withRig("lateFade") { r ->
        r.prepareTone()
        r.schedule(r.toneItem("late"))
        // A pause inside the pre-buffer window longer than the 10 s preparation deadline keeps a ready B.
        r.engine.pause()
        SystemClock.sleep(11_000L)
        r.factory.call { r.engine.playersForTest().size }.let { assertEquals("B kept while paused", 2, it) }
        assertTrue(r.engine.hasPreloaded())
        // Begin the fade with ~500 ms left: A's processor has usually decoded its last frame already, so the
        // fade request may never be observed; B must still start and play at full level after the fade.
        r.engine.seekTo(9_500L)
        r.engine.play()
        await { r.engine.positionMs() > 9_500L }
        assertTrue(r.engine.beginCrossfade(500L))
        val incoming = r.engine.playersForTest().first()
        val tap = r.gainTaps.last()
        await { r.factory.call { incoming.isPlaying } }
        r.awaitComplete()
        await { tap.windows().takeLast(20).let { rows -> rows.size == 20 && rows.all { 20 * log10(it.peak / AMP) >= -0.5 } } }
        val position = r.engine.positionMs()
        assertTrue("incoming clock advances: $position", position > 0L)
        assertEquals(1, r.factory.livePlayers)
        r.details = "pausedMs=11000 incomingPosition=$position lead=${r.engine.outgoingLeadMsForTest}"
    }

    private inner class Rig : AutoCloseable {
        val gainTaps = mutableListOf<Tap>()
        val dspTaps = mutableListOf<Tap>()
        val factory = Media3PlayerFactory(context,
            afterGainProcessors = { Tap().also { gainTaps += it }.let { arrayOf<AudioProcessor>(TeeAudioProcessor(it)) } },
            audioProcessors = { Tap().also { dspTaps += it }.let { arrayOf<AudioProcessor>(TeeAudioProcessor(it)) } },
        )
        val engine = Media3Engine(factory)
        val tone = writeTone(File(context.cacheDir, "t059_tone.wav"), 10)
        val events = mutableListOf<EngineEvent>()
        var details = ""
        var audibleOffsetMs = Double.NaN
        var snapPeak = Double.NaN
        init { engine.setCrossfade(FADE_MS, true); engine.setFocusGain(0f) }
        fun toneItem(id: String) = item(tone, id)
        fun prepareTone(id: String = "a", startPositionMs: Long = 4_000L, paused: Boolean = false) {
            runBlocking { engine.prepare(toneItem(id), ItemGain.Unity, startPositionMs, paused) }
            if (!paused) await { engine.positionMs() > startPositionMs + 100L }
        }
        fun schedule(next: PlaybackItem) {
            runBlocking { engine.preloadNext(next, ItemGain.Unity) }
            awaitIncoming()
        }
        fun awaitIncoming() = await {
            factory.livePlayers == 2 && factory.call {
                engine.playersForTest().all { it.playbackState == Player.STATE_READY && it.audioSessionId == engine.audioSessionIdForTest }
            }
        }
        fun drain() { events += engine.pollEvents() }
        fun awaitComplete() = await { drain(); !engine.isCrossfading() && factory.livePlayers == 1 }
        override fun close() { engine.close(); assertEquals("leaked players", 0, factory.livePlayers) }
    }

    private fun withRig(name: String, body: (Rig) -> Unit) {
        check(context.packageName.endsWith(".qa")) { "Media3CrossfadeTest requires the .qa target" }
        assumeTrue("test corpus missing: $CORPUS", File(CORPUS, "gapless_flac_1.flac").isFile)
        val rig = Rig()
        var result = "FAIL"
        try { body(rig); result = "PASS" } finally {
            try { rig.close() } finally {
                val line = "$name result=$result leadMs=${rig.engine.outgoingLeadMsForTest} audibleStartOffsetMs=${rig.audibleOffsetMs} peakAfterSnap=${rig.snapPeak} ${rig.details}"
                android.util.Log.i("Media3CrossfadeTest", line)
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("T059", line) })
            }
        }
    }

    private data class Window(val generation: Int, val frame: Long, val gain: Double, val peak: Double)
    /** Five-ms RMS windows (five full sine periods) preserve the S1 PCM envelope measurement.
     * Kept under one lock because renderer threads write while the test thread reads.
     */
    private class Tap : TeeAudioProcessor.AudioBufferSink {
        private val rows = mutableListOf<Window>()
        private val flushTimes = mutableMapOf<Int, Long>()
        private var gen = 0
        private var channels = 0
        private var encoding = 0
        private var frame = 0L
        private var n = 0
        private var energy = 0.0
        private var peak = 0.0
        @Synchronized override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            assertTrue("tee sample rate", sampleRateHz > 0)
            gen++; flushTimes[gen] = SystemClock.elapsedRealtimeNanos()
            channels = channelCount; this.encoding = encoding
            frame = 0L; n = 0; energy = 0.0; peak = 0.0
        }
        @Synchronized override fun handleBuffer(buffer: ByteBuffer) {
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            while (b.hasRemaining()) {
                var first = 0.0
                repeat(channels) { channel ->
                    val sample = if (encoding == C.ENCODING_PCM_FLOAT) b.float.toDouble() else b.short / 32768.0
                    if (channel == 0) first = sample
                }
                energy += first * first; peak = maxOf(peak, abs(first)); n++; frame++
                if (n == WINDOW) {
                    rows += Window(gen, frame - WINDOW, sqrt(energy / WINDOW) / (AMP / sqrt(2.0)), peak)
                    energy = 0.0; n = 0; peak = 0.0
                }
            }
        }
        @Synchronized fun generation() = gen
        @Synchronized fun flushedAt(generation: Int): Long = flushTimes[generation] ?: Long.MIN_VALUE
        @Synchronized fun windows(): List<Window> = rows.toList()
    }

    private data class Curve(val startFrame: Double, val rows: List<Window>, val maxError: Double) {
        fun gainAt(progress: Double): Double {
            val frame = startFrame + progress * FADE_MS * RATE / 1000.0
            return rows.minBy { abs(it.frame + WINDOW / 2.0 - frame) }.gain
        }
    }
    private fun fitCurve(tap: Tap, incoming: Boolean): Curve {
        val all = tap.windows()
        val generation = all.maxOf { it.generation }
        val rows = all.filter { it.generation == generation }
        val middle = rows.filter { it.gain in 0.15..0.95 }
        assertTrue("not enough curve samples", middle.size >= 20)
        val durationFrames = FADE_MS * RATE / 1000.0
        val start = middle.map {
            val phase = if (incoming) asin(it.gain.coerceIn(0.0, 1.0)) else acos(it.gain.coerceIn(0.0, 1.0))
            it.frame + WINDOW / 2.0 - phase / (PI / 2) * durationFrames
        }.average()
        val error = middle.maxOf {
            val progress = ((it.frame + WINDOW / 2.0 - start) / durationFrames).coerceIn(0.0, 1.0)
            abs(it.gain - if (incoming) sin(progress * PI / 2) else cos(progress * PI / 2))
        }
        return Curve(start, rows, error)
    }

    private data class Clock(val time: Long, val outgoing: Long, val incoming: Long)
    private fun crossingTime(clocks: List<Clock>, targetMs: Double, incoming: Boolean): Double {
        // Positive positions anchor the audible clock; extrapolate to the fitted start sample.
        val anchors = clocks.filter { (if (incoming) it.incoming else it.outgoing) > maxOf(1.0, targetMs) }
        assertTrue("no audible clock anchors for $targetMs", anchors.isNotEmpty())
        return anchors.take(20).map { it.time - ((if (incoming) it.incoming else it.outgoing) - targetMs) }.average()
    }

    /** Every window of every stream flushed after [snapAtNanos] (at least 20 windows) is at full gain. */
    private fun assertUnfadedAfterFlush(r: Rig, tap: Tap, snapAtNanos: Long) {
        var rows = emptyList<Window>()
        await {
            rows = tap.windows().filter { tap.flushedAt(it.generation) > snapAtNanos }
            rows.size >= 20
        }
        assertAllFull(r, rows)
    }
    private fun assertAllFull(r: Rig, rows: List<Window>) {
        assertTrue(rows.isNotEmpty())
        r.snapPeak = rows.minOf { it.peak }
        for (row in rows) assertTrue("old faded gain after flush: peak=${row.peak} generation=${row.generation} frame=${row.frame}",
            20 * log10(row.peak / AMP) >= -0.5)
    }

    private fun await(timeoutMs: Long = 15_000L, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (predicate()) return
            SystemClock.sleep(10L)
        }
        throw AssertionError("condition timed out after ${timeoutMs}ms")
    }
    private fun item(file: File, id: String) = PlaybackItem(id, id, "test", file.absolutePath)
    private fun writeTone(file: File, seconds: Int, rightHz: Int = 1000): File {
        val frames = RATE * seconds
        val b = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + frames * 4); b.put("WAVEfmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(2); b.putInt(RATE); b.putInt(RATE * 4)
        b.putShort(4); b.putShort(16); b.put("data".toByteArray()); b.putInt(frames * 4)
        repeat(frames) { frame ->
            val sample = (sin(2 * PI * 1000 * frame / RATE) * AMP * 32767).roundToInt().toShort()
            b.putShort(sample)
            b.putShort((sin(2 * PI * rightHz * frame / RATE) * AMP * 32767).roundToInt().toShort())
        }
        file.writeBytes(b.array()); return file
    }
    private companion object {
        const val CORPUS = "/sdcard/Music/SistrumTestCorpus"
        const val RATE = 48_000
        const val WINDOW = 240
        const val AMP = 0.25
        const val FADE_MS = 2_000L
    }
}
