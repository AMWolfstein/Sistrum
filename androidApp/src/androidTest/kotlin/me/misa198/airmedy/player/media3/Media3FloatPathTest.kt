package me.misa198.airmedy.player.media3

import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.ExoPlayer
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
 * Instrumented float-DSP-chain and mixer-block tests for [Media3PlayerFactory] (T048b). Every test
 * builds its own factory/engines, mutes them (so the device stays quiet), and closes everything in
 * `finally`, asserting no player or DSP chain leaks.
 */
@RunWith(AndroidJUnit4::class)
class Media3FloatPathTest {

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sendStatus(code: Int, bundle: Bundle) =
        InstrumentationRegistry.getInstrumentation().sendStatus(code, bundle)

    @Test(timeout = 120_000)
    fun neutralChainIsBitExactInFloat() {
        for (name in listOf("wav_pcm16_48.wav", "wav_pcm24_96.wav", "aiff_none_32.aiff", "wav_pcm_f32.wav")) {
            val file = corpusFile(name)
            val withChain = captureNeutral(file, dspChainEnabled = true)
            val withoutChain = captureNeutral(file, dspChainEnabled = false)
            assertEquals("tap encoding should be float for $name", C.ENCODING_PCM_FLOAT, withChain.encoding)
            assertEquals("tap encoding should be float for $name", C.ENCODING_PCM_FLOAT, withoutChain.encoding)

            val a = withChain.rawBytes()
            val b = withoutChain.rawBytes()
            val common = minOf(a.size, b.size)
            val minBytes = 2 * withChain.sampleRate * withChain.channelCount * 4
            assertTrue(
                "common length $common must be >= 2 s of frames ($minBytes) for $name",
                common >= minBytes,
            )
            assertTrue(
                "neutral chain must be bit-exact vs no chain for $name",
                a.copyOfRange(0, common).contentEquals(b.copyOfRange(0, common)),
            )
        }
    }

    @Test(timeout = 120_000)
    fun hiResSurvivesTheChain() {
        val file = corpusFile("wav_pcm24_96.wav")

        val floatFraction = captureHiResFraction(file, floatSink = true).first
        val intCapture = captureHiResFraction(file, floatSink = false)
        val intFraction = intCapture.first
        val intEncoding = intCapture.second

        sendStatus(
            0,
            Bundle().apply {
                putString(
                    "hi_res_fractions",
                    "floatFraction=$floatFraction intFraction=$intFraction intEncoding=$intEncoding",
                )
            },
        )

        assertTrue(
            "> 50% of non-zero float samples should be finer than 16-bit (fraction=$floatFraction)",
            floatFraction > 0.5,
        )
        val intIs16Bit = intEncoding == C.ENCODING_PCM_16BIT
        val intAll16 = intFraction == 0.0
        assertTrue(
            "int path must be 16-bit or all samples multiples of 1/32768 (encoding=$intEncoding, fraction=$intFraction)",
            intIs16Bit || intAll16,
        )
    }

    @Test(timeout = 120_000)
    fun bandCentreToneResponseMatchesGolden96k() {
        val runA = captureBandTone(96_000, null)
        assertEquals("run A encoding should be float", C.ENCODING_PCM_FLOAT, runA.encoding)
        for (i in BAND_FREQ.indices) {
            assertDbClose(BAND_AMP, runA.amplitudes[i], 0.1, "neutral band ${BAND_FREQ[i]} Hz @ 96000")
        }

        val runB = captureBandTone(96_000, mixedSettings())
        for (i in BAND_FREQ.indices) {
            val gainDb = 20.0 * log10(runB.amplitudes[i] / runA.amplitudes[i])
            assertTrue(
                "band ${BAND_FREQ[i]} Hz @ 96000: gain $gainDb dB, expected ${GOLDEN_96K[i]} ±0.1",
                abs(gainDb - GOLDEN_96K[i]) <= 0.1,
            )
        }
    }

