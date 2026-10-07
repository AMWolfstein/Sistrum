package me.misa198.airmedy.player.media3

import android.media.audiofx.DynamicsProcessing
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import me.misa198.airmedy.ui.screens.limiterNoteRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test (T048c) that imitates another app's equalizer/effect taking over, disabling and
 * releasing the audio session shared with our Media3 engine, and asserts the published
 * [Media3PlayerFactory.limiterState] and the settings note follow the limiter state machine, while
 * playback keeps advancing in every phase.
 *
 * The competitor is a real [DynamicsProcessing] created with priority 1000 on the engine's session:
 * creating it takes control of the shared effect instance (priority 0 loses control), disabling it
 * disables the shared instance, and releasing it frees control so our probe can take it back.
 *
 * Instrumentation arguments: `audible=true` keeps normal gain (default is muted via
 * [Media3Engine.setFocusGain]); `phaseSeconds` (default 3) sets how long each phase is held.
 */
@RunWith(AndroidJUnit4::class)
class Media3EqAppImitationTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sendStatus(key: String, value: String) =
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(key, value) },
        )

    @Test(timeout = 300_000)
    fun competingEffectTakesDisablesAndReleases() {
        val arguments = InstrumentationRegistry.getArguments()
        val audible = arguments.getString("audible") == "true"
        val phaseSeconds = arguments.getString("phaseSeconds")?.toIntOrNull() ?: 3

        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        var competitor: DynamicsProcessing? = null
        try {
            val wav = writeStereoWav(
                File(context().cacheDir, "eq_imitation_${System.nanoTime()}.wav"),
                60.0 + 4.0 * phaseSeconds,
            )
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(if (audible) 1f else 0f)
            assertTrue("output should start", awaitOutputStarted(engine, 10_000L))

            // Phase 0: baseline — our limiter owns the shared session.
            logPhase(0, "baseline", factory.limiterState.value)
            val t0 = awaitState(factory, available = true, controlled = true, timeoutMs = 10_000L, phase = "phase0")
            assertNull("phase0 note should be null", limiterNoteRes(LimiterStatus.state.value))
            assertPlaybackAdvances(engine, "phase0")
            hold(engine, phaseSeconds, "phase0")

            // Phase 1: a higher-priority effect on the same session takes control.
            logPhase(1, "take-control", factory.limiterState.value)
            competitor = createCompetitor(engine.audioSessionIdForTest)
            competitor.enabled = true
            val t1 = awaitState(factory, available = true, controlled = false, timeoutMs = 5_000L, phase = "phase1")
            assertEquals(
                R.string.playback_limiter_not_controlled_note,
                limiterNoteRes(LimiterStatus.state.value),
            )
            assertPlaybackAdvances(engine, "phase1")
            hold(engine, phaseSeconds, "phase1")

            // Phase 2: the other app disables the effect; we release, probe, but must not take control.
            logPhase(2, "competitor-disables", factory.limiterState.value)
            competitor.enabled = false
            val t2 = awaitState(factory, available = false, controlled = false, timeoutMs = 5_000L, phase = "phase2")
            assertEquals(
                R.string.playback_limiter_unavailable_note,
                limiterNoteRes(LimiterStatus.state.value),
            )
            hold(engine, maxOf(phaseSeconds, 7), "phase2")
            assertEquals(
                "phase2 state should remain (false, false) while the competitor holds priority",
                LimiterState(available = false, controlled = false),
                factory.limiterState.value,
            )
            assertPlaybackAdvances(engine, "phase2")

            // Phase 3: the other app releases; our probe takes control back.
            logPhase(3, "competitor-releases", factory.limiterState.value)
            competitor.release()
            competitor = null
            val t3 = awaitState(factory, available = true, controlled = true, timeoutMs = 12_000L, phase = "phase3")
            assertNull("phase3 note should be null", limiterNoteRes(LimiterStatus.state.value))
            assertPlaybackAdvances(engine, "phase3")
            hold(engine, phaseSeconds, "phase3")

            val summary = "eq_imitation: p0=(true,true)@${t0}ms p1=(true,false)@${t1}ms " +
                "p2=(false,false)@${t2}ms p3=(true,true)@${t3}ms " +
                "final=${factory.limiterState.value}"
            Log.i(TAG, summary)
            sendStatus("eq_imitation", summary)
        } finally {
            try {
                competitor?.release()
            } catch (t: Throwable) {
                Log.i(TAG, "competitor release failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            try {
                engine.close()
            } catch (t: Throwable) {
                Log.i(TAG, "engine close failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    /** Creates a neutral higher-priority DynamicsProcessing competitor on [sessionId]. */
    private fun createCompetitor(sessionId: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            false, 0,
            false, 0,
            false, 0,
            true,
        ).setPreferredFrameDuration(10f).build()
        config.setInputGainAllChannelsTo(0f)
        config.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true,
                true,
                0,
                1f,
                60f,
                1f,
                0f,
                0f,
            )
        )
        return DynamicsProcessing(1000, sessionId, config)
    }

    private fun logPhase(n: Int, name: String, state: LimiterState?) {
        val line = "PHASE $n $name state=$state"
        Log.i(TAG, line)
        sendStatus("PHASE $n $name", line)
    }

    /** Polls [Media3PlayerFactory.limiterState] every 50 ms and returns the transition time in ms. */
    private fun awaitState(
        factory: Media3PlayerFactory,
        available: Boolean,
        controlled: Boolean,
        timeoutMs: Long,
        phase: String,
    ): Long {
        val start = SystemClock.elapsedRealtime()
        val deadline = start + timeoutMs
        var last: LimiterState? = factory.limiterState.value
        while (SystemClock.elapsedRealtime() < deadline) {
            last = factory.limiterState.value
            if (last != null && last.available == available && last.controlled == controlled) {
                return SystemClock.elapsedRealtime() - start
            }
            SystemClock.sleep(50L)
        }
        throw AssertionError(
            "$phase: limiterState never reached (available=$available, controlled=$controlled) " +
                "within $timeoutMs ms; last=$last",
        )
    }

    /** Asserts playback advances by >= 1500 ms over a 2 s window with no [EngineEvent.Error]. */
    private fun assertPlaybackAdvances(engine: Media3Engine, phase: String) {
        drainEventsFailOnError(engine, phase)
        val start = engine.positionMs()
        var end = start
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            drainEventsFailOnError(engine, phase)
            end = engine.positionMs()
            SystemClock.sleep(50L)
        }
        assertTrue(
            "$phase: playback advanced only ${end - start} ms over 2 s (start=$start, end=$end)",
            end - start >= 1_500L,
        )
    }

    /** Sleeps [seconds] while draining events and failing on any [EngineEvent.Error]. */
    private fun hold(engine: Media3Engine, seconds: Int, phase: String) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            drainEventsFailOnError(engine, phase)
            SystemClock.sleep(100L)
        }
    }

    private fun drainEventsFailOnError(engine: Media3Engine, phase: String) {
        engine.pollEvents().filterIsInstance<EngineEvent.Error>().firstOrNull()?.let { error ->
            throw AssertionError("$phase: engine error: ${error.cause.message ?: error.cause}")
        }
    }

    private fun collectUntil(
        engine: Media3Engine,
        timeoutMs: Long,
        done: (List<EngineEvent>) -> Boolean,
    ): List<EngineEvent> {
        val out = mutableListOf<EngineEvent>()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            out += engine.pollEvents()
            if (done(out)) return out
            SystemClock.sleep(20)
        }
        return out
    }

    private fun awaitOutputStarted(engine: Media3Engine, timeoutMs: Long): Boolean =
        collectUntil(engine, timeoutMs) { events -> events.any { it is EngineEvent.OutputStarted } }
            .any { it is EngineEvent.OutputStarted }

    private fun item(path: String) = PlaybackItem(
        trackId = path,
        title = path,
        artist = "test",
        audioPath = path,
    )

    /** Stereo 48 kHz 16-bit WAV of 440 Hz + 660 Hz at -14 dBFS each with a 20 ms fade-in. */
    private fun writeStereoWav(file: File, seconds: Double): File {
        val sampleRate = 48_000
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = (sampleRate * seconds).roundToLong()
        val dataSize = (numFrames * blockAlign).toInt()
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        val amplitude = 10.0.pow(-14.0 / 20.0)
        val fadeFrames = (sampleRate * 0.020).roundToInt()
        for (i in 0 until numFrames) {
            val fade = if (i < fadeFrames) i.toDouble() / fadeFrames.toDouble() else 1.0
            val value = (
                sin(2.0 * PI * 440.0 * i / sampleRate) +
                    sin(2.0 * PI * 660.0 * i / sampleRate)
                ) * amplitude * fade
            val sample = (value * 32767.0).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        file.outputStream().use { it.write(buffer.array()) }
        return file
    }

    private companion object {
        const val TAG = "Media3EqAppImitationTest"
    }
}
