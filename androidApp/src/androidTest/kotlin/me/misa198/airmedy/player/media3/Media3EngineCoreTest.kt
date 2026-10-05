package me.misa198.airmedy.player.media3

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented single-player core tests for [Media3Engine] (T020). Each test drives its own engine
 * on the shared playback looper, mutes it via [Media3Engine.setFocusGain] so the device stays quiet,
 * and closes it in `finally`, asserting no player is leaked.
 */
@RunWith(AndroidJUnit4::class)
class Media3EngineCoreTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000)
    fun preparePausedReportsZeroPositionAndValidDuration() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val generated = writeSineWav(File(context().cacheDir, "engine_5s.wav"), seconds = 5)
        try {
            val cases: List<Pair<String, Long?>> = listOf(
                generated.absolutePath to 5_000L,
                corpusFile("wav_pcm16_48.wav").absolutePath to null,
                corpusFile("mp3_untagged.mp3").absolutePath to null,
                corpusFile("flac_untagged.flac").absolutePath to null,
            )
            for ((path, expectedDuration) in cases) {
                runBlocking { engine.prepare(item(path), ItemGain.Unity, 0L, startPaused = true) }
                engine.setFocusGain(0f)

                assertEquals("position should be 0 for $path", 0L, engine.positionMs())
                val duration = engine.durationMs()
                assertTrue("duration should be positive for $path", duration > 0L)
                if (expectedDuration != null) {
                    assertTrue(
                        "generated WAV duration should be ~${expectedDuration}ms but was ${duration}ms",
                        abs(duration - expectedDuration) <= 50L,
                    )
                }
                val events = collectEvents(engine, 500L)
                assertTrue("no OutputStarted expected while paused for $path", events.none { it is EngineEvent.OutputStarted })
            }
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun unpausedPrepareEmitsOutputStartedOnceThenAdvancesMonotonically() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_advance.wav"), seconds = 5)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            var started = 0
            val startedDeadline = SystemClock.elapsedRealtime() + 3_000L
            while (SystemClock.elapsedRealtime() < startedDeadline) {
                started += engine.pollEvents().count { it is EngineEvent.OutputStarted }
                if (started > 0) break
                SystemClock.sleep(20)
            }
            assertEquals("OutputStarted should appear exactly once", 1, started)

            SystemClock.sleep(1_000)

            val tFirst = SystemClock.elapsedRealtime()
            val pFirst = engine.positionMs()
            val readings = mutableListOf<Pair<Long, Long>>()
            var prevT = tFirst
            var prevP = pFirst
            repeat(10) {
                SystemClock.sleep(200)
                val t = SystemClock.elapsedRealtime()
                val p = engine.positionMs()
                readings += (t - tFirst) to (p - pFirst)
                assertTrue("position must strictly increase ($prevP -> $p)", p > prevP)
                assertTrue("step too large: ${p - prevP} ms", p - prevP <= 450L)
                assertTrue(
                    "position drifted from wall time (t=${t - prevT}ms, p=${p - prevP}ms)",
                    abs((p - prevP) - (t - prevT)) <= 150L,
                )
                prevT = t
                prevP = p
            }
            Log.i(
                "Media3EngineCoreTest",
                "readings (t, p): ${readings.joinToString(", ") { "(${it.first}, ${it.second})" }}",
            )
            assertTrue(
                "accumulated drift over the window",
                abs((prevP - pFirst) - (prevT - tFirst)) <= 150L,
            )
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun pauseHoldsPositionThenPlayResumesOutput() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_pause.wav"), seconds = 5)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            assertTrue("first OutputStarted expected", awaitOutputStarted(engine, 3_000L))
            SystemClock.sleep(500)
            engine.pause()
            val held = engine.positionMs()
            SystemClock.sleep(600)
            assertTrue("position should hold within ±20ms", abs(engine.positionMs() - held) <= 20L)

            engine.play()
            var started = 0
            val deadline = SystemClock.elapsedRealtime() + 3_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                started += engine.pollEvents().count { it is EngineEvent.OutputStarted }
                if (started > 0) break
                SystemClock.sleep(20)
            }
            assertEquals("OutputStarted should appear exactly once after play", 1, started)

            val beforeAdvance = engine.positionMs()
            SystemClock.sleep(400)
            assertTrue("position should advance after play", engine.positionMs() > beforeAdvance)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun positionAfterPauseIsStableAndSeekBackwardReturnsLowerPosition() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_stable.wav"), seconds = 5)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            assertTrue("output started expected", awaitOutputStarted(engine, 3_000L))
            SystemClock.sleep(1_000)
            engine.pause()
            val first = engine.positionMs()
            SystemClock.sleep(300)
            val second = engine.positionMs()
            assertEquals("position should be stable after pause", first, second)

            engine.seekTo(200L)
            assertTrue("seek backward should return ~200ms", abs(engine.positionMs() - 200L) <= 100L)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun prepareSeekPositionAndManualSeekAreHonored() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_seek.wav"), seconds = 5)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 2_000L, startPaused = true) }
            engine.setFocusGain(0f)

            assertTrue("start position should be ~2000ms", abs(engine.positionMs() - 2_000L) <= 100L)
            engine.seekTo(3_000L)
            assertTrue("seek position should be ~3000ms", abs(engine.positionMs() - 3_000L) <= 100L)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun endedIsEmittedOnceForAShortItem() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_end.wav"), seconds = 1)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            var ended = 0
            val deadline = SystemClock.elapsedRealtime() + 4_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                ended += engine.pollEvents().count { it is EngineEvent.Ended }
                if (ended > 0) break
                SystemClock.sleep(20)
            }
            assertEquals("Ended should appear exactly once", 1, ended)

            val more = collectEvents(engine, 1_000L).count { it is EngineEvent.Ended }
            assertEquals("no further Ended expected", 0, more)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun malformedFilePrepareThrowsWithoutLeakingPlayers() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        try {
            val garbage = writeGarbageFlac(File(context().cacheDir, "garbage.flac"))
            val empty = File(context().cacheDir, "empty.mp3").also { it.writeBytes(ByteArray(0)) }
            for (path in listOf(garbage.absolutePath, empty.absolutePath)) {
                val before = factory.livePlayers
                val thrown = try {
                    runBlocking { engine.prepare(item(path), ItemGain.Unity, 0L, startPaused = true) }
                    null
                } catch (t: Throwable) {
                    t
                }
                assertTrue("prepare should throw for $path", thrown != null)
                assertTrue(
                    "prepare should throw an Exception for $path, not ${thrown?.javaClass?.name}",
                    thrown is Exception,
                )
                assertEquals("livePlayers should be unchanged for $path", before, factory.livePlayers)
            }

            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        } finally {
            engine.close()
        }
    }

    @Test(timeout = 120_000)
    fun truncatedWavEndsOrErrorsWithoutCrashing() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_trunc.wav"), seconds = 1, claimedSeconds = 10)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            var ended = false
            var platformError = false
            val deadline = SystemClock.elapsedRealtime() + 6_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                for (event in engine.pollEvents()) {
                    when (event) {
                        is EngineEvent.Ended -> ended = true
                        is EngineEvent.Error -> if (event.provider == "platform") platformError = true
                        else -> Unit
                    }
                }
                if (ended || platformError) break
                SystemClock.sleep(20)
            }
            assertTrue("expected Ended or a platform Error", ended || platformError)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun closeIsIdempotentAndDisablesTheEngine() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_close.wav"), seconds = 2)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
            engine.setFocusGain(0f)

            engine.close()
            engine.close()

            assertEquals(0L, engine.positionMs())
            assertEquals(0L, engine.durationMs())
            assertTrue(engine.pollEvents().isEmpty())
            engine.play()
            assertTrue(engine.pollEvents().isEmpty())
            assertEquals("live players leaked", 0, factory.livePlayers)
        } finally {
            engine.close()
        }
    }

    @Test(timeout = 120_000)
    fun missingFilePrepareThrowsWithoutLeakingPlayers() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        try {
            val before = factory.livePlayers
            val missing = File(context().cacheDir, "does_not_exist.wav")
            val thrown = try {
                runBlocking { engine.prepare(item(missing.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
                null
            } catch (t: Throwable) {
                t
            }
            assertTrue("prepare should throw for a missing file", thrown != null)
            assertEquals("livePlayers should be unchanged", before, factory.livePlayers)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun prepareAfterCloseThrowsAndCreatesNoPlayer() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_closed.wav"), seconds = 1)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
            engine.setFocusGain(0f)
            engine.close()
            assertEquals(0, factory.livePlayers)

            val thrown = try {
                runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
                null
            } catch (t: Throwable) {
                t
            }
            assertTrue("prepare after close should throw IllegalStateException", thrown is IllegalStateException)
            assertEquals("prepare after close should not create a player", 0, factory.livePlayers)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun positionReadsAreSafeFromMultipleThreads() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writeSineWav(File(context().cacheDir, "engine_conc.wav"), seconds = 3)
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = true) }
            engine.setFocusGain(0f)

            val errors = AtomicInteger(0)
            val threads = List(2) {
                Thread {
                    repeat(100) {
                        try {
                            engine.positionMs()
                        } catch (t: Throwable) {
                            errors.incrementAndGet()
                        }
                    }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            assertEquals("no exceptions expected from concurrent position reads", 0, errors.get())
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

    private fun awaitOutputStarted(engine: Media3Engine, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (engine.pollEvents().any { it is EngineEvent.OutputStarted }) return true
            SystemClock.sleep(20)
        }
        return false
    }

    private fun collectEvents(engine: Media3Engine, durationMs: Long, stepMs: Long = 20L): List<EngineEvent> {
        val out = mutableListOf<EngineEvent>()
        val deadline = SystemClock.elapsedRealtime() + durationMs
        while (SystemClock.elapsedRealtime() < deadline) {
            out += engine.pollEvents()
            SystemClock.sleep(stepMs)
        }
        return out
    }

    private fun writeGarbageFlac(file: File): File {
        val random = Random(7)
        val payload = ByteArray(8 * 1024)
        random.nextBytes(payload)
        val out = java.io.ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(payload)
        file.outputStream().use { it.write(out.toByteArray()) }
        return file
    }

    private fun corpusFile(name: String): File {
        val file = File(CORPUS_DIR, name)
        if (!file.isFile) {
            throw AssertionError("missing test corpus file: ${file.absolutePath}")
        }
        return file
    }

    private fun writeSineWav(file: File, seconds: Int, claimedSeconds: Int = seconds): File {
        val sampleRate = 48_000
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = sampleRate * seconds
        val dataSize = numFrames * blockAlign
        val claimedDataSize = sampleRate * claimedSeconds * blockAlign
        val amplitude = 0.5
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + claimedDataSize)
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
        buffer.putInt(claimedDataSize)
        for (i in 0 until numFrames) {
            val value = (sin(2.0 * PI * 440.0 * i / sampleRate) * amplitude * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(value)
            buffer.putShort(value)
        }
        file.outputStream().use { it.write(buffer.array()) }
        return file
    }

    private companion object {
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
    }
}
