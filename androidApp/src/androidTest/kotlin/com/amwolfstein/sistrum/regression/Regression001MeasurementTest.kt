package com.amwolfstein.sistrum.regression

import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.engine.EngineEvent
import com.amwolfstein.sistrum.player.engine.ItemGain
import com.amwolfstein.sistrum.player.engine.LegacyNativeEngine
import com.amwolfstein.sistrum.player.engine.PlayerEngine
import com.amwolfstein.sistrum.player.media3.Media3Engine
import com.amwolfstein.sistrum.player.media3.Media3PlayerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** T062 part A. Measurements only, except SC-014 attribution assertions.
 * Run each method separately on .qa. Expected mid-range-device times: SC-006 ~1–2 min
 * (72 starts, bounded to 6 s each: ~8 min worst case), SC-003 ~30 s (four 15 s
 * capture windows, <2 min including prepares), SC-014 ~1–2 min (49 * (10 s prepare
 * timeout + 1 s playback), <10 min), CPU ~6–7 min (six 60 s windows plus setup).
 * This build is debuggable: CPU figures are relative only; release CPU is part B.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Regression001MeasurementTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 590_000)
    fun sc006StartLatencyNativeVsMedia3() {
        val corpus = guard()
        val opus = corpus.filter {
            (it.name.startsWith("opus_") && it.extension == "opus") ||
                (it.name.startsWith("ogg_") && it.extension == "ogg")
        }
        assumeTrue("missing OPUS/OGG fixtures", opus.isNotEmpty())
        val heavy = fixtures(
            "flac_untagged.flac", "flac_24_96.flac", "m4a_alac_untagged.m4a",
            "wav_pcm16_48.wav", "mp3_untagged.mp3", "m4a_aac_untagged.m4a",
        )
        for ((mix, files) in listOf("opus" to opus, "lossless_mp3_aac" to heavy)) {
            val native = mutableListOf<Pair<Double, Double>>()
            val media3 = mutableListOf<Pair<Double, Double>>()
            for (file in files) {
                fun measure(isNative: Boolean): Pair<Double, Double> {
                    val runs = List(3) { run ->
                        var eventLatency = Double.NaN
                        var positionLatency = Double.NaN
                        withEngine(isNative) { engine, _ ->
                            engine.setFocusGain(0f)
                            try {
                                runBlocking {
                                    withTimeout(6_000L) {
                                        // Native's OutputStarted is the seam's immediate successful-start
                                        // notification; Media3's is rendered-position advance (FR-090).
                                        // prepare() suspends: both clocks include the entire prepare call.
                                        val start = SystemClock.elapsedRealtimeNanos()
                                        engine.prepare(item(file), ItemGain.Unity, 0L, startPaused = false)
                                        val deadline = start + 6_000_000_000L
                                        while (SystemClock.elapsedRealtimeNanos() < deadline) {
                                            val events = engine.pollEvents()
                                            if (events.any { it is EngineEvent.Error }) {
                                                eventLatency = Double.NaN
                                                positionLatency = Double.NaN
                                                break
                                            }
                                            val eventAt = SystemClock.elapsedRealtimeNanos()
                                            if (eventAt >= deadline) break
                                            if (eventLatency.isNaN() && events.any { it is EngineEvent.OutputStarted }) {
                                                eventLatency = (eventAt - start) / 1e6
                                            }
                                            val position = engine.positionMs()
                                            val positionAt = SystemClock.elapsedRealtimeNanos()
                                            if (positionAt >= deadline) break
                                            if (positionLatency.isNaN() && position > 0L) {
                                                positionLatency = (positionAt - start) / 1e6
                                            }
                                            if (eventLatency.isFinite() && positionLatency.isFinite()) break
                                            SystemClock.sleep(5L)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                eventLatency = Double.NaN
                                positionLatency = Double.NaN
                                row("sc006 mix=$mix file=${token(file.name)} engine=${if (isNative) "native" else "media3"} " +
                                    "run=${run + 1} result=FAIL cause=${token(e.javaClass.name)}")
                            }
                        }
                        SystemClock.sleep(300L)
                        eventLatency to positionLatency
                    }
                    return median(runs.map { it.first }) to median(runs.map { it.second })
                }
                val n = measure(true)
                val m = measure(false)
                native += n
                media3 += m
                row("sc006 mix=$mix file=${token(file.name)} native_event_ms=${number(n.first)} " +
                    "media3_event_ms=${number(m.first)} native_pos_ms=${number(n.second)} media3_pos_ms=${number(m.second)}")
            }
            val nEvent = median(native.map { it.first })
            val mEvent = median(media3.map { it.first })
            val nPosition = median(native.map { it.second })
            val mPosition = median(media3.map { it.second })
            row("sc006 mix=$mix native_event_ms=${number(nEvent)} media3_event_ms=${number(mEvent)} " +
                "native_pos_ms=${number(nPosition)} media3_pos_ms=${number(mPosition)} " +
                "ratio_event=${number(mEvent / nEvent)} ratio_pos=${number(mPosition / nPosition)}")
        }
    }

    @Test(timeout = 590_000)
    fun sc003GaplessSilencePerPair() {
        guard()
        val files = fixtures("gapless_flac_1.flac", "gapless_flac_2.flac", "gapless_flac_3.flac",
            "gapless_mp3_1.mp3", "gapless_mp3_2.mp3", "gapless_mp3_3.mp3")
        for ((a, b) in listOf(files[0] to files[1], files[1] to files[2], files[3] to files[4], files[4] to files[5])) {
            val taps = mutableListOf<JoinTap>()
            val factory = Media3PlayerFactory(context, afterGainProcessors = {
                arrayOf(TeeAudioProcessor(JoinTap().also { taps += it }))
            })
            val engine = Media3Engine(factory)
            try {
                engine.setFocusGain(0f)
                runBlocking { engine.prepare(item(a), ItemGain.Unity, 0L, startPaused = true) }
                val duration = engine.durationMs()
                val origin = (duration - 3_000L).coerceAtLeast(0L)
                // A fresh player/tap at the seek position excludes PCM decoded by the duration probe.
                runBlocking { engine.prepare(item(a), ItemGain.Unity, origin, startPaused = true) }
                val tap = factory.call { taps.last() }
                val boundaryMs = duration - origin
                tap.setWindow(boundaryMs)
                val formats = linkedMapOf<String, Pair<Int, Int>>()
                fun readFormat() = factory.call {
                    engine.playersForTest().forEach { player ->
                        val id = player.currentMediaItem?.mediaId
                        val format = player.audioFormat
                        if (id != null && format != null) {
                            formats[id] = format.encoderDelay to format.encoderPadding
                        }
                    }
                }
                readFormat()
                runBlocking { engine.preloadNext(item(b), ItemGain.Unity) }
                engine.play()
                var advanced = false
                var errors = 0
                val deadline = SystemClock.elapsedRealtime() + 15_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    readFormat()
                    val events = engine.pollEvents()
                    advanced = advanced || events.any { it is EngineEvent.GaplessAdvanced }
                    errors += events.count { it is EngineEvent.Error }
                    if (events.any { it is EngineEvent.Ended } || errors > 0 ||
                        (advanced && engine.positionMs() >= 1_500L)) break
                    SystemClock.sleep(5L)
                }
                val measurement = tap.snapshot()
                row("sc003 pair=${token(a.name)}->${token(b.name)} frames=${measurement.longest} " +
                    "ms=${number(measurement.longest * 1000.0 / measurement.rate)} " +
                    "output_rate_hz=${measurement.rate} captured_frames=${measurement.frames} " +
                    "advanced=$advanced errors=$errors window_complete=${measurement.complete}")
                if (a.extension == "mp3") {
                    for (file in listOf(a, b)) {
                        val format = formats[file.absolutePath]
                        row("sc003 pair=${token(a.name)}->${token(b.name)} file=${token(file.name)} " +
                            "encoder_delay=${format?.first ?: "unavailable"} encoder_padding=${format?.second ?: "unavailable"}")
                    }
                }
            } finally {
                engine.close()
            }
        }
    }

    @Test(timeout = 590_000)
    fun sc014EveryCorpusFileFailureIsAttributed() {
        val files = guard()
        val unattributed = mutableListOf<String>()
        for (file in files) {
            withEngine(false) { engine, factory ->
                engine.setFocusGain(0f)
                var prepareException: Exception? = null
                val errors = mutableListOf<EngineEvent.Error>()
                try {
                    runBlocking { engine.prepare(item(file), ItemGain.Unity, 0L, startPaused = true) }
                    engine.play()
                    val deadline = SystemClock.elapsedRealtime() + 1_000L
                    while (SystemClock.elapsedRealtime() < deadline) {
                        errors += engine.pollEvents().filterIsInstance<EngineEvent.Error>()
                        SystemClock.sleep(5L)
                    }
                } catch (e: Exception) {
                    prepareException = e
                }
                errors += engine.pollEvents().filterIsInstance<EngineEvent.Error>()
                val failures = errors.map { Failure(it.provider, it.format, it.cause, "event") }.toMutableList()
                prepareException?.let { e ->
                    // prepare() releases its player before rethrowing, and emits no Error when not
                    // yet prepared. Use the accepting extractor's recorded provider, never invent
                    // one. Extension is the engine's own format fallback when no Format exists.
                    failures += Failure(factory!!.providerRecorder.providerFor(Uri.fromFile(file).toString()).orEmpty(),
                        file.extension, e, "prepare")
                }
                if (failures.isEmpty()) {
                    val provider = factory!!.providerRecorder.providerFor(Uri.fromFile(file).toString()) ?: "none"
                    val format = factory.call {
                        (engine as Media3Engine).playersForTest().firstOrNull()?.audioFormat?.sampleMimeType
                    } ?: file.extension
                    row("sc014 file=${token(file.name)} result=OK provider=${token(provider)} format=${token(format)} cause=none")
                } else {
                    // One row per file; comma-separated fields preserve every failure, in order.
                    row("sc014 file=${token(file.name)} result=FAIL " +
                        "provider=${failures.joinToString(",") { token(it.provider) }} " +
                        "format=${failures.joinToString(",") { token(it.format) }} " +
                        "cause=${failures.joinToString(",") { token(it.cause.javaClass.name) }} " +
                        "source=${failures.joinToString(",") { it.source }} failures=${failures.size}")
                    if (failures.any { it.provider.isBlank() || it.format.isBlank() }) {
                        unattributed += file.name
                    }
                }
            }
        }
        // Delay this single required assertion until every corpus file has a measurement row.
        assertTrue("SC-014 failures lack provider/format: $unattributed", unattributed.isEmpty())
    }

    @Test(timeout = 590_000)
    fun cpuPerEngineAndCrossfadeMode() {
        guard()
        val files = fixtures("gapless_flac_1.flac", "gapless_flac_2.flac")
        val ticksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK)
        repeat(2) { repetition ->
            for (mode in listOf("native", "media3_off", "media3_on")) {
                withEngine(mode == "native") { engine, _ ->
                    engine.setFocusGain(0f)
                    val fadeMs = if (mode == "media3_on") 4_000L else 0L
                    engine.setCrossfade(fadeMs, true)
                    runBlocking { engine.prepare(item(files[0]), ItemGain.Unity, 0L, startPaused = true) }
                    // Use the same final 10 seconds in all modes; repeat after each advance. This
                    // bounds the cycle even for long fixtures and schedules >=2 automatic fades.
                    engine.seekTo((engine.durationMs() - 10_000L).coerceAtLeast(0L))
                    runBlocking { engine.preloadNext(item(files[1]), ItemGain.Unity) }
                    val pssBefore = Debug.getPss()
                    val cpuBefore = processTicks()
                    val start = SystemClock.elapsedRealtime()
                    engine.play()
                    var current = 0
                    var reposition = false
                    var fades = 0
                    var advances = 0
                    var errors = 0
                    while (SystemClock.elapsedRealtime() - start < 60_000L) {
                        for (event in engine.pollEvents()) {
                            when (event) {
                                is EngineEvent.TransitionStarted -> { fades++; current = 1 - current; reposition = true }
                                is EngineEvent.GaplessAdvanced -> { advances++; current = 1 - current; reposition = true }
                                is EngineEvent.Error -> errors++
                                else -> Unit
                            }
                        }
                        if (reposition && !engine.isCrossfading()) {
                            engine.seekTo((engine.durationMs() - 10_000L).coerceAtLeast(0L))
                            runBlocking { engine.preloadNext(item(files[1 - current]), ItemGain.Unity) }
                            reposition = false
                        }
                        if (fadeMs > 0L && !reposition && !engine.isCrossfading() && engine.hasPreloaded() &&
                            engine.durationMs() - engine.positionMs() <= fadeMs + engine.fadeLeadMs()) {
                            engine.beginCrossfade(fadeMs)
                        }
                        SystemClock.sleep(20L)
                    }
                    val wallMs = SystemClock.elapsedRealtime() - start
                    val cpuMs = (processTicks() - cpuBefore) * 1000.0 / ticksPerSecond
                    val pssAfter = Debug.getPss()
                    row("cpu mode=$mode repeat=${repetition + 1} cpuMs=${number(cpuMs)} " +
                        "cpuPercentOfWall=${number(cpuMs * 100.0 / wallMs)} pssKb=$pssAfter " +
                        "pssBeforeKb=$pssBefore pssDeltaKb=${pssAfter - pssBefore} wallMs=$wallMs " +
                        "fades=$fades advances=$advances errors=$errors")
                }
            }
        }
    }

    private fun guard(): List<File> {
        check(context.packageName.endsWith(".qa")) { "Regression001MeasurementTest requires the .qa target" }
        val files = File(CORPUS).listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        assumeTrue("test corpus missing: $CORPUS", files.isNotEmpty())
        return files
    }

    private fun fixtures(vararg names: String): List<File> = names.map { name ->
        File(CORPUS, name).also { assumeTrue("missing corpus fixture: $name", it.isFile) }
    }

    private fun withEngine(native: Boolean, body: (PlayerEngine, Media3PlayerFactory?) -> Unit) {
        val factory = if (native) null else Media3PlayerFactory(context)
        val engine: PlayerEngine = if (native) LegacyNativeEngine() else Media3Engine(factory!!)
        try {
            body(engine, factory)
        } finally {
            // Factory is not Closeable and owns no separate player lifetime: engine.close()
            // releases all players/chains/limiter sessions created through it.
            engine.close()
        }
    }

    private fun item(file: File) = PlaybackItem(
        trackId = file.absolutePath, title = file.name, artist = "T062", audioPath = file.absolutePath,
    )

    private fun row(fields: String) {
        val line = "T062 $fields"
        Log.i("T062", line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("T062", line) })
    }

    private fun token(value: String) = Uri.encode(value).ifEmpty { "missing" }
    private fun number(value: Double) = String.format(Locale.US, "%.3f", value)
    // A failed start invalidates its aggregate rather than silently dropping it from the median.
    private fun median(values: List<Double>): Double {
        if (values.isEmpty() || values.any { !it.isFinite() }) return Double.NaN
        val sorted = values.sorted()
        return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2.0
    }

    private fun processTicks(): Long {
        // comm (field 2) may contain spaces/parentheses. After its final ')' field 3 is index 0;
        // utime/stime are fields 14/15, hence indices 11/12. Include all process threads.
        val fields = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(Regex("\\s+"))
        return fields[11].toLong() + fields[12].toLong()
    }

    private data class Failure(val provider: String, val format: String, val cause: Throwable, val source: String)
    private data class Silence(val longest: Long, val rate: Int, val frames: Long, val complete: Boolean)

    /** Counts frames online in the +/-1 s join window, with bounded memory. The gain tap
     * precedes focus volume. Synchronized snapshots avoid racing the renderer thread.
     * Corpus joins are 48 kHz; report the actual rate too so an unexpected output is visible.
     */
    private class JoinTap : TeeAudioProcessor.AudioBufferSink {
        private var rate = 0
        private var channels = 0
        private var encoding = C.ENCODING_INVALID
        private var frames = 0L
        private var run = 0L
        private var longest = 0L
        private var boundaryMs = 3_000L

        @Synchronized fun setWindow(ms: Long) { boundaryMs = ms }

        @Synchronized override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            rate = sampleRateHz
            channels = channelCount
            this.encoding = encoding
        }

        @Synchronized override fun handleBuffer(buffer: ByteBuffer) {
            val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val bytes = when (encoding) {
                C.ENCODING_PCM_FLOAT -> 4
                C.ENCODING_PCM_16BIT -> 2
                else -> error("unsupported T062 tap encoding: $encoding")
            }
            if (channels <= 0) return
            val from = ((boundaryMs - 1_000L).coerceAtLeast(0L) * rate) / 1000L
            val to = (boundaryMs + 1_000L) * rate / 1000L
            while (pcm.remaining() >= bytes * channels) {
                var silent = true
                repeat(channels) {
                    val sample = if (bytes == 4) pcm.float else pcm.short / 32768f
                    if (!(abs(sample) < 1e-4f)) silent = false
                }
                if (frames in from until to) {
                    run = if (silent) run + 1 else 0
                    longest = maxOf(longest, run)
                }
                frames++
            }
        }

        @Synchronized fun snapshot() = Silence(longest, rate, frames,
            rate > 0 && frames >= (boundaryMs + 1_000L) * rate / 1000L)
    }

    private companion object { const val CORPUS = "/sdcard/Music/SistrumTestCorpus" }
}
