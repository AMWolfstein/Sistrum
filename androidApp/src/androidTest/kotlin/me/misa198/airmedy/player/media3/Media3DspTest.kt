package me.misa198.airmedy.player.media3

import android.os.Bundle
import android.os.Process
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
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.GlobalDspConfig
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented per-player DSP chain tests for [Media3PlayerFactory] and [Media3Engine] (T045).
 * Every test builds its own factory/engines, mutes them via [Media3Engine.setFocusGain] (the
 * [TeeAudioProcessor] sits in the sink pipeline before the player volume, so it still captures),
 * and closes every engine in `finally`, asserting no player or DSP chain leaks.
 */
@RunWith(AndroidJUnit4::class)
class Media3DspTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120_000)
    fun bandCentreToneResponseMatchesGolden48k() {
        assertBandCentreGolden(48_000, GOLDEN_48K)
    }

    @Test(timeout = 120_000)
    fun bandCentreToneResponseMatchesGolden44k() {
        assertBandCentreGolden(44_100, GOLDEN_44K)
    }

    @Test(timeout = 120_000)
    fun preampAndWidthApply() {
        val refs = captureWidthMeasure(48_000, null)
        val run = captureWidthMeasure(
            48_000,
            GlobalDspConfig(preampGainDb = -6f, stereoWidth = 0.5f),
        )
        val g = 10.0.pow(-6.0 / 20.0)
        assertDbClose(0.75 * g * refs.l1k, run.l1k, 0.1, "L'@1k")
        assertDbClose(0.25 * g * refs.l1k, run.r1k, 0.1, "R'@1k")
        assertDbClose(0.75 * g * refs.r3k, run.r3k, 0.1, "R'@3k")
        assertDbClose(0.25 * g * refs.r3k, run.l3k, 0.1, "L'@3k")
    }

    @Test(timeout = 120_000)
    fun liveChangeReachesEveryLivePlayer() {
        val recorders = listOf(DspRecorder(), DspRecorder(), DspRecorder())
        var next = 0
        val factory = Media3PlayerFactory(context()) {
            val recorder = recorders[next++]
            arrayOf<AudioProcessor>(TeeAudioProcessor(recorder))
        }
        val wav6 = writeBandToneWav(File(context().cacheDir, "live6_${System.nanoTime()}.wav"), 48_000, 6.0)
        val wav4 = writeBandToneWav(File(context().cacheDir, "live4_${System.nanoTime()}.wav"), 48_000, 4.0)
        val engine1 = Media3Engine(factory)
        val engine2 = Media3Engine(factory)
        try {
            runBlocking { engine1.prepare(item(wav6.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine1.setFocusGain(0f)
            runBlocking { engine2.prepare(item(wav6.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine2.setFocusGain(0f)

            assertTrue("engine1 output should start", awaitOutputStarted(engine1, 10_000L))
            assertTrue("engine2 output should start", awaitOutputStarted(engine2, 10_000L))
            SystemClock.sleep(1_000)
            assertEquals("two live dsp chains while both play", 2, factory.liveDspChains)

            engine1.setDsp(mixedSettings())
            assertEquals("two live dsp chains after change", 2, factory.liveDspChains)

            val engine3 = Media3Engine(factory)
            try {
                runBlocking { engine3.prepare(item(wav4.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
                engine3.setFocusGain(0f)

                assertTrue("engine1 should reach Ended", awaitEnded(engine1, 30_000L))
                assertTrue("engine2 should reach Ended", awaitEnded(engine2, 30_000L))
                assertTrue("engine3 should reach Ended", awaitEnded(engine3, 30_000L))

                for (recorder in recorders.take(2)) {
                    val sr = recorder.sampleRate
                    val samples = recorder.samples()
                    val before1k = amplitude(samples, recorder.channelCount, 0, sr, 0.2, 0.5, 1000.0)
                    val after1k = amplitude(samples, recorder.channelCount, 0, sr, 4.5, 1.0, 1000.0)
                    val before2k = amplitude(samples, recorder.channelCount, 0, sr, 0.2, 0.5, 2000.0)
                    val after2k = amplitude(samples, recorder.channelCount, 0, sr, 4.5, 1.0, 2000.0)
                    val gain1k = 20.0 * log10(after1k / before1k)
                    val gain2k = 20.0 * log10(after2k / before2k)
                    assertTrue("1000 Hz live gain $gain1k dB vs -1.9258 ±0.1", abs(gain1k - (-1.9258)) <= 0.1)
                    assertTrue("2000 Hz live gain $gain2k dB vs 1.4202 ±0.1", abs(gain2k - 1.4202) <= 0.1)
                }

                val sr3 = recorders[2].sampleRate
                val samples3 = recorders[2].samples()
                val amp1k = amplitude(samples3, recorders[2].channelCount, 0, sr3, 2.0, 1.0, 1000.0)
                assertDbClose(BAND_AMP * 10.0.pow(-1.9258 / 20.0), amp1k, 0.1, "seeded 1000 Hz")
            } finally {
                engine3.close()
            }
        } finally {
            engine2.close()
            engine1.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    @Test(timeout = 900_000)
    fun chainCpuCostReported() {
        val wav = writeBandToneWav(
            File(context().cacheDir, "cpu_${System.nanoTime()}.wav"),
            48_000,
            130.0,
            addRight440 = true,
        )
        val noChain = measureCpu(wav, dspChainEnabled = false, null)
        val neutral = measureCpu(wav, dspChainEnabled = true, null)
        val full = measureCpu(
            wav,
            dspChainEnabled = true,
            GlobalDspConfig(preampGainDb = -3f, stereoWidth = 0.5f, eqBandGainsDb = mixedGainsArray()),
        )
        val text = "noChain=$noChain neutral=$neutral full=$full cpuPoints"
        Log.i("Media3DspTest", "dsp_cpu: $text")
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("dsp_cpu", text) },
        )
        assertTrue(
            "neutral ($neutral) should be within +1.0 CPU point of noChain ($noChain)",
            neutral <= noChain + 1.0,
        )
    }

    private fun assertBandCentreGolden(sampleRate: Int, golden: DoubleArray) {
        val runA = captureBandTone(sampleRate, null)
        assertEquals("run A encoding should be float", C.ENCODING_PCM_FLOAT, runA.encoding)
        for (i in BAND_FREQ.indices) {
            assertDbClose(BAND_AMP, runA.amplitudes[i], 0.1, "neutral band ${BAND_FREQ[i]} Hz @ $sampleRate")
        }

        val runB = captureBandTone(sampleRate, mixedSettings())
        for (i in BAND_FREQ.indices) {
            val gainDb = 20.0 * log10(runB.amplitudes[i] / runA.amplitudes[i])
            assertTrue(
                "band ${BAND_FREQ[i]} Hz @ $sampleRate: gain $gainDb dB, expected ${golden[i]} ±0.1",
                abs(gainDb - golden[i]) <= 0.1,
            )
        }
    }

    /** Plays the band-tone WAV once and returns the 10 band-centre amplitudes over window 2.0–3.0 s. */
    private fun captureBandTone(sampleRate: Int, settings: EqualizerSettings?): BandCapture {
        val recorder = DspRecorder()
        val factory = Media3PlayerFactory(context()) { arrayOf<AudioProcessor>(TeeAudioProcessor(recorder)) }
        val engine = Media3Engine(factory)
        val wav = writeBandToneWav(File(context().cacheDir, "band_${System.nanoTime()}.wav"), sampleRate, 4.0)
        try {
            if (settings != null) engine.setDsp(settings)
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("engine should reach Ended", awaitEnded(engine, 30_000L))
            return BandCapture(bandAmplitudes(recorder), recorder.encoding)
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    /** Plays the 1 kHz/3 kHz stereo WAV once and returns L/R amplitudes at 1 kHz and 3 kHz. */
    private fun captureWidthMeasure(sampleRate: Int, config: GlobalDspConfig?): WidthMeasure {
        val recorder = DspRecorder()
        val factory = Media3PlayerFactory(context()) { arrayOf<AudioProcessor>(TeeAudioProcessor(recorder)) }
        val engine = Media3Engine(factory)
        val wav = writeStereoTestToneWav(File(context().cacheDir, "w_${System.nanoTime()}.wav"), sampleRate, 4.0)
        try {
            if (config != null) factory.setDsp(config)
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("engine should reach Ended", awaitEnded(engine, 30_000L))
            val sr = recorder.sampleRate
            val samples = recorder.samples()
            return WidthMeasure(
                l1k = amplitude(samples, recorder.channelCount, 0, sr, 2.0, 1.0, 1000.0),
                r1k = amplitude(samples, recorder.channelCount, 1, sr, 2.0, 1.0, 1000.0),
                l3k = amplitude(samples, recorder.channelCount, 0, sr, 2.0, 1.0, 3000.0),
                r3k = amplitude(samples, recorder.channelCount, 1, sr, 2.0, 1.0, 3000.0),
            )
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    /** Plays [wav] for one CPU measurement window and returns CPU points over 120 s. */
    private fun measureCpu(wav: File, dspChainEnabled: Boolean, config: GlobalDspConfig?): Double {
        val factory = Media3PlayerFactory(context(), dspChainEnabled = dspChainEnabled)
        val engine = Media3Engine(factory)
        try {
            if (config != null) factory.setDsp(config)
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("output should start", awaitOutputStarted(engine, 30_000L))
            SystemClock.sleep(5_000)

            val cpuStart = Process.getElapsedCpuTime()
            val wallStart = SystemClock.elapsedRealtime()
            SystemClock.sleep(120_000)
            val cpuEnd = Process.getElapsedCpuTime()
            val wallEnd = SystemClock.elapsedRealtime()

            val cpuMsDelta = (cpuEnd - cpuStart).toDouble()
            val wallMsDelta = (wallEnd - wallStart).toDouble()
            return 100.0 * cpuMsDelta / wallMsDelta
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    /** Measures the 10 band-centre amplitudes over window 2.0–3.0 s on the left channel. */
    private fun bandAmplitudes(recorder: DspRecorder): DoubleArray {
        val sr = recorder.sampleRate
        val samples = recorder.samples()
        return DoubleArray(BAND_FREQ.size) { i ->
            amplitude(samples, recorder.channelCount, 0, sr, 2.0, 1.0, BAND_FREQ[i])
        }
    }

    /**
     * Exact DFT bin magnitude at [freqHz] over a [windowSeconds] window starting at
     * [startSeconds]. All test frequencies are integer Hz and windows are an integer number of
     * seconds, so the window holds an integer number of cycles and the bin has no leakage.
     * Returns the peak (not RMS) amplitude.
     */
    private fun amplitude(
        samples: FloatArray,
        channels: Int,
        channel: Int,
        sampleRate: Int,
        startSeconds: Double,
        windowSeconds: Double,
        freqHz: Double,
    ): Double {
        val frameStart = (startSeconds * sampleRate).roundToInt()
        val frameCount = (windowSeconds * sampleRate).roundToInt()
        var re = 0.0
        var im = 0.0
        val w = 2.0 * PI * freqHz / sampleRate
        for (i in 0 until frameCount) {
            val x = samples[(frameStart + i) * channels + channel].toDouble()
            re += x * cos(w * i)
            im -= x * sin(w * i)
        }
        return 2.0 * sqrt(re * re + im * im) / frameCount
    }

    /** Asserts that [actual]/[expected] is within [toleranceDb] of 0 dB. */
    private fun assertDbClose(expected: Double, actual: Double, toleranceDb: Double, message: String) {
        val db = 20.0 * log10(actual / expected)
        assertTrue("$message: $db dB (expected 0 ± $toleranceDb dB)", abs(db) <= toleranceDb)
    }

    private fun mixedSettings() = EqualizerSettings(
        enabled = true,
        presetKey = "flat",
        editedGainsDb = mapOf("flat" to MIXED_GAINS),
    )

    private fun mixedGainsArray() = MIXED_GAINS.toFloatArray()

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

    private fun awaitEnded(engine: Media3Engine, timeoutMs: Long): Boolean =
        collectUntil(engine, timeoutMs) { events -> events.any { it is EngineEvent.Ended } }
            .any { it is EngineEvent.Ended }

    /** Writes a 16-bit stereo WAV at [sampleRate] for [seconds] using a per-sample callback. */
    private fun writeWav(
        file: File,
        sampleRate: Int,
        seconds: Double,
        sample: (frame: Int, channel: Int) -> Double,
    ): File {
        val channels = CHANNELS
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
        for (i in 0 until numFrames) {
            for (c in 0 until channels) {
                val value = sample(i.toInt(), c).coerceIn(-1.0, 1.0)
                buffer.putShort(
                    (value * 32767.0).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort(),
                )
            }
        }
        file.outputStream().use { it.write(buffer.array()) }
        return file
    }

    /** Sum of the 10 band-centre sines at [BAND_AMP] each, optionally adding 440 Hz on the right. */
    private fun writeBandToneWav(
        file: File,
        sampleRate: Int,
        seconds: Double,
        addRight440: Boolean = false,
    ): File = writeWav(file, sampleRate, seconds) { frame, channel ->
        var value = 0.0
        for (f in BAND_FREQ) value += BAND_AMP * sin(2.0 * PI * f * frame / sampleRate)
        if (addRight440 && channel == 1) value += BAND_AMP * sin(2.0 * PI * 440.0 * frame / sampleRate)
        value
    }

    /** Left = 1000 Hz, right = 3000 Hz, amplitude 0.2 each. */
    private fun writeStereoTestToneWav(file: File, sampleRate: Int, seconds: Double): File =
        writeWav(file, sampleRate, seconds) { frame, channel ->
            val freq = if (channel == 0) 1000.0 else 3000.0
            0.2 * sin(2.0 * PI * freq * frame / sampleRate)
        }

    private data class BandCapture(val amplitudes: DoubleArray, val encoding: Int)
    private data class WidthMeasure(val l1k: Double, val r1k: Double, val l3k: Double, val r3k: Double)

    /**
     * Records every buffer the [TeeAudioProcessor] receives (after the DSP chain, before the track
     * volume), so the test can measure float samples.
     */
    private class DspRecorder : TeeAudioProcessor.AudioBufferSink {
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
        const val CHANNELS = 2
        const val BAND_AMP = 0.03
        val BAND_FREQ = doubleArrayOf(32.0, 64.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        val MIXED_GAINS = listOf(12f, -12f, 6f, -6f, 3f, -3f, 0f, 9f, -9f, 1.5f)
        val GOLDEN_44K = doubleArrayOf(8.3720, -6.4574, 1.1825, -4.1473, 0.5446, -1.9267, 1.4213, 6.4671, -6.4972, 0.7327)
        val GOLDEN_48K = doubleArrayOf(8.3937, -6.4689, 1.1751, -4.1489, 0.5437, -1.9258, 1.4202, 6.3970, -6.3773, 0.5163)
    }
}
