package me.misa198.airmedy.player.media3

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented gapless-preload tests for [Media3Engine] (T021). Each test builds one engine on the
 * shared playback looper, mutes it via [Media3Engine.setFocusGain] (the tee captures before the
 * track volume), wires a [TeeAudioProcessor] around a [GapRecorder] so the output can be measured,
 * and closes the engine in `finally`, asserting no player is leaked.
 */
@RunWith(AndroidJUnit4::class)
class Media3EngineGaplessTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000)
    fun continuousSineSplitAdvancesGaplesslyWithinTenMs() {
        val recorder = GapRecorder()
        val factory = Media3PlayerFactory(context()) { arrayOf<AudioProcessor>(TeeAudioProcessor(recorder)) }
        val engine = Media3Engine(factory)
        val a = writeSineWav(File(context().cacheDir, "gapless_a.wav"), seconds = 2.0, startFrame = 0L)
        val b = writeSineWav(
            File(context().cacheDir, "gapless_b.wav"),
            seconds = 1.5,
            startFrame = (2.0 * SAMPLE_RATE).toLong(),
        )
        try {
            runBlocking { engine.prepare(item(a.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(b.absolutePath), ItemGain.Unity) }
            assertTrue("B should be preloaded", engine.hasPreloaded())

            val events = collectUntil(engine, 15_000L) { list -> list.any { it is EngineEvent.Ended } }
            val advances = events.filterIsInstance<EngineEvent.GaplessAdvanced>()
            val ended = events.filterIsInstance<EngineEvent.Ended>()
            assertEquals("exactly one GaplessAdvanced", 1, advances.size)
            assertEquals("exactly one Ended", 1, ended.size)
            assertEquals("advance should carry B's item", b.absolutePath, advances.single().incoming.trackId)

            val sampleRate = recorder.sampleRate
            assertTrue("tee should report a sample rate", sampleRate > 0)
            assertTrue("tee should report channels", recorder.channelCount > 0)
            val expectedDurationMs = (2.0 + 1.5) * 1000.0
            val actualDurationMs = recorder.totalFrames * 1000.0 / sampleRate
            assertTrue(
                "captured ${actualDurationMs}ms, expected ${expectedDurationMs}ms",
                abs(actualDurationMs - expectedDurationMs) <= 10.0,
            )

            val samples = recorder.samples()
            val silentThresholdFrames = (0.010 * sampleRate).roundToInt()
            val silentRun = longestNearSilentFrames(samples, recorder.channelCount, 0.001f)
            assertTrue(
                "near-silent run ${silentRun} frames exceeds ${silentThresholdFrames} frames (10 ms)",
                silentRun <= silentThresholdFrames,
            )

            val maxStep = AMPLITUDE * 2.0 * PI * FREQ_HZ / sampleRate
            val boundaryFrame = (2.0 * sampleRate).roundToInt()
            val window = (0.005 * sampleRate).roundToInt()
            val jump = maxSampleToSampleJump(samples, recorder.channelCount, boundaryFrame - window, boundaryFrame + window)
            assertTrue(
                "max sample-to-sample jump ${jump} at boundary exceeds 2x per-sample step ${2.0 * maxStep}",
                jump < 2.0 * maxStep,
            )
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun corpusFlacAdvancesGaplessly() {
        assertCorpusGapless("gapless_flac_1.flac", "gapless_flac_2.flac", silentFramesLimit = 480)
    }

    @Test(timeout = 120_000)
    fun corpusMp3AdvancesGaplessly() {
        assertCorpusGapless("gapless_mp3_1.mp3", "gapless_mp3_2.mp3", silentFramesLimit = 1152)
    }

    @Test(timeout = 120_000)
    fun threeShortItemsProduceExactlyOrderedAdvancesThenEnded() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val x = writeSineWav(File(context().cacheDir, "short_x.wav"), seconds = 0.3)
        val y = writeSineWav(File(context().cacheDir, "short_y.wav"), seconds = 0.3)
        val z = writeSineWav(File(context().cacheDir, "short_z.wav"), seconds = 0.3)
        try {
            runBlocking { engine.prepare(item(x.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(y.absolutePath), ItemGain.Unity) }

            val events = mutableListOf<EngineEvent>()
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                for (event in engine.pollEvents()) {
                    events += event
                    if (event is EngineEvent.GaplessAdvanced && event.incoming.trackId == y.absolutePath) {
                        runBlocking { engine.preloadNext(item(z.absolutePath), ItemGain.Unity) }
                    }
                }
                if (events.any { it is EngineEvent.Ended }) break
                SystemClock.sleep(20)
            }

            val transitions = events.filter { it is EngineEvent.GaplessAdvanced || it is EngineEvent.Ended }
            assertEquals(
                "expected [GaplessAdvanced(Y), GaplessAdvanced(Z), Ended] but was $transitions",
                listOf(
                    EngineEvent.GaplessAdvanced::class.java,
                    EngineEvent.GaplessAdvanced::class.java,
                    EngineEvent.Ended::class.java,
                ),
                transitions.map { it::class.java },
            )
            val advances = events.filterIsInstance<EngineEvent.GaplessAdvanced>()
            assertEquals(listOf(y.absolutePath, z.absolutePath), advances.map { it.incoming.trackId })
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun clearPreloadedEndsWithoutAdvancing() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val a = writeSineWav(File(context().cacheDir, "clear_a.wav"), seconds = 0.5)
        val b = writeSineWav(File(context().cacheDir, "clear_b.wav"), seconds = 1.0)
        try {
            runBlocking { engine.prepare(item(a.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(b.absolutePath), ItemGain.Unity) }
            assertTrue("B should be preloaded", engine.hasPreloaded())
            engine.clearPreloaded()
            assertFalse("clearPreloaded should retire the preloaded slot", engine.hasPreloaded())

            val events = collectUntil(engine, 8_000L) { list -> list.any { it is EngineEvent.Ended } }
            assertEquals("no GaplessAdvanced expected after clearPreloaded", 0, events.count { it is EngineEvent.GaplessAdvanced })
            assertTrue("Ended expected", events.any { it is EngineEvent.Ended })
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun positionAndDurationReferToNewItemAfterAdvance() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val a = writeSineWav(File(context().cacheDir, "pos_a.wav"), seconds = 0.5)
        val b = writeSineWav(File(context().cacheDir, "pos_b.wav"), seconds = 2.0)
        try {
            runBlocking { engine.prepare(item(a.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(b.absolutePath), ItemGain.Unity) }

            val advance = awaitFirstAdvance(engine, 8_000L)
            assertTrue("expected a GaplessAdvanced", advance != null)

            val position = engine.positionMs()
            assertTrue("position after advance should be < 500 ms, was $position", position < 500L)
            val duration = engine.durationMs()
            assertTrue("duration should be ~2000 ms, was $duration", abs(duration - 2_000L) <= 50L)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun preloadOfMissingFileThrowsAndKeepsPlaying() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val a = writeSineWav(File(context().cacheDir, "miss_a.wav"), seconds = 3.0)
        val missing = File(context().cacheDir, "missing_preload.wav")
        try {
            runBlocking { engine.prepare(item(a.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("output should start", awaitOutputStarted(engine, 3_000L))

            val thrown = try {
                runBlocking { engine.preloadNext(item(missing.absolutePath), ItemGain.Unity) }
                null
            } catch (t: Throwable) {
                t
            }
            assertTrue("preloadNext should throw for a missing file", thrown != null)
            assertFalse("preloadNext should leave nothing preloaded", engine.hasPreloaded())

            val before = engine.positionMs()
            SystemClock.sleep(300)
            assertTrue("playback should continue after a failed preload", engine.positionMs() > before)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    private fun assertCorpusGapless(nameA: String, nameB: String, silentFramesLimit: Int) {
        val recorder = GapRecorder()
        val factory = Media3PlayerFactory(context()) { arrayOf<AudioProcessor>(TeeAudioProcessor(recorder)) }
        val engine = Media3Engine(factory)
        val a = corpusFile(nameA)
        val b = corpusFile(nameB)
        try {
            runBlocking { engine.prepare(item(a.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
            val duration = engine.durationMs()
            assertTrue("corpus A duration should be known", duration > 3_000L)
            engine.seekTo(duration - 3_000L)
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(b.absolutePath), ItemGain.Unity) }
            engine.play()

            val events = collectUntil(engine, 30_000L) { list -> list.any { it is EngineEvent.Ended } }
            val advances = events.filterIsInstance<EngineEvent.GaplessAdvanced>()
            assertEquals("exactly one GaplessAdvanced for $nameA -> $nameB", 1, advances.size)
            assertEquals("advance should carry B", b.absolutePath, advances.single().incoming.trackId)

            val sampleRate = recorder.sampleRate
            assertTrue("tee should report a sample rate", sampleRate > 0)
            assertTrue("tee should report channels", recorder.channelCount > 0)
            val samples = recorder.samples()
            val boundaryFrame = (3.0 * sampleRate).roundToInt()
            val window = (1.0 * sampleRate).roundToInt()
            val silentRun = longestNearSilentFrames(samples, recorder.channelCount, 0.001f, boundaryFrame - window, boundaryFrame + window)
            Log.i(
                "Media3EngineGaplessTest",
                "$nameA -> $nameB: silentRun=${silentRun} frames (limit $silentFramesLimit), sampleRate=$sampleRate",
            )
            assertTrue(
                "$nameA -> $nameB near-silent run ${silentRun} frames exceeds ${silentFramesLimit} frames",
                silentRun <= silentFramesLimit,
            )
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    private fun item(path: String) = PlaybackItem(
        trackId = path,
        title = path,
        artist = "test",
        audioPath = path,
    )

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

    private fun awaitFirstAdvance(engine: Media3Engine, timeoutMs: Long): EngineEvent.GaplessAdvanced? =
        collectUntil(engine, timeoutMs) { events -> events.any { it is EngineEvent.GaplessAdvanced } }
            .filterIsInstance<EngineEvent.GaplessAdvanced>()
            .firstOrNull()

    private fun corpusFile(name: String): File {
        val file = File(CORPUS_DIR, name)
        if (!file.isFile) {
            throw AssertionError("missing test corpus file: ${file.absolutePath}")
        }
        return file
    }

    /** Writes a 48 kHz stereo 16-bit WAV holding a 440 Hz sine segment starting at [startFrame]. */
    private fun writeSineWav(file: File, seconds: Double, startFrame: Long = 0L): File {
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = CHANNELS * bytesPerSample
        val numFrames = (SAMPLE_RATE * seconds).roundToLong()
        val dataSize = (numFrames * blockAlign).toInt()
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (i in 0 until numFrames) {
            val globalFrame = startFrame + i
            val value = (sin(2.0 * PI * FREQ_HZ * globalFrame / SAMPLE_RATE) * AMPLITUDE * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(value)
            buffer.putShort(value)
        }
        file.outputStream().use { it.write(buffer.array()) }
        return file
    }

    /** Longest run of consecutive frames whose every channel sample is below [threshold]. */
    private fun longestNearSilentFrames(
        samples: FloatArray,
        channels: Int,
        threshold: Float,
        fromFrame: Int = 0,
        toFrame: Int = samples.size / channels,
    ): Int {
        val start = maxOf(0, fromFrame)
        val end = minOf(samples.size / channels, toFrame)
        var longest = 0
        var run = 0
        for (frame in start until end) {
            var silent = true
            for (c in 0 until channels) {
                if (abs(samples[frame * channels + c]) >= threshold) {
                    silent = false
                    break
                }
            }
            if (silent) run++ else {
                if (run > longest) longest = run
                run = 0
            }
        }
        if (run > longest) longest = run
        return longest
    }

    /** Largest jump between consecutive samples of the same channel over [fromFrame, toFrame]. */
    private fun maxSampleToSampleJump(samples: FloatArray, channels: Int, fromFrame: Int, toFrame: Int): Float {
        val start = maxOf(0, fromFrame)
        val end = minOf(samples.size / channels - 1, toFrame)
        var maxJump = 0f
        for (frame in start until end) {
            for (c in 0 until channels) {
                val jump = abs(samples[(frame + 1) * channels + c] - samples[frame * channels + c])
                if (jump > maxJump) maxJump = jump
            }
        }
        return maxJump
    }

    /**
     * Records every buffer the [TeeAudioProcessor] receives (before the track volume), so the test
     * can measure frame counts and sample values across a gapless boundary.
     */
    private class GapRecorder : TeeAudioProcessor.AudioBufferSink {
        private val data = ByteArrayOutputStream()

        var sampleRate = 0
            private set
        var channelCount = 0
            private set
        var encoding = C.ENCODING_INVALID
            private set

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            this.sampleRate = sampleRateHz
            this.channelCount = channelCount
            this.encoding = encoding
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val dup = buffer.duplicate()
            val bytes = ByteArray(dup.remaining())
            dup.get(bytes)
            data.write(bytes)
        }

        val totalFrames: Long
            get() {
                val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
                if (sampleRate <= 0 || channelCount <= 0) return 0L
                return data.size().toLong() / (bytesPerSample * channelCount)
            }

        /** Interleaved samples normalized to [-1, 1]. */
        fun samples(): FloatArray {
            val raw = data.toByteArray()
            val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val count = raw.size / bytesPerSample
            val out = FloatArray(count)
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            if (encoding == C.ENCODING_PCM_FLOAT) {
                for (i in 0 until count) out[i] = buffer.float
            } else {
                for (i in 0 until count) out[i] = buffer.short.toFloat() / 32768f
            }
            return out
        }
    }

    private companion object {
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val AMPLITUDE = 0.5
        const val FREQ_HZ = 440.0
    }
}
