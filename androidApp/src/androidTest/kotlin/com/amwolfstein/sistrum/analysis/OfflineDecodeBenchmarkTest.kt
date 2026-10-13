package com.amwolfstein.sistrum.analysis

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import com.amwolfstein.sistrum.analysis.loudness.LoudnessMeter
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.engine.EngineEvent
import com.amwolfstein.sistrum.player.engine.ItemGain
import com.amwolfstein.sistrum.player.media3.Media3Engine
import com.amwolfstein.sistrum.player.media3.Media3PlayerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented offline-decode benchmark and accuracy tests (T051d). Reads the corpus from
 * `/sdcard/Music/SistrumTestCorpus` (a missing file skips via [assumeTrue]) and reports every
 * number with `sendStatus` and `Log.i`, one tab-separated line per file. The orchestrator runs
 * this on a phone; this file is only required to compile here.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDecodeBenchmarkTest {

    @Test(timeout = 180_000)
    fun offlineDecodeMatchesPlaybackCapture() {
        val names = listOf(
            "flac_untagged.flac",
            "opus_untagged.opus",
            "mp3_untagged.mp3",
            "m4a_aac_untagged.m4a",
            "aiff_none_24.aiff",
            "wav_pcm24_96.wav",
        )
        var present = 0
        for (name in names) {
            val file = File(CORPUS_DIR, name)
            if (!file.isFile) {
                report("matches", name, "SKIP", "reason=missing")
                continue
            }
            present++
            val playback = capturePlayback(file)
            val offline = decodeToMemory(file.absolutePath, Process.THREAD_PRIORITY_BACKGROUND)

            assertEquals("rate mismatch for $name", playback.rate, offline.rate)
            assertEquals("channels mismatch for $name", playback.channels, offline.channels)

            val common = minOf(playback.samples.size, offline.samples.size)
            val minSamples = offline.rate * offline.channels
            assertTrue("at least 1 s compared for $name (common=$common)", common >= minSamples)

            var maxDiff = 0f
            for (i in 0 until common) {
                val d = abs(playback.samples[i] - offline.samples[i])
                if (d > maxDiff) maxDiff = d
            }
            assertEquals("offline must match playback bit for bit for $name", 0f, maxDiff, 0f)
            report(
                "matches",
                name,
                "tapEncoding=${encodingName(playback.encoding)}",
                "rate=${offline.rate}",
                "channels=${offline.channels}",
                "comparedFrames=${common / offline.channels}",
                "maxDiff=$maxDiff",
            )
        }
        assertTrue("no files present for the playback comparison", present > 0)
    }

    @Test(timeout = 600_000)
    fun corpusLoudnessMatchesOracle() {
        val rows = readOracleRows().filter { it.file.startsWith("sistrum/") && it.status == "ok" }
        assumeTrue("no sistrum oracle rows", rows.isNotEmpty())

        var present = 0
        var compared = 0
        val failedNames = mutableListOf<String>()
        val outOfTolerance = mutableListOf<String>()
        for (row in rows) {
            val name = row.file.substringAfter("sistrum/")
            val file = File(CORPUS_DIR, name)
            if (!file.isFile) {
                report("loudness", name, "SKIP", "reason=missing")
                continue
            }
            present++
            val (outcome, meter) = decodeAndMeter(file.absolutePath)
            when (outcome) {
                is DecodeOutcome.Completed -> {
                    val oursLufs = meter?.integratedLufs()
                    val oursTp = meter?.truePeakDbtp()
                    val (tolLu, tolTp) = if (isLossless(name)) 0.01 to 0.05 else 0.1 to 0.5
                    val oracleLufs = row.integratedLufs
                    val oracleTp = row.truePeakDbtp
                    val dLu = if (oursLufs != null && oracleLufs != null && oracleLufs.isFinite()) {
                        abs(oursLufs - oracleLufs)
                    } else {
                        Double.NaN
                    }
                    val dTp = if (oursTp != null && oracleTp != null && oracleTp.isFinite()) {
                        abs(oursTp - oracleTp)
                    } else {
                        Double.NaN
                    }
                    // WaxFlow decodes to float; integer-output platform decoders (e.g. c2.android.opus, 16-bit) clip a
                    // source whose oracle true peak is above 0 dBTP, as playback does. Such rows check that the clip is
                    // the only difference: our sample peak at full scale, loudness within 0.5 LU.
                    val clipped = !isLossless(name) && oracleTp != null && oracleTp > 0.0
                    val oursSamplePeak = meter?.samplePeakDbfs()
                    val bad = if (clipped) {
                        dLu.isNaN() || dLu > 0.5 || oursSamplePeak == null || oursSamplePeak > 0.01 || oursSamplePeak < -0.1
                    } else {
                        dLu.isNaN() || dLu > tolLu || dTp.isNaN() || dTp > tolTp
                    }
                    report(
                        "loudness",
                        name,
                        "outcome=Completed",
                        "provider=${outcome.providerId}",
                        "oursLufs=$oursLufs",
                        "oracleLufs=$oracleLufs",
                        "oursTp=$oursTp",
                        "oracleTp=$oracleTp",
                        "dLu=$dLu",
                        "dTp=$dTp",
                        "clippedByDecoder=$clipped",
                        "oursSamplePeak=$oursSamplePeak",
                    )
                    if (bad) outOfTolerance += "$name (dLu=$dLu, dTp=$dTp)"
                    compared++
                }

                is DecodeOutcome.Unsupported -> report("loudness", name, "SKIP", "reason=${outcome.reason}")
                is DecodeOutcome.Failed -> {
                    failedNames += name
                    report("loudness", name, "SKIP", "reason=${outcome.reason}")
                }
                is DecodeOutcome.Cancelled -> report("loudness", name, "SKIP", "reason=cancelled")
            }
        }

        assumeTrue("no corpus files present", present > 0)
        assertTrue("zero of $present present files compared (failed: $failedNames)", compared > 0)
        assertTrue("more than half of $present present files failed: $failedNames", failedNames.size <= present / 2)
        assertTrue("out of tolerance: ${outOfTolerance.joinToString("; ")}", outOfTolerance.isEmpty())
    }

    @Test(timeout = 1_800_000)
    fun decodeAndMeterSpeedPerFormat() {
        reportDeviceInfo()
        val files = listOf(
            "opus_untagged.opus",
            "ogg_untagged.ogg",
            "flac_untagged.flac",
            "flac_24_96.flac",
            "flac_24_192.flac",
            "m4a_alac_untagged.m4a",
            "mp3_untagged.mp3",
            "m4a_aac_untagged.m4a",
            "aiff_none_24.aiff",
            "wav_pcm16_48.wav",
            "wav_pcm24_96.wav",
            "wav_pcm_f32.wav",
        )
        // A file this device has no decoder for (e.g. ALAC on the CPH2307) is reported and left out, as the scan does.
        val decodable = ArrayList<String>()
        for (name in files) {
            val file = File(CORPUS_DIR, name)
            if (!file.isFile) {
                report("speed", name, "SKIP", "reason=missing")
                continue
            }
            val probe = OfflineDecoder(context()).decode(file.absolutePath, null, PcmConsumer { _, _, _, _ -> })
            if (probe is DecodeOutcome.Failed && probe.reason.startsWith("no decoder")) {
                report("speed", name, "SKIP", "reason=${probe.reason}")
                continue
            }
            decodable += name
        }
        assertTrue("no decodable files present for the speed test", decodable.isNotEmpty())

        for (priority in listOf(Process.THREAD_PRIORITY_BACKGROUND, Process.THREAD_PRIORITY_DEFAULT)) {
            for (name in decodable) {
                val decodeMsPerSec = ArrayList<Double>()
                val decodeCpuMsPerSec = ArrayList<Double>()
                val meterWallMsPerSec = ArrayList<Double>()
                val meterThreadMsPerSec = ArrayList<Double>()
                val combinedMsPerSec = ArrayList<Double>()
                val decodeAffinities = ArrayList<Affinity>()
                val meterAffinities = ArrayList<Affinity>()
                val combinedAffinities = ArrayList<Affinity>()

                repeat(3) {
                    val decodeOnly = measureDecodeOnly("$CORPUS_DIR/$name", priority)
                    val audioSec = decodeOnly.audioSeconds
                    decodeMsPerSec += decodeOnly.wallMs / audioSec
                    decodeCpuMsPerSec += decodeOnly.cpuMs / audioSec
                    decodeAffinities += decodeOnly.affinity

                    val audio = decodeToMemory("$CORPUS_DIR/$name", priority)
                    val meterOnly = measureMeterOnly(audio, priority)
                    meterWallMsPerSec += meterOnly.wallMs / audio.audioSeconds
                    meterThreadMsPerSec += meterOnly.threadCpuMs / audio.audioSeconds
                    meterAffinities += meterOnly.affinity

                    val combined = measureDecodeAndMeter("$CORPUS_DIR/$name", priority)
                    combinedMsPerSec += combined.wallMs / combined.audioSeconds
                    combinedAffinities += combined.affinity
                }

                report(
                    "speed",
                    name,
                    "priority=$priority",
                    "decodeWallMsPerSec=${median(decodeMsPerSec)}",
                    "decodeCpuMsPerSec=${median(decodeCpuMsPerSec)}",
                    "meterWallMsPerSec=${median(meterWallMsPerSec)}",
                    "meterThreadMsPerSec=${median(meterThreadMsPerSec)}",
                    "combinedWallMsPerSec=${median(combinedMsPerSec)}",
                    "decodeCpuset=${decodeAffinities.joinToString("|") { it.cpuset }}",
                    "decodeCpus=${decodeAffinities.joinToString("|") { it.cpuNumbers.toSortedSet().joinToString(",") }}",
                    "decodePriority=${decodeAffinities.joinToString("|") { it.priority.toString() }}",
                    "meterCpuset=${meterAffinities.joinToString("|") { it.cpuset }}",
                    "meterCpus=${meterAffinities.joinToString("|") { it.cpuNumbers.toSortedSet().joinToString(",") }}",
                    "meterPriority=${meterAffinities.joinToString("|") { it.priority.toString() }}",
                    "combinedCpuset=${combinedAffinities.joinToString("|") { it.cpuset }}",
                    "combinedCpus=${combinedAffinities.joinToString("|") { it.cpuNumbers.toSortedSet().joinToString(",") }}",
                    "combinedPriority=${combinedAffinities.joinToString("|") { it.priority.toString() }}",
                )
            }
        }
    }

    @Test(timeout = 120_000)
    fun cancelStopsWithinOneSecond() {
        val file = corpusFileOrSkip("flac_24_192.flac")
        val decoder = OfflineDecoder(context())
        val flipAt = SystemClock.elapsedRealtime() + 200
        val outcome = decoder.decode(
            file.absolutePath,
            null,
            { _, _, _, _ -> },
            isCancelled = { SystemClock.elapsedRealtime() >= flipAt },
        )
        val elapsedAfterFlip = SystemClock.elapsedRealtime() - flipAt
        assertTrue("expected Cancelled but got $outcome", outcome is DecodeOutcome.Cancelled)
        assertTrue("cancel took ${elapsedAfterFlip} ms after flip", elapsedAfterFlip <= 1_000)

        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline && decoder.decodeThreads.any { it.isAlive }) {
            SystemClock.sleep(50)
        }
        assertTrue("decode threads still alive after cancel", decoder.decodeThreads.none { it.isAlive })
    }

    @Test(timeout = 300_000)
    fun longFileDecodesCompletely() {
        val dir = context().cacheDir
        val (file16, directLufs16) = writeAndMeterSineWav(File(dir, "long16.wav"), 48_000, 2, 300, 997.0, 0.1, 16)
        val (file24, _) = writeAndMeterSineWav(File(dir, "long24.wav"), 96_000, 2, 120, 997.0, 0.1, 24)
        try {
            val decoded16 = decodeToMemory(file16.absolutePath, Process.THREAD_PRIORITY_BACKGROUND)
            assertEquals("48 kHz decoded frame count", 300 * 48_000, decoded16.frames)

            val decodedMeter = LoudnessMeter(decoded16.rate, decoded16.channels)
            processAll(decodedMeter, decoded16)
            val decodedLufs = decodedMeter.integratedLufs()
            assertNotNull("direct 48 kHz loudness should be non-null", directLufs16)
            assertNotNull("decoded 48 kHz loudness should be non-null", decodedLufs)
            assertTrue(
                "decoded meter $decodedLufs should match direct meter $directLufs16",
                abs(decodedLufs!! - directLufs16!!) < 0.001,
            )

            val decoded24 = decodeToMemory(file24.absolutePath, Process.THREAD_PRIORITY_BACKGROUND)
            assertEquals("96 kHz decoded frame count", 120 * 96_000, decoded24.frames)
        } finally {
            file16.delete()
            file24.delete()
        }
    }

    // ---------------------------------------------------------------------
    // Decoding helpers
    // ---------------------------------------------------------------------

    private data class Audio(val samples: FloatArray, val rate: Int, val channels: Int, val encoding: Int = C.ENCODING_PCM_FLOAT) {
        val frames: Int get() = samples.size / channels
        val audioSeconds: Double get() = frames.toDouble() / rate
    }

    private fun decodeToMemory(path: String, priority: Int): Audio {
        val chunks = ArrayList<FloatArray>()
        var rate = 0
        var channels = 0
        val decoder = OfflineDecoder(context(), threadPriority = priority)
        val outcome = decoder.decode(path, null, consumer = { samples, frames, r, c ->
            rate = r
            channels = c
            chunks += samples.copyOf(frames * c)
        })
        assertTrue("decode failed for $path: $outcome", outcome is DecodeOutcome.Completed)
        val total = chunks.sumOf { it.size }
        val all = FloatArray(total)
        var at = 0
        for (chunk in chunks) {
            chunk.copyInto(all, at)
            at += chunk.size
        }
        return Audio(all, rate, channels)
    }

    private fun decodeAndMeter(path: String): Pair<DecodeOutcome, LoudnessMeter?> {
        val meterRef = AtomicReference<LoudnessMeter?>()
        val decoder = OfflineDecoder(context())
        val outcome = decoder.decode(path, null, consumer = { samples, frames, rate, channels ->
            val meter = meterRef.get() ?: LoudnessMeter(rate, channels).also { meterRef.set(it) }
            meter.process(samples, 0, frames)
        })
        val meter = meterRef.get()
        meter?.flush()
        return outcome to meter
    }

    private data class DecodeMeasure(val wallMs: Long, val cpuMs: Long, val audioSeconds: Double, val affinity: Affinity)

    private fun measureDecodeOnly(path: String, priority: Int): DecodeMeasure {
        val decoder = OfflineDecoder(context(), threadPriority = priority)
        val outcomeRef = AtomicReference<DecodeOutcome>()
        val framesRef = AtomicLong(0)
        val rateRef = AtomicInteger(0)
        val alive = AtomicBoolean(true)
        val wallStart = SystemClock.elapsedRealtime()
        val cpuStart = Process.getElapsedCpuTime()
        val worker = Thread({
            outcomeRef.set(decoder.decode(path, null, consumer = { _, frames, rate, _ ->
                framesRef.addAndGet(frames.toLong())
                rateRef.set(rate)
            }))
            alive.set(false)
        }, "sistrum-decode-worker").apply { start() }
        val affinity = sampleAffinity({ decoder.decodeThreads.getOrNull(1)?.threadId }, { alive.get() })
        worker.join()
        val wallMs = SystemClock.elapsedRealtime() - wallStart
        val cpuMs = Process.getElapsedCpuTime() - cpuStart
        assertTrue("decode failed for $path: ${outcomeRef.get()}", outcomeRef.get() is DecodeOutcome.Completed)
        val rate = rateRef.get().coerceAtLeast(1)
        return DecodeMeasure(wallMs, cpuMs, framesRef.get().toDouble() / rate, affinity)
    }

    private data class MeterMeasure(val wallMs: Long, val threadCpuMs: Long, val affinity: Affinity)

    private fun measureMeterOnly(audio: Audio, priority: Int): MeterMeasure {
        val meter = LoudnessMeter(audio.rate, audio.channels)
        val tidRef = AtomicInteger(-1)
        val alive = AtomicBoolean(true)
        val wallRef = AtomicLong(0)
        val threadRef = AtomicLong(0)
        val thread = Thread({
            Process.setThreadPriority(priority)
            tidRef.set(Process.myTid())
            val wallStart = SystemClock.elapsedRealtime()
            val threadStart = SystemClock.currentThreadTimeMillis()
            processAll(meter, audio)
            wallRef.set(SystemClock.elapsedRealtime() - wallStart)
            threadRef.set(SystemClock.currentThreadTimeMillis() - threadStart)
            alive.set(false)
        }, "sistrum-meter-bench").apply { start() }
        val affinity = sampleAffinity({ tidRef.get().takeIf { it > 0 } }, { alive.get() })
        thread.join()
        return MeterMeasure(wallRef.get(), threadRef.get(), affinity)
    }

    private data class CombinedMeasure(val wallMs: Long, val audioSeconds: Double, val affinity: Affinity)

    private fun measureDecodeAndMeter(path: String, priority: Int): CombinedMeasure {
        val decoder = OfflineDecoder(context(), threadPriority = priority)
        val meterRef = AtomicReference<LoudnessMeter?>()
        val framesRef = AtomicLong(0)
        val rateRef = AtomicInteger(0)
        val alive = AtomicBoolean(true)
        val wallStart = SystemClock.elapsedRealtime()
        val worker = Thread({
            decoder.decode(path, null, consumer = { samples, frames, rate, channels ->
                framesRef.addAndGet(frames.toLong())
                rateRef.set(rate)
                val meter = meterRef.get() ?: LoudnessMeter(rate, channels).also { meterRef.set(it) }
                meter.process(samples, 0, frames)
            })
            meterRef.get()?.flush()
            alive.set(false)
        }, "sistrum-combined-worker").apply { start() }
        val affinity = sampleAffinity({ decoder.decodeThreads.getOrNull(1)?.threadId }, { alive.get() })
        worker.join()
        val wallMs = SystemClock.elapsedRealtime() - wallStart
        val rate = rateRef.get().coerceAtLeast(1)
        return CombinedMeasure(wallMs, framesRef.get().toDouble() / rate, affinity)
    }

    private fun processAll(meter: LoudnessMeter, audio: Audio) {
        val chunk = 4800
        var frame = 0
        while (frame < audio.frames) {
            val take = minOf(chunk, audio.frames - frame)
            meter.process(audio.samples, frame, take)
            frame += take
        }
        meter.flush()
    }

    // ---------------------------------------------------------------------
    // Synthetic WAV writing (longFileDecodesCompletely)
    // ---------------------------------------------------------------------

    /** Writes a sine PCM WAV and returns the file plus the integrated loudness of the raw samples. */
    private fun writeAndMeterSineWav(
        file: File,
        rate: Int,
        channels: Int,
        seconds: Int,
        freq: Double,
        amplitude: Double,
        bits: Int,
    ): Pair<File, Double?> {
        val frames = rate * seconds
        val samples = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val v = (amplitude * sin(2.0 * PI * freq * f / rate)).toFloat()
            for (c in 0 until channels) samples[f * channels + c] = v
        }
        val meter = LoudnessMeter(rate, channels)
        processAll(meter, Audio(samples, rate, channels))
        writePcmWav(file, samples, rate, channels, bits)
        return file to meter.integratedLufs()
    }

    /** Writes interleaved [-1, 1] samples as a 16- or 24-bit little-endian PCM WAV. */
    private fun writePcmWav(file: File, samples: FloatArray, rate: Int, channels: Int, bits: Int) {
        val bytesPerSample = bits / 8
        val blockAlign = channels * bytesPerSample
        val dataSize = samples.size * bytesPerSample
        val out = ByteArrayOutputStream(44 + dataSize)
        fun le16(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }
        fun le32(v: Int) {
            le16(v and 0xFFFF)
            le16((v shr 16) and 0xFFFF)
        }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        le32(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        le32(16)
        le16(1)
        le16(channels)
        le32(rate)
        le32(rate * blockAlign)
        le16(blockAlign)
        le16(bits)
        out.write("data".toByteArray(Charsets.US_ASCII))
        le32(dataSize)
        if (bits == 16) {
            for (i in samples.indices) {
                val v = (samples[i] * 32767.0).roundToInt().coerceIn(-32768, 32767)
                le16(v)
            }
        } else {
            for (i in samples.indices) {
                val v = (samples[i] * 8388607.0).roundToInt().coerceIn(-8388608, 8388607)
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
                out.write((v shr 16) and 0xFF)
            }
        }
        file.writeBytes(out.toByteArray())
    }

    // ---------------------------------------------------------------------
    // Playback capture (mirrors Media3FloatPathTest)
    // ---------------------------------------------------------------------

    private fun capturePlayback(file: File): Audio {
        val recorder = TapRecorder()
        val factory = Media3PlayerFactory(context(), dspChainEnabled = false) {
            arrayOf<AudioProcessor>(TeeAudioProcessor(recorder))
        }
        val engine = Media3Engine(factory)
        try {
            runBlocking { engine.prepare(playbackItem(file.absolutePath), ItemGain.Unity, 0L, startPaused = false) }
            engine.setFocusGain(0f)
            assertTrue("output should start for ${file.name}", awaitOutputStarted(engine, 30_000L))
            SystemClock.sleep(3_200L)
            engine.pause()
            return recorder.toAudio()
        } finally {
            engine.close()
            assertEquals("live players leaked", 0, factory.livePlayers)
            assertEquals("live dsp chains leaked", 0, factory.liveDspChains)
        }
    }

    private fun playbackItem(path: String) = PlaybackItem(
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

    /** Records every buffer the [TeeAudioProcessor] receives, as in Media3FloatPathTest. */
    private class TapRecorder : TeeAudioProcessor.AudioBufferSink {
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

        fun toAudio(): Audio {
            val raw = data.toByteArray()
            val samples = when (encoding) {
                C.ENCODING_PCM_FLOAT -> floatSamples(raw)
                C.ENCODING_PCM_16BIT -> intSamples(raw, 2, 15)
                C.ENCODING_PCM_24BIT -> intSamples(raw, 3, 23)
                C.ENCODING_PCM_32BIT -> intSamples(raw, 4, 31)
                else -> error("unexpected tap encoding $encoding")
            }
            return Audio(samples, sampleRate, channelCount, encoding)
        }

        private fun floatSamples(raw: ByteArray): FloatArray {
            val count = raw.size / 4
            val out = FloatArray(count)
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) out[i] = buffer.float
            return out
        }

        private fun intSamples(raw: ByteArray, bytesPerSample: Int, shift: Int): FloatArray {
            val count = raw.size / bytesPerSample
            val out = FloatArray(count)
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val factor = 1.0 / (1L shl shift)
            for (i in 0 until count) {
                var v = 0L
                for (b in 0 until bytesPerSample) {
                    v = v or ((buffer.get().toLong() and 0xFF) shl (8 * b))
                }
                if ((v and (1L shl (bytesPerSample * 8 - 1))) != 0L) {
                    v = v or (-1L shl (bytesPerSample * 8))
                }
                out[i] = (v.toDouble() * factor).toFloat()
            }
            return out
        }
    }

    // ---------------------------------------------------------------------
    // Affinity / CPU sampling
    // ---------------------------------------------------------------------

    private data class Affinity(val cpuset: String, val cpuNumbers: Set<Int>, val priority: Int)

    private fun sampleAffinity(tidProvider: () -> Int?, isRunning: () -> Boolean): Affinity {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var tid = -1
        while (tid <= 0 && isRunning() && SystemClock.elapsedRealtime() < deadline) {
            tid = tidProvider() ?: -1
            if (tid <= 0) SystemClock.sleep(5)
        }
        if (tid <= 0) return Affinity("?", emptySet(), -1)
        val cpuset = readCpuset(tid)
        val priority = readThreadPriority(tid)
        val cpus = mutableSetOf<Int>()
        while (isRunning()) {
            readCpuNumber(tid)?.let { cpus.add(it) }
            SystemClock.sleep(50)
        }
        return Affinity(cpuset, cpus, priority)
    }

    private fun readThreadPriority(tid: Int): Int =
        try {
            Process.getThreadPriority(tid)
        } catch (e: Exception) {
            -1
        }

    private fun readCpuset(tid: Int): String =
        try {
            File("/proc/self/task/$tid/cpuset").readText().trim()
        } catch (e: Exception) {
            "?"
        }

    private fun readCpuNumber(tid: Int): Int? =
        try {
            val stat = File("/proc/self/task/$tid/stat").readText()
            val closeParen = stat.lastIndexOf(')')
            val fields = stat.substring(closeParen + 2).split(' ').filter { it.isNotBlank() }
            if (fields.size <= 36) null else fields[36].toInt()
        } catch (e: Exception) {
            null
        }

    // ---------------------------------------------------------------------
    // Oracle fixtures
    // ---------------------------------------------------------------------

    private data class OracleRow(
        val file: String,
        val status: String,
        val integratedLufs: Double?,
        val truePeakDbtp: Double?,
    )

    private fun readOracleRows(): List<OracleRow> {
        val text = InstrumentationRegistry.getInstrumentation().context.assets
            .open("oracle-fixtures.tsv")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return parseFixtures(text)
    }

    private fun parseFixtures(text: String): List<OracleRow> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val headerLine = lines.firstOrNull { !it.startsWith("#") } ?: return emptyList()
        val header = headerLine.split('\t')
        val rows = mutableListOf<OracleRow>()
        for (line in lines) {
            if (line.startsWith("#") || line == headerLine) continue
            val cells = line.split('\t')
            fun cell(name: String): String? {
                val index = header.indexOf(name)
                return if (index in cells.indices) cells[index].ifBlank { null } else null
            }
            val file = cell("file") ?: continue
            rows += OracleRow(
                file = file,
                status = cell("status") ?: "",
                integratedLufs = parseLoudness(cell("integrated_lufs")),
                truePeakDbtp = parseLoudness(cell("true_peak_dbtp")),
            )
        }
        return rows
    }

    private fun parseLoudness(value: String?): Double? {
        if (value == null) return null
        return when (value.trim()) {
            "-Inf" -> Double.NEGATIVE_INFINITY
            "+Inf", "Inf" -> Double.POSITIVE_INFINITY
            "NaN" -> Double.NaN
            else -> value.trim().toDoubleOrNull()
        }
    }

    private fun isLossless(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".aiff") ||
            lower.endsWith(".aifc") || lower.endsWith(".wv") || (lower.endsWith(".m4a") && "alac" in lower)
    }

    // ---------------------------------------------------------------------
    // Reporting / device info
    // ---------------------------------------------------------------------

    private fun reportDeviceInfo() {
        val ctx = context()
        val battery = try {
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = if (temp == Int.MIN_VALUE) "?" else (temp / 10.0).toString()
        report("device", "model=${Build.MODEL}", "sdk=${Build.VERSION.SDK_INT}", "batteryStatus=$status", "temperatureC=$tempC")
    }

    private fun report(vararg fields: String) {
        val line = fields.joinToString("\t")
        Log.i(TAG, line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("bench", line) })
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun encodingName(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_FLOAT -> "float"
        C.ENCODING_PCM_16BIT -> "16bit"
        C.ENCODING_PCM_24BIT -> "24bit"
        C.ENCODING_PCM_32BIT -> "32bit"
        else -> "encoding$encoding"
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun corpusFileOrSkip(name: String): File {
        val file = File(CORPUS_DIR, name)
        assumeTrue("missing corpus file: $name", file.isFile)
        return file
    }

    private companion object {
        const val TAG = "OfflineDecodeBench"
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
    }
}
