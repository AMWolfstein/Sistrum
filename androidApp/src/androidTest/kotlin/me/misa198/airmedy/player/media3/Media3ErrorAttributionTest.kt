package me.misa198.airmedy.player.media3

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented checks that a decode error after an automatic gapless advance names the item
 * that is actually playing, not the first track of the run (T041a, FR-064/FR-064a).
 */
@RunWith(AndroidJUnit4::class)
class Media3ErrorAttributionTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000)
    fun errorAfterGaplessAdvanceNamesTheSecondItem() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writePcmWav(File(context().cacheDir, "attribution_first.wav"), seconds = 1)
        val ima4 = File(context().cacheDir, "attribution_second.aifc")
        ima4.writeBytes(
            aiff(
                form = "AIFC",
                channels = 1,
                sampleRate = 44_100.0,
                bits = 16,
                samples = ByteArray(256),
                compression = "ima4",
            ),
        )
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(ima4.absolutePath), ItemGain.Unity) }

            // The ima4 second item fails at read time, after the gapless advance. Wait for that
            // error and assert it is attributed to the second item, not the first WAV.
            val error = awaitError(engine, 15_000L)
            assertNotNull("an Error event was expected once the ima4 second item advanced", error)
            assertEquals("kotlin-aiff", error!!.provider)
            assertEquals("aifc", error.format)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun validSecondWavAdvancesWithoutError() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val first = writePcmWav(File(context().cacheDir, "attribution_control_a.wav"), seconds = 1)
        val second = writePcmWav(File(context().cacheDir, "attribution_control_b.wav"), seconds = 1)
        try {
            runBlocking { engine.prepare(item(first.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            runBlocking { engine.preloadNext(item(second.absolutePath), ItemGain.Unity) }

            val events = collectUntil(engine, 15_000L) { list -> list.any { it is EngineEvent.GaplessAdvanced } }
            assertTrue(
                "a GaplessAdvanced expected but got $events",
                events.any { it is EngineEvent.GaplessAdvanced },
            )
            // Give any spurious error a chance to surface before asserting there was none.
            events += collectFor(engine, 500L)
            assertEquals("no Error expected for two valid WAVs", 0, events.count { it is EngineEvent.Error })
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

    private fun awaitError(engine: Media3Engine, timeoutMs: Long): EngineEvent.Error? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val error = engine.pollEvents().filterIsInstance<EngineEvent.Error>().firstOrNull()
            if (error != null) return error
            SystemClock.sleep(20)
        }
        return null
    }

    private fun collectUntil(
        engine: Media3Engine,
        timeoutMs: Long,
        done: (List<EngineEvent>) -> Boolean,
    ): MutableList<EngineEvent> {
        val out = mutableListOf<EngineEvent>()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            out += engine.pollEvents()
            if (done(out)) return out
            SystemClock.sleep(20)
        }
        return out
    }

    private fun collectFor(engine: Media3Engine, durationMs: Long): List<EngineEvent> {
        val out = mutableListOf<EngineEvent>()
        val deadline = SystemClock.elapsedRealtime() + durationMs
        while (SystemClock.elapsedRealtime() < deadline) {
            out += engine.pollEvents()
            SystemClock.sleep(20)
        }
        return out
    }

    // --- Fixtures ------------------------------------------------------------

    /** A 1-second, 16-bit stereo 44.1 kHz PCM WAV (little-endian RIFF). */
    private fun writePcmWav(file: File, seconds: Int): File {
        val sampleRate = 44_100
        val channels = 2
        val bytesPerSample = 2
        val frames = sampleRate * seconds
        val dataSize = frames * channels * bytesPerSample
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes((36 + dataSize).toLong()))
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(16))
        out.write(le16Bytes(1))
        out.write(le16Bytes(channels))
        out.write(le32Bytes(sampleRate.toLong()))
        out.write(le32Bytes(sampleRate.toLong() * channels * bytesPerSample))
        out.write(le16Bytes(channels * bytesPerSample))
        out.write(le16Bytes(16))
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(dataSize.toLong()))
        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * 440.0 * frame / sampleRate) * 0.5 * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            for (channel in 0 until channels) {
                out.write(value and 0xFF)
                out.write((value ushr 8) and 0xFF)
            }
        }
        file.writeBytes(out.toByteArray())
        return file
    }

    /** Big-endian 16-bit, for the AIFF fixtures. */
    private fun u16Bytes(value: Int): ByteArray =
        byteArrayOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    /** Big-endian 32-bit, for the AIFF fixtures. */
    private fun u32Bytes(value: Long): ByteArray = ByteArray(4) { index ->
        ((value ushr (8 * (3 - index))) and 0xFF).toByte()
    }

    /** Little-endian 16-bit, for the RIFF/WAV fixtures. */
    private fun le16Bytes(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte())

    /** Little-endian 32-bit, for the RIFF/WAV fixtures. */
    private fun le32Bytes(value: Long): ByteArray = ByteArray(4) { index ->
        ((value ushr (8 * index)) and 0xFF).toByte()
    }

    /** The 80-bit IEEE 754 extended float the AIFF COMM chunk uses for sample rate. */
    private fun extended80(value: Double): ByteArray {
        val result = ByteArray(10)
        if (value == 0.0) return result
        val bits = java.lang.Double.doubleToLongBits(value)
        val sign = (bits ushr 63) and 1L
        val unbiased = ((bits ushr 52) and 0x7FFL).toInt() - 1023
        val exponent = unbiased + 16383
        val mantissa = (1L shl 63) or ((bits and 0x000FFFFFFFFFFFFFL) shl 11)
        result[0] = ((sign shl 7) or ((exponent ushr 8) and 0x7F).toLong()).toByte()
        result[1] = (exponent and 0xFF).toByte()
        for (index in 0 until 8) {
            result[2 + index] = ((mantissa ushr (8 * (7 - index))) and 0xFF).toByte()
        }
        return result
    }

    private fun chunk(id: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(u32Bytes(body.size.toLong()))
        out.write(body)
        if (body.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }

    private fun commBody(
        channels: Int,
        frames: Int,
        bits: Int,
        sampleRate: Double,
        compression: String?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u16Bytes(channels))
        out.write(u32Bytes(frames.toLong()))
        out.write(u16Bytes(bits))
        out.write(extended80(sampleRate))
        compression?.let { out.write(it.toByteArray(Charsets.US_ASCII)) }
        return out.toByteArray()
    }

    private fun ssndBody(samples: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32Bytes(0))
        out.write(u32Bytes(0))
        out.write(samples)
        return out.toByteArray()
    }

    private fun aiff(
        form: String,
        channels: Int,
        sampleRate: Double,
        bits: Int,
        samples: ByteArray,
        compression: String?,
    ): ByteArray {
        val frames = samples.size / (channels * (bits / 8))
        val comm = chunk("COMM", commBody(channels, frames, bits, sampleRate, compression))
        val ssnd = chunk("SSND", ssndBody(samples))
        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.US_ASCII))
        out.write(u32Bytes((4 + comm.size + ssnd.size).toLong()))
        out.write(form.toByteArray(Charsets.US_ASCII))
        out.write(comm)
        out.write(ssnd)
        return out.toByteArray()
    }
}
