package com.amwolfstein.sistrum.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.analysis.SilenceProfile
import com.amwolfstein.sistrum.player.engine.EngineEvent
import com.amwolfstein.sistrum.player.engine.ItemGain
import com.amwolfstein.sistrum.player.media3.Media3Engine
import com.amwolfstein.sistrum.player.media3.Media3PlayerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** T020 / SC-009. Generated corpus has exactly known silence edges, not edges inferred from
 * the policy under test. Fit 5ms PCM gain windows and anchor them to the players' audible
 * clocks on elapsedRealtime, as in 001 Media3CrossfadeTest.
 * Captures are before track volume/session effects, so focus mute cannot hide a PCM failure.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SilenceCrossfadeDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 30_000)
    fun retainedLeadCannotStartMeasuredTailBeforeCurrentProcessedClock() {
        check(context.packageName.endsWith(".qa"))
        val factory = Media3PlayerFactory(context)
        val engine = Media3Engine(factory)
        val file = File(context.cacheDir, "t021-clock-${System.nanoTime()}.wav")
        try {
            writeTone(file, 0L, 8_000L)
            val item = PlaybackItem("clock", "clock", "test", file.absolutePath,
                silence = SilenceProfile(0L, 2_000L))
            engine.setCrossfade(2_000L, true)
            engine.setFocusGain(0f)
            runBlocking { engine.prepare(item, ItemGain.Unity, 0L, startPaused = true) }
            await { (engine.gainProcessingPositionForTest(false)?.frames ?: 0L) > 0L }
            val clock = requireNotNull(engine.gainProcessingPositionForTest(false))
            assertTrue("paused preroll must precede the 6s fade boundary",
                clock.frames * 1_000L / clock.sampleRate < 6_000L)
            engine.setCrossfadeSkipSilence(false)
            assertEquals("disabled policy keeps the supplied 001 clock", 2_000L,
                engine.automaticFadeDurationMs(item, 8_000L, TOTAL_MS, false, 2))
            engine.setCrossfadeSkipSilence(true)
            assertEquals("repeat-one keeps the supplied 001 clock", 2_000L,
                engine.automaticFadeDurationMs(item, 8_000L, TOTAL_MS, true, 2))
            assertEquals("no-tail policy keeps the supplied 001 clock", 2_000L,
                engine.automaticFadeDurationMs(item.copy(silence = SilenceProfile(0L, 0L)),
                    8_000L, TOTAL_MS, false, 2))
            assertNull("retained lead must not start a measured-tail fade before processing reaches it",
                engine.automaticFadeDurationMs(item, 6_000L, TOTAL_MS, false, 2))
        } finally {
            engine.close()
            assertEquals(0, factory.livePlayers)
            assertTrue(!file.exists() || file.delete())
        }
    }

    @Test(timeout = 120_000)
    fun twoAndThreeSecondTailsOverlapAudibleAudioAndSkipIncomingLead() {
        for (tailMs in listOf(2_000L, 3_000L)) {
            val measured = measure(tailMs, leadMs = 2_000L, profiles = true, skip = true)
            val expectedEnd = TOTAL_MS - tailMs
            assertEquals("fade must end at last audible outgoing frame", expectedEnd.toDouble(),
                measured.outgoingEndMs, 10.0)
            assertTrue("incoming leading silence was rendered into the fade: ${measured.incomingFirstAudibleMs}ms",
                measured.incomingFirstAudibleMs <= 100.0)
            assertCommonClockOverlap(measured)
            report("tail=$tailMs lead=2000 $measured")
        }
    }

    @Test(timeout = 120_000)
    fun noProfileAndNoSilenceKeep001PcmTimingWithinTenMs() {
        // Normalize the fitted start to a 2s fade using its event duration: this removes only the
        // coordinator polling overshoot (001 clamps its fade to the remaining processed frames).
        // Compare both policies to 001's independent source-end target, then to each other.
        val legacy = measure(0L, 0L, profiles = false, skip = false)
        for (profiles in listOf(false, true)) {
            val current = measure(0L, 0L, profiles = profiles, skip = true)
            assertEquals("001 fitted fade end", TOTAL_MS.toDouble(), legacy.outgoingEndMs, 10.0)
            assertEquals("SC-009 source-end timing", TOTAL_MS.toDouble(), current.outgoingEndMs, 10.0)
            assertEquals("SC-009 fitted starts at common 2s duration", legacy.normalizedStartMs, current.normalizedStartMs, 10.0)
            assertEquals("full nonsilent overlap", 0, current.silentOverlapMs)
            report("noSilence profiles=$profiles legacy=$legacy current=$current")
        }
    }

    @Test(timeout = 90_000)
    fun disabledSilenceSkippingRetainsLeadingAndTrailingSilence() {
        val measured = measure(2_000L, 2_000L, profiles = true, skip = false)
        // A's full 2s tail and B's full 2s lead cover the legacy overlap. Measuring that silence
        // is also a negative control: the SC-009 metric must reject the unmodified 001 behaviour.
        assertTrue("disabled option unexpectedly skipped silence", measured.silentOverlapMs >= 1_800)
        assertTrue("disabled option unexpectedly skipped incoming lead", measured.incomingFirstAudibleMs >= 1_900.0)
        report("skip=false $measured")
    }

    @Test(timeout = 60_000)
    fun delayedIncomingPlaybackFailsTheCommonClockOverlapOracle() {
        val delayed = measure(2_000L, 2_000L, profiles = true, skip = true, delayIncomingMs = 3_000L)
        assertTrue("actual delayed B was not detected: $delayed", delayed.incomingOffsetMs > 100.0)
        assertThrows(AssertionError::class.java) { assertCommonClockOverlap(delayed) }
    }

    @Test(timeout = 90_000)
    fun shortAndEntirelySilentIncomingTracksCannotEndDuringTheOutgoingFade() {
        check(context.packageName.endsWith(".qa"))
        for (leadMs in listOf(9_000L, TOTAL_MS)) {
            val taps = mutableListOf<Tap>()
            val factory = Media3PlayerFactory(context, afterGainProcessors = {
                arrayOf<AudioProcessor>(TeeAudioProcessor(Tap().also { taps += it }))
            })
            val engine = Media3Engine(factory)
            val aFile = File(context.cacheDir, "t021-fix-a-${System.nanoTime()}.wav")
            val bFile = File(context.cacheDir, "t021-fix-b-${System.nanoTime()}.wav")
            val events = mutableListOf<EngineEvent>()
            try {
                writeTone(aFile, 0L, TOTAL_MS)
                writeTone(bFile, leadMs, TOTAL_MS)
                val a = PlaybackItem("a", "outgoing", "test", aFile.absolutePath, silence = SilenceProfile(0L, 0L))
                val b = PlaybackItem("b", "incoming", "test", bFile.absolutePath, silence = SilenceProfile(leadMs, if (leadMs == TOTAL_MS) TOTAL_MS else 0L))
                engine.setCrossfade(4_000L, true)
                engine.setFocusGain(0f)
                runBlocking { engine.prepare(a, ItemGain.Unity, ORIGIN_MS, false) }
                runBlocking { engine.preloadNext(b, ItemGain.Unity) }
                await {
                    events += engine.pollEvents()
                    assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
                    factory.livePlayers == 2 && factory.call {
                        engine.playersForTest().all { it.playbackState == Player.STATE_READY }
                    }
                }
                await {
                    val duration = engine.automaticFadeDurationMs(a, engine.positionMs() + engine.fadeLeadMs(), TOTAL_MS, false, 4)
                    duration != null && engine.hasPreloaded() && engine.beginCrossfade(duration)
                }
                val incomingStart = engine.positionMs()
                await {
                    events += engine.pollEvents()
                    assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
                    assertTrue("incoming EOF truncated outgoing fade, lead=$leadMs", events.none { it is EngineEvent.Ended })
                    !engine.isCrossfading()
                }
                assertTrue("unsafe lead must restart from zero", incomingStart < 1_000L)
                val fade = events.filterIsInstance<EngineEvent.TransitionStarted>().single()
                val outStart = fitStart(taps[0].rows(), fade.fadeMs, incoming = false)
                assertEquals("outgoing curve reaches the last source frame before EOF promotion",
                    TOTAL_MS.toDouble(), ORIGIN_MS + outStart + fade.fadeMs, 10.0)
                assertEquals("must finish both curves before outgoing release", 1, factory.livePlayers)
                await {
                    events += engine.pollEvents()
                    assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
                    events.any { it is EngineEvent.Ended }
                }
                assertEquals("one real incoming EOF after fade completion", 1, events.count { it is EngineEvent.Ended })
            } finally {
                engine.close()
                assertEquals(0, factory.livePlayers)
                assertTrue(!aFile.exists() || aFile.delete())
                assertTrue(!bFile.exists() || bFile.delete())
            }
        }
    }

    private fun assertCommonClockOverlap(measured: Measurement) {
        assertTrue("incoming audible clock offset=${measured.incomingOffsetMs}ms", abs(measured.incomingOffsetMs) <= 100.0)
        assertTrue("SC-009 overlap silence=${measured.silentOverlapMs}ms", measured.silentOverlapMs <= 100)
    }

    private data class Measurement(
        val outgoingStartMs: Double?,
        val fadeMs: Long,
        val incomingFirstAudibleMs: Double,
        val silentOverlapMs: Int,
        val incomingOffsetMs: Double,
    ) {
        val outgoingEndMs get() = requireNotNull(outgoingStartMs) + fadeMs
        val normalizedStartMs get() = outgoingEndMs - FADE_MS
    }

    private fun measure(tailMs: Long, leadMs: Long, profiles: Boolean, skip: Boolean, delayIncomingMs: Long = 0L): Measurement {
        check(context.packageName.endsWith(".qa")) { "T020 requires the .qa target" }
        val taps = mutableListOf<Tap>()
        val factory = Media3PlayerFactory(context, afterGainProcessors = {
            Tap().also { taps += it }.let { arrayOf<AudioProcessor>(TeeAudioProcessor(it)) }
        })
        val engine = Media3Engine(factory)
        val outgoingFile = File(context.cacheDir, "t020-out-${System.nanoTime()}.wav")
        val incomingFile = File(context.cacheDir, "t020-in-${System.nanoTime()}.wav")
        val events = mutableListOf<EngineEvent>()
        var outgoing: ExoPlayer? = null
        var incoming: ExoPlayer? = null
        val aClocks = mutableListOf<Clock>()
        val bClocks = mutableListOf<Clock>()
        val delayHandler = factory.call { Handler(Looper.myLooper()!!) }
        var delayedResume: Runnable? = null
        fun sampleClocks() = factory.call {
            val live = engine.playersForTest()
            val now = SystemClock.elapsedRealtime()
            outgoing?.takeIf { it in live }?.let { aClocks += Clock(now, it.currentPosition) }
            incoming?.takeIf { it in live }?.let { bClocks += Clock(now, it.currentPosition) }
        }
        try {
            writeTone(outgoingFile, 0L, TOTAL_MS - tailMs)
            writeTone(incomingFile, leadMs, TOTAL_MS)
            val a = PlaybackItem("a", "T020 outgoing", "test", outgoingFile.absolutePath,
                silence = if (profiles) SilenceProfile(0L, tailMs) else null)
            val b = PlaybackItem("b", "T020 incoming", "test", incomingFile.absolutePath,
                silence = if (profiles) SilenceProfile(leadMs, 0L) else null)
            engine.setCrossfade(FADE_MS, true)
            engine.setCrossfadeSkipSilence(skip)
            engine.setFocusGain(0f)
            runBlocking { engine.prepare(a, ItemGain.Unity, ORIGIN_MS, false) }
            runBlocking { engine.preloadNext(b, ItemGain.Unity) }
            outgoing = engine.playersForTest().first()
            await {
                events += engine.pollEvents()
                assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
                engine.hasPreloaded() && factory.livePlayers == 2 && factory.call {
                    engine.playersForTest().all { it.playbackState == Player.STATE_READY }
                }
            }
            var began = false
            await {
                events += engine.pollEvents()
                assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
                val decision = engine.positionMs() + engine.fadeLeadMs()
                // Toggle-OFF reference uses 001's decision functions, independently of T018.
                val duration = if (!skip) {
                    if (shouldStartCrossfade(2, decision, TOTAL_MS, engine.hasPreloaded()))
                        crossfadeDurationMs(2, decision, TOTAL_MS) else null
                } else engine.automaticFadeDurationMs(a, decision, TOTAL_MS, false, 2)
                if (duration != null) {
                    if (delayIncomingMs > 0L) factory.call {
                        val p = engine.playersForTest().last()
                        p.addListener(object : Player.Listener {
                            private var delayed = false
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                if (!isPlaying || delayed || !engine.isCrossfading()) return
                                delayed = true
                                p.pause()
                                delayedResume = Runnable { p.play() }.also {
                                    delayHandler.postDelayed(it, delayIncomingMs)
                                }
                            }
                        })
                    }
                    assertTrue("ready fade refused", engine.beginCrossfade(duration))
                    incoming = engine.playersForTest().first()
                    began = true
                }
                began
            }
            await {
                sampleClocks()
                events += engine.pollEvents()
                assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
                !engine.isCrossfading() && factory.livePlayers == 1
            }
            // Keep sampling until the incoming audible clock advances after its final seek.
            val incomingOrigin = if (skip) leadMs else 0L
            await {
                sampleClocks()
                bClocks.count { it.positionMs > incomingOrigin + 100L } >= 20
            }
            // Capture enough B after completion to include an unskipped 2s lead in the OFF control.
            await { taps[1].rows().any { it.rms > 0.01 } }
            events += engine.pollEvents()
            assertTrue("engine errors: $events", events.none { it is EngineEvent.Error })
            val transition = events.filterIsInstance<EngineEvent.TransitionStarted>().single()
            assertEquals(b, transition.incoming)
            assertEquals("no gapless fallback", 0, events.count { it is EngineEvent.GaplessAdvanced })
            val out = taps[0].rows()
            val inc = taps[1].rows()
            assertTrue("missing outgoing PCM", out.size >= 100)
            assertTrue("missing incoming PCM", inc.size >= 100)
            if (!skip && tailMs > 0L) {
                // A's fade is entirely over zero PCM; its gain cannot be fitted to that silence.
                // B is the independent source-clock anchor: first nonzero window must follow 2s.
                val first = inc.first { it.rms > 0.001 }.ms
                val tail = out.filter { it.ms >= TOTAL_MS - tailMs - ORIGIN_MS }
                assertTrue("missing captured outgoing tail", tail.size >= (tailMs - 100) / WINDOW_MS)
                assertTrue("outgoing fixture tail is not silent", tail.all { it.peak < SILENCE_AMPLITUDE })
                assertEquals("OFF retains the outgoing source end", TOTAL_MS.toDouble(),
                    ORIGIN_MS + out.last().ms + WINDOW_MS / 2.0, 10.0)
                val silence = (0 until transition.fadeMs.toInt() step WINDOW_MS).count { ms ->
                    val row = inc.minBy { abs(it.ms - ms) }
                    row.peak < SILENCE_AMPLITUDE
                } * WINDOW_MS
                return Measurement(null, transition.fadeMs, first, silence, 0.0)
            }
            val outStart = fitStart(out, transition.fadeMs, incoming = false)
            val inStart = fitStart(inc, transition.fadeMs, incoming = true)
            val firstAudible = inc.first { it.rms > 0.001 }.ms - inStart
            // Project the fitted sample positions onto elapsedRealtime via the players' audible
            // clocks (001 timing instrumentation). Never independently shift B onto A's fade.
            val aStartTime = audibleTime(aClocks, ORIGIN_MS + outStart)
            val bStartTime = audibleTime(bClocks, incomingOrigin + inStart)
            val offset = bStartTime - aStartTime
            val silence = (0 until transition.fadeMs.toInt() step WINDOW_MS).count { ms ->
                val aRow = out.minBy { abs(it.ms - (outStart + ms + WINDOW_MS / 2.0)) }
                val bMs = inStart + ms + WINDOW_MS / 2.0 - offset
                val bPeak = if (bMs < 0.0) 0.0 else inc.minBy { abs(it.ms - bMs) }.peak
                aRow.peak < SILENCE_AMPLITUDE && bPeak < SILENCE_AMPLITUDE
            } * WINDOW_MS
            return Measurement(ORIGIN_MS + outStart, transition.fadeMs, firstAudible, silence, offset)
        } finally {
            factory.call { delayedResume?.let { delayHandler.removeCallbacks(it) } }
            engine.close()
            assertEquals("player leak", 0, factory.livePlayers)
            assertTrue("delete outgoing fixture", !outgoingFile.exists() || outgoingFile.delete())
            assertTrue("delete incoming fixture", !incomingFile.exists() || incomingFile.delete())
        }
    }

    private data class Clock(val timeMs: Long, val positionMs: Long)

    private fun audibleTime(clocks: List<Clock>, sourceMs: Double): Double {
        val anchors = clocks.filter { it.positionMs > sourceMs + 100.0 }.take(20)
        assertTrue("missing audible clock anchors for $sourceMs", anchors.isNotEmpty())
        return anchors.map { it.timeMs - (it.positionMs - sourceMs) }.average()
    }

    private fun fitStart(rows: List<Row>, durationMs: Long, incoming: Boolean): Double {
        val middle = rows.filter { it.gain in 0.15..0.95 }
        assertTrue("insufficient audible fade PCM", middle.size >= 20)
        val start = middle.map { row ->
            val phase = if (incoming) asin(row.gain) else acos(row.gain)
            row.ms - phase / (PI / 2) * durationMs
        }.average()
        val error = middle.maxOf { row ->
            val progress = ((row.ms - start) / durationMs).coerceIn(0.0, 1.0)
            abs(row.gain - if (incoming) sin(progress * PI / 2) else cos(progress * PI / 2))
        }
        assertTrue("captured fade curve error=$error", error < 0.015)
        return start
    }

    private data class Row(val generation: Int, val ms: Double, val rms: Double, val peak: Double) {
        val gain get() = rms / (AMP / sqrt(2.0))
    }

    private class Tap : TeeAudioProcessor.AudioBufferSink {
        private val captured = mutableListOf<Row>()
        private var generation = 0
        private var channels = 0
        private var encoding = 0
        private var frame = 0L
        private var count = 0
        private var energy = 0.0
        private var peak = 0.0
        @Synchronized override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            assertEquals(RATE, sampleRateHz)
            assertTrue("supported PCM", encoding == C.ENCODING_PCM_FLOAT || encoding == C.ENCODING_PCM_16BIT)
            assertEquals("stereo fixture", 2, channelCount)
            channels = channelCount
            this.encoding = encoding
            // Tee also flushes at EOS/reset. Preserve completed PCM; rows() selects the last
            // populated generation, as 001 does, excluding B's preroll after its restart.
            generation++; frame = 0; count = 0; energy = 0.0; peak = 0.0
        }
        @Synchronized override fun handleBuffer(buffer: ByteBuffer) {
            val data = buffer.duplicate().order(ByteOrder.nativeOrder())
            while (data.hasRemaining()) {
                var sample = 0.0
                repeat(channels) { channel ->
                    val value = if (encoding == C.ENCODING_PCM_FLOAT) data.float.toDouble() else data.short / 32768.0
                    assertTrue("nonfinite PCM", value.isFinite())
                    if (channel == 0) sample = value
                }
                energy += sample * sample; peak = maxOf(peak, abs(sample)); count++; frame++
                if (count == RATE * WINDOW_MS / 1000) {
                    captured += Row(generation, (frame - count / 2.0) * 1000 / RATE, sqrt(energy / count), peak)
                    count = 0; energy = 0.0; peak = 0.0
                }
            }
        }
        @Synchronized fun rows(): List<Row> {
            val latest = captured.lastOrNull()?.generation ?: return emptyList()
            return captured.filter { it.generation == latest }
        }
    }

    private fun writeTone(file: File, audibleFromMs: Long, audibleToMs: Long) {
        val frames = (TOTAL_MS * RATE / 1000).toInt()
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()); bytes.putInt(36 + frames * 4); bytes.put("WAVEfmt ".toByteArray())
        bytes.putInt(16); bytes.putShort(1); bytes.putShort(2); bytes.putInt(RATE); bytes.putInt(RATE * 4)
        bytes.putShort(4); bytes.putShort(16); bytes.put("data".toByteArray()); bytes.putInt(frames * 4)
        repeat(frames) { frame ->
            val audible = frame >= audibleFromMs * RATE / 1000 && frame < audibleToMs * RATE / 1000
            val sample = if (audible) (sin(2 * PI * 1000 * frame / RATE) * AMP * 32767).roundToInt().toShort() else 0.toShort()
            bytes.putShort(sample); bytes.putShort(sample)
        }
        file.writeBytes(bytes.array())
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(5L)
        }
        throw AssertionError("PCM/transition condition timed out")
    }
    private fun report(line: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("T020-Silence", line) })
    }
    private companion object {
        const val RATE = 48_000
        const val TOTAL_MS = 10_000L
        const val ORIGIN_MS = 3_000L
        const val FADE_MS = 2_000L
        const val WINDOW_MS = 5
        const val AMP = 0.2
        const val SILENCE_AMPLITUDE = 0.001
    }
}