    @Test(timeout = 600_000)
    fun twoPlayersOnSharedSessionHeldForRoutingCheck() {
        val holdSeconds = InstrumentationRegistry.getArguments().getString("holdSeconds")?.toIntOrNull() ?: 3
        val factory = Media3PlayerFactory(context())
        val session = factory.newLimiterSession()
        factory.call { session.open(true) }
        val files = listOf("wav_pcm16_48.wav", "wav_pcm24_96.wav", "wav_pcm_f32.wav").map { corpusFile(it) }
        try {
            for (file in files) {
                val players = factory.call {
                    val p1 = factory.newPlayer()
                    val p2 = factory.newPlayer()
                    p1.setAudioSessionId(session.sessionId)
                    p2.setAudioSessionId(session.sessionId)
                    val uri = Uri.fromFile(file)
                    p1.setMediaItem(MediaItem.fromUri(uri))
                    p2.setMediaItem(MediaItem.fromUri(uri))
                    p1.repeatMode = Player.REPEAT_MODE_ONE
                    p2.repeatMode = Player.REPEAT_MODE_ONE
                    p1.volume = 0f
                    p2.volume = 0f
                    p1.prepare()
                    p2.prepare()
                    p1.playWhenReady = true
                    p2.playWhenReady = true
                    listOf(p1, p2)
                }
                try {
                    awaitPlayingAndSession(players, factory, session.sessionId, 30_000L)
                    Log.i(TAG, "HOLD_START ${file.name} session=${session.sessionId}")
                    sendStatus(0, Bundle().apply { putString("hold", "start ${file.name} session=${session.sessionId}") })
                    SystemClock.sleep(holdSeconds * 1_000L)
                    Log.i(TAG, "HOLD_END ${file.name}")
                    factory.call {
                        for (p in players) {
                            p.playerError?.let {
                                assertTrue("player error after hold: ${it.message ?: it.errorCodeName}", false)
                            }
                            assertTrue("player left the shared session after hold", p.audioSessionId == session.sessionId)
                            assertTrue("player not playing after hold", p.isPlaying)
                        }
                    }
                } finally {
                    for (p in players) factory.release(p)
                }
            }

            var blockMs = factory.mixerBlockMs.value
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (blockMs == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100L)
                blockMs = factory.mixerBlockMs.value
            }
            assertTrue("mixer block should be non-null", blockMs != null)
            val block = blockMs!!
            assertTrue("mixer block should be in [1, 100] ms (was $block)", block in 1f..100f)

            var frameMs = session.frameDurationMsForTest
            val frameDeadline = SystemClock.elapsedRealtime() + 3_000L
            while (abs(frameMs - block) >= 0.5f && SystemClock.elapsedRealtime() < frameDeadline) {
                SystemClock.sleep(100L)
                frameMs = session.frameDurationMsForTest
            }
            sendStatus(
                0,
                Bundle().apply {
                    putString("mixer_block", "mixerBlockMs=$block frameDurationMs=$frameMs")
                },
            )
            assertTrue(
                "session frame duration should follow measured block (block=$block ms, frame=$frameMs ms)",
                abs(frameMs - block) < 0.5f,
            )
        } finally {
            session.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    @Test(timeout = 900_000)
    fun floatVsIntCpuAndMemoryReported() {
        val wav = writeBandToneWav(
            File(context().cacheDir, "cpu96_${System.nanoTime()}.wav"),
            48_000,
            130.0,
            addRight440 = true,
        )
        val full = GlobalDspConfig(
            preampGainDb = -3f,
            stereoWidth = 0.5f,
            eqBandGainsDb = mixedGainsArray(),
        )
        val floatNeutral = measureCpuAndPss(wav, floatSink = true, dspChainEnabled = true, config = null)
        val floatFull = measureCpuAndPss(wav, floatSink = true, dspChainEnabled = true, config = full)
        val intNeutral = measureCpuAndPss(wav, floatSink = false, dspChainEnabled = true, config = null)
        val intFull = measureCpuAndPss(wav, floatSink = false, dspChainEnabled = true, config = full)

        val text = "float_vs_int: floatNeutral=${floatNeutral.cpuPoints} floatFull=${floatFull.cpuPoints} " +
            "intNeutral=${intNeutral.cpuPoints} intFull=${intFull.cpuPoints} cpuPoints " +
            "pss floatNeutral=${floatNeutral.pssKb} floatFull=${floatFull.pssKb} " +
            "intNeutral=${intNeutral.pssKb} intFull=${intFull.pssKb} kB"
        Log.i(TAG, text)
        sendStatus(0, Bundle().apply { putString("float_vs_int", text) })
    }

    /** Plays [file] for ~3 s with a tap and returns the recorder (float sink). */
    private fun captureNeutral(file: File, dspChainEnabled: Boolean): DspRecorder {
        val recorder = DspRecorder()
        val factory = Media3PlayerFactory(context(), dspChainEnabled = dspChainEnabled) {
            arrayOf<AudioProcessor>(TeeAudioProcessor(recorder))
        }
        val engine = Media3Engine(factory)
        try {
            runBlocking { engine.prepare(item(file.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("output should start", awaitOutputStarted(engine, 30_000L))
            SystemClock.sleep(3_200L)
            engine.pause()
            return recorder
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    /** Returns the fraction of non-zero samples finer than 16-bit, and the tap encoding. */
    private fun captureHiResFraction(file: File, floatSink: Boolean): Pair<Double, Int> {
        val recorder = DspRecorder()
        val factory = Media3PlayerFactory(context(), floatSink = floatSink) {
            arrayOf<AudioProcessor>(TeeAudioProcessor(recorder))
        }
        val engine = Media3Engine(factory)
        try {
            runBlocking { engine.prepare(item(file.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("output should start", awaitOutputStarted(engine, 30_000L))
            SystemClock.sleep(3_000L)
            engine.pause()
            val samples = recorder.samples()
            var nonZero = 0
            var finer = 0
            for (x in samples) {
                if (x != 0f) {
                    nonZero++
                    if ((x * 32768f) % 1f != 0f) finer++
                }
            }
            val fraction = if (nonZero == 0) 0.0 else finer.toDouble() / nonZero.toDouble()
            return fraction to recorder.encoding
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
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

    private fun measureCpuAndPss(
        wav: File,
        floatSink: Boolean,
        dspChainEnabled: Boolean,
        config: GlobalDspConfig?,
    ): CpuMeasure {
        val factory = Media3PlayerFactory(context(), floatSink = floatSink, dspChainEnabled = dspChainEnabled)
        val engine = Media3Engine(factory)
        try {
            if (config != null) factory.setDsp(config)
            runBlocking { engine.prepare(item(wav.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("output should start", awaitOutputStarted(engine, 30_000L))
            SystemClock.sleep(5_000L)

            val cpuStart = Process.getElapsedCpuTime()
            val wallStart = SystemClock.elapsedRealtime()
            SystemClock.sleep(120_000L)
            val cpuEnd = Process.getElapsedCpuTime()
            val wallEnd = SystemClock.elapsedRealtime()

            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)

            val cpuPoints = 100.0 * (cpuEnd - cpuStart).toDouble() / (wallEnd - wallStart).toDouble()
            return CpuMeasure(cpuPoints, memInfo.totalPss)
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
     * [startSeconds]. Returns the peak (not RMS) amplitude.
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

    private fun corpusFile(name: String): File {
        val file = File(CORPUS_DIR, name)
        if (!file.isFile) {
            throw AssertionError("missing test corpus file: ${file.absolutePath}")
        }
        return file
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

    private fun awaitEnded(engine: Media3Engine, timeoutMs: Long): Boolean =
        collectUntil(engine, timeoutMs) { events -> events.any { it is EngineEvent.Ended } }
            .any { it is EngineEvent.Ended }

    private fun awaitPlayingAndSession(
        players: List<ExoPlayer>,
        factory: Media3PlayerFactory,
        sessionId: Int,
        timeoutMs: Long,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = factory.call {
                val errors = players.mapNotNull { it.playerError }
                val sessionOk = players.all { it.audioSessionId == sessionId }
                val playing = players.all { it.isPlaying }
                Triple(errors, sessionOk, playing)
            }
            val (errors, sessionOk, playing) = snapshot
            if (errors.isNotEmpty()) {
                assertTrue(
                    "player error while waiting: ${errors.first().message ?: errors.first().errorCodeName}",
                    false,
                )
            }
            assertTrue("player left the shared session", sessionOk)
            if (playing) return
            SystemClock.sleep(50)
        }
        assertTrue("players did not reach shared-session playing state", false)
    }

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

    private data class BandCapture(val amplitudes: DoubleArray, val encoding: Int)
    private data class CpuMeasure(val cpuPoints: Double, val pssKb: Int)

    /**
     * Records every buffer the [TeeAudioProcessor] receives (after the DSP chain, before the track
     * volume), so the test can measure float samples and raw bytes.
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

        fun rawBytes(): ByteArray = data.toByteArray()

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
        const val TAG = "Media3FloatPathTest"
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
        const val CHANNELS = 2
        const val BAND_AMP = 0.03
        val BAND_FREQ = doubleArrayOf(32.0, 64.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        val MIXED_GAINS = listOf(12f, -12f, 6f, -6f, 3f, -3f, 0f, 9f, -9f, 1.5f)
        val GOLDEN_96K = doubleArrayOf(8.3192, -6.4279, 1.2095, -4.1377, 0.5440, -1.9214, 1.4163, 6.1218, -5.8641, -0.4125)
    }
}
