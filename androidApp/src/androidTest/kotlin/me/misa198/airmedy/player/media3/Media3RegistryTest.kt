package me.misa198.airmedy.player.media3

import android.net.Uri
import android.os.SystemClock
import android.util.Log
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
 * Instrumented checks that the Media3 engine actually demuxes the formats a registered
 * provider supplies (T038, FR-063) and names that provider on failure (FR-064). AIFF is
 * generated here so the test does not depend on the device corpus.
 */
@RunWith(AndroidJUnit4::class)
class Media3RegistryTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000)
    fun aiffPlaysThroughTheKotlinProviderAndReportsIt() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val aiff = writeAiff(File(context().cacheDir, "registry_1s.aiff"), seconds = 1)
        val uri = Uri.fromFile(aiff).toString()
        try {
            runBlocking { engine.prepare(item(aiff.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            assertTrue("OutputStarted expected", awaitOutputStarted(engine, 5_000L))
            SystemClock.sleep(300)
            val before = engine.positionMs()
            SystemClock.sleep(300)
            assertTrue("position should advance", engine.positionMs() > before)
            assertEquals("kotlin-aiff", factory.providerRecorder.providerFor(uri))
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun compressedAiffCErrorsUnderTheKotlinProvider() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val file = File(context().cacheDir, "registry_ima4.aiff")
        file.writeBytes(
            aiff(
                form = "AIFC",
                channels = 1,
                sampleRate = 44_100.0,
                bits = 16,
                samples = ByteArray(256),
                compression = "ima4",
            ),
        )
        val uri = Uri.fromFile(file).toString()
        try {
            val thrown = try {
                runBlocking { engine.prepare(item(file.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
                null
            } catch (t: Throwable) {
                t
            }
            if (thrown != null) {
                // A failed prepare (FR-086) takes the read-time ParserException out of the event stream, so
                // assert on the exception chain instead. Which path ran is logged for the orchestrator.
                Log.i("Media3RegistryTest", "ima4 path: prepare-threw")
                assertTrue(
                    "prepare exception should name ima4: ${chainMessages(thrown)}",
                    chainContains(thrown, "compressed AIFF-C (ima4)"),
                )
            } else {
                // Prepare succeeded and playback failed with an Error event.
                Log.i("Media3RegistryTest", "ima4 path: error-event")
                engine.setFocusGain(0f)
                val error = awaitError(engine, 5_000L)
                assertNotNull("an Error event expected", error)
                assertEquals("kotlin-aiff", error!!.provider)
                assertTrue(
                    "Error cause should name ima4: ${chainMessages(error.cause)}",
                    chainContains(error.cause, "compressed AIFF-C (ima4)"),
                )
            }
            assertEquals("kotlin-aiff", factory.providerRecorder.providerFor(uri))
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
        }
    }

    @Test(timeout = 120_000)
    fun pcmWavPlaysThroughThePlatformProvider() {
        val factory = Media3PlayerFactory(context())
        val engine = Media3Engine(factory)
        val wav = writePcmWav(File(context().cacheDir, "registry_pcm.wav"), seconds = 1)
        val uri = Uri.fromFile(wav).toString()
        try {
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)

            assertTrue("OutputStarted expected", awaitOutputStarted(engine, 5_000L))
            assertEquals("platform", factory.providerRecorder.providerFor(uri))
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

    private fun awaitError(engine: Media3Engine, timeoutMs: Long): EngineEvent.Error? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val error = engine.pollEvents().filterIsInstance<EngineEvent.Error>().firstOrNull()
            if (error != null) return error
            SystemClock.sleep(20)
        }
        return null
    }

    private fun chainContains(error: Throwable, text: String): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current.message?.contains(text) == true) return true
            current = current.cause
        }
        return false
    }

    private fun chainMessages(error: Throwable): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null) {
            messages += "${current.javaClass.simpleName}: ${current.message}"
            current = current.cause
        }
        return messages.joinToString(" <- ")
    }

    // --- Fixtures ------------------------------------------------------------

    /** A 1-second, 16-bit stereo 44.1 kHz uncompressed AIFF. */
    private fun writeAiff(file: File, seconds: Int): File {
        val sampleRate = 44_100
        val channels = 2
        val bytesPerSample = 2
        val frames = sampleRate * seconds
        val samples = ByteArray(frames * channels * bytesPerSample)
        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * 440.0 * frame / sampleRate) * 0.5 * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val high = ((value ushr 8) and 0xFF).toByte()
            val low = (value and 0xFF).toByte()
            for (channel in 0 until channels) {
                val index = (frame * channels + channel) * bytesPerSample
                samples[index] = high
                samples[index + 1] = low
            }
        }
        file.writeBytes(aiff("AIFF", channels, sampleRate.toDouble(), 16, samples, compression = null))
        return file
    }

    /** A 1-second, 16-bit mono 48 kHz PCM WAV. */
    private fun writePcmWav(file: File, seconds: Int): File {
        val sampleRate = 48_000
        val channels = 1
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
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
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
