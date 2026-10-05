package me.misa198.airmedy.spikes

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spike S2 test code for specs/001-media3-migration (ADR-004), not app code.
 *
 * Drives two [ExoPlayer]s on one [HandlerThread] sharing a single audio session, with a
 * limiter-only [DynamicsProcessing] attached to that session, and measures the output-mix peak/RMS
 * with [Visualizer]. Also captures `dumpsys media.audio_flinger` to inspect routing. Every
 * measurement is best effort: unavailable measurements are recorded in the result, never thrown.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SharedSessionLimiterSpikeTest {

    private companion object {
        const val TAG = "S2"
        const val OUTPUT_MIX_SESSION = 0
        const val DP_UUID = "e0e6539b-1781-7261-676f-6d7573696340"
        const val SETTLE_MS = 1_000L
        const val WINDOW_MS = 3_000L
        const val POLL_MS = 50L
        const val TONE_SECONDS = 15
        const val HIRES_SECONDS = 10
        const val LOUD_AMPLITUDE = 0.708
        const val QUIET_AMPLITUDE = 0.354
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var dp: DynamicsProcessing? = null
    private var visualizer: Visualizer? = null

    private var audioSessionId = 0
    private var frameMs = 10f
    private var dpAvailable = false
    private var dpError: String? = null
    private var visualizerStatus = "unavailable"
    private var visualizerSession = -1
    private var playerASession = -1
    private var playerBSession = -1

    private val phaseResults = linkedMapOf<String, Measurement>()
    private val captures = linkedMapOf<String, String?>()

    private data class Measurement(val peakMb: Long?, val rmsMb: Long?, val windowMs: Long)

    private data class DumpsysExtract(val threads: String, val effect: String)

    @Test(timeout = 180_000)
    fun sharedSessionLimiterMeasurements() {
        val loud1k = File(context.cacheDir, "s2_1k_loud.wav")
        val loud1500 = File(context.cacheDir, "s2_1500_loud.wav")
        val quiet1k = File(context.cacheDir, "s2_1k_quiet.wav")
        val quiet1500 = File(context.cacheDir, "s2_1500_quiet.wav")
        val hires = File(context.cacheDir, "s2_hires.wav")
        writeSineWav(loud1k, 1_000.0, LOUD_AMPLITUDE)
        writeSineWav(loud1500, 1_500.0, LOUD_AMPLITUDE)
        writeSineWav(quiet1k, 1_000.0, QUIET_AMPLITUDE)
        writeSineWav(quiet1500, 1_500.0, QUIET_AMPLITUDE)
        writeHiresWav(hires)

        thread = HandlerThread("s2-player").apply { start() }
        handler = Handler(thread.looper)

        var thrown: Throwable? = null
        try {
            val audioManager = context.getSystemService(AudioManager::class.java)
            audioSessionId = audioManager.generateAudioSessionId()
            frameMs = computeFrameMs(audioManager)
            Log.i(TAG, "sessionId=$audioSessionId frameMs=$frameMs")

            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            playerA = call { buildPlayer(attributes, "playerA") }
            playerB = call { buildPlayer(attributes, "playerB") }

            preparePlayer(playerA!!, loud1k)
            preparePlayer(playerB!!, loud1500)
            playerASession = ensureSharedSession(playerA!!, "playerA")
            playerBSession = ensureSharedSession(playerB!!, "playerB")

            val created = createDynamicsProcessing()
            dp = created.first
            dpError = created.second
            dpAvailable = dp != null
            Log.i(TAG, "dpAvailable=$dpAvailable dpError=$dpError")

            // Output mix (session 0): a Visualizer on the shared session is inserted first in its chain and
            // would measure the limiter's input; the output mix sees the post-limiter sum.
            val vis = createVisualizer(OUTPUT_MIX_SESSION)
            visualizer = vis.first
            visualizerStatus = vis.second
            visualizerSession = if (vis.first != null) OUTPUT_MIX_SESSION else -1
            Log.i(TAG, "visualizer=$visualizerStatus visualizerSession=$visualizerSession")

            phaseResults["silence"] = runSilencePhase()
            phaseResults["singleOn"] = runPhase("singleOn", loud1k, null, dpEnabled = true, capturePhase = null)
            phaseResults["singleOff"] = runPhase("singleOff", loud1k, null, dpEnabled = false, capturePhase = null)
            phaseResults["quietSumOn"] =
                runPhase("quietSumOn", quiet1k, quiet1500, dpEnabled = true, capturePhase = null)
            phaseResults["quietSumOff"] =
                runPhase("quietSumOff", quiet1k, quiet1500, dpEnabled = false, capturePhase = null)
            phaseResults["loudSumOn"] =
                runPhase("loudSumOn", loud1k, loud1500, dpEnabled = true, capturePhase = "overlap")
            phaseResults["loudSumOff"] =
                runPhase("loudSumOff", loud1k, loud1500, dpEnabled = false, capturePhase = null)
            phaseResults["hires"] = runPhase("hires", hires, null, dpEnabled = true, capturePhase = "hires")
        } catch (t: Throwable) {
            thrown = t
        } finally {
            releaseAll()
        }

        report()
        thrown?.let { throw it }
    }

    private fun computeFrameMs(audioManager: AudioManager): Float {
        val frames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        return if (frames != null && frames > 0 && sampleRate != null && sampleRate > 0) {
            frames.toFloat() * 1000f / sampleRate.toFloat()
        } else {
            10f
        }
    }

    private fun buildPlayer(attributes: AudioAttributes, label: String): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            val parameters = DefaultTrackSelector.Parameters.Builder()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        )
                        .build()
                )
                .build()
            setParameters(parameters)
        }
        return ExoPlayer.Builder(context)
            .setLooper(thread.looper)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(attributes, false)
            .build()
            .apply {
                setAudioSessionId(audioSessionId)
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        Log.i(TAG, "$label.onAudioSessionIdChanged=$audioSessionId")
                    }
                })
            }
    }

    private fun ensureSharedSession(player: ExoPlayer, label: String): Int {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        var session = call { player.audioSessionId }
        while (session != audioSessionId && SystemClock.elapsedRealtime() < deadline) {
            call { player.setAudioSessionId(audioSessionId) }
            SystemClock.sleep(50)
            session = call { player.audioSessionId }
        }
        if (session != audioSessionId) {
            throw AssertionError("$label session $session != shared $audioSessionId")
        }
        Log.i(TAG, "ensureSharedSession $label ok session=$session")
        return session
    }

    private fun createDynamicsProcessing(): Pair<DynamicsProcessing?, String?> {
        return try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                false, 0,
                false, 0,
                true,
            ).setPreferredFrameDuration(frameMs).build()
            config.setInputGainAllChannelsTo(0f)
            config.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1f,
                    60f,
                    10f,
                    -1f,
                    0f,
                )
            )
            val processing = DynamicsProcessing(0, audioSessionId, config)
            processing.enabled = true
            processing to null
        } catch (t: Throwable) {
            null to "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun createVisualizer(sessionId: Int): Pair<Visualizer?, String> {
        return try {
            val vis = Visualizer(sessionId)
            vis.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            vis.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            vis.setEnabled(true)
            vis to "available"
        } catch (t: Throwable) {
            null to "unavailable(${t.javaClass.simpleName}: ${t.message})"
        }
    }

    private fun runSilencePhase(): Measurement {
        dp?.enabled = true
        call {
            playerA?.pause()
            playerB?.pause()
        }
        SystemClock.sleep(SETTLE_MS)
        val measurement = measure()
        val peak = measurement.peakMb?.let { "$it mB" } ?: "unavailable"
        val rms = measurement.rmsMb?.let { "$it mB" } ?: "unavailable"
        Log.i(
            TAG,
            "phase=silence dpEnabled=true windowMs=${measurement.windowMs} peak=$peak rms=$rms",
        )
        assertTrue("silence did not run its full measurement window", measurement.windowMs >= WINDOW_MS)
        return measurement
    }

    private fun runPhase(
        name: String,
        fileA: File,
        fileB: File?,
        dpEnabled: Boolean,
        capturePhase: String?,
    ): Measurement {
        dp?.enabled = dpEnabled
        call {
            playerA?.pause()
            playerB?.pause()
        }
        preparePlayer(playerA!!, fileA)
        playerASession = ensureSharedSession(playerA!!, "playerA")
        if (fileB != null) {
            preparePlayer(playerB!!, fileB)
            playerBSession = ensureSharedSession(playerB!!, "playerB")
        }

        call {
            playerA?.seekTo(0L)
            playerA?.play()
            if (fileB != null) {
                playerB?.seekTo(0L)
                playerB?.play()
            }
        }

        SystemClock.sleep(SETTLE_MS)
        if (capturePhase != null) {
            captures[capturePhase] = captureAudioFlinger(capturePhase)
        }
        val measurement = measure()

        call {
            playerA?.pause()
            playerB?.pause()
        }

        val peak = measurement.peakMb?.let { "$it mB" } ?: "unavailable"
        val rms = measurement.rmsMb?.let { "$it mB" } ?: "unavailable"
        Log.i(
            TAG,
            "phase=$name dpEnabled=$dpEnabled windowMs=${measurement.windowMs} peak=$peak rms=$rms",
        )
        assertTrue("$name did not run its full measurement window", measurement.windowMs >= WINDOW_MS)
        return measurement
    }

    private fun preparePlayer(player: ExoPlayer, file: File) {
        call {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        }
        awaitReady(player)
    }

    private fun awaitReady(player: ExoPlayer, timeoutMs: Long = 20_000L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = call { player.playbackState }
            if (state == Player.STATE_READY) return
            if (state == Player.STATE_IDLE) break
            SystemClock.sleep(100)
        }
        throw AssertionError("player not ready: ${call { player.playbackState }}")
    }

    private fun measure(): Measurement {
        val vis = visualizer
        val start = SystemClock.elapsedRealtime()
        val end = start + WINDOW_MS
        var peakMb: Long? = null
        var rmsSum = 0L
        var rmsCount = 0L
        val holder = Visualizer.MeasurementPeakRms()
        while (SystemClock.elapsedRealtime() < end) {
            if (vis != null) {
                try {
                    if (vis.getMeasurementPeakRms(holder) == Visualizer.SUCCESS) {
                        val peak = holder.mPeak.toLong()
                        val rms = holder.mRms.toLong()
                        val previousPeak = peakMb
                        if (previousPeak == null || peak > previousPeak) peakMb = peak
                        rmsSum += rms
                        rmsCount++
                    }
                } catch (t: Throwable) {
                    Log.i(TAG, "visualizer read failed: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
            SystemClock.sleep(POLL_MS)
        }
        val windowMs = SystemClock.elapsedRealtime() - start
        val meanRms = if (rmsCount > 0L) rmsSum / rmsCount else null
        return Measurement(peakMb, meanRms, windowMs)
    }

    private fun captureAudioFlinger(phase: String): String? {
        return try {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("dumpsys media.audio_flinger")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val text = input.readBytes().toString(Charsets.UTF_8)
                try {
                    val dir = context.getExternalFilesDir(null) ?: context.filesDir
                    File(dir, "s2-audioflinger-$phase.txt").writeText(text)
                } catch (t: Throwable) {
                    Log.i(TAG, "writing dumpsys $phase failed: ${t.javaClass.simpleName}: ${t.message}")
                }
                text
            }
        } catch (t: Throwable) {
            Log.i(TAG, "dumpsys $phase failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun extractDumpsys(text: String?, sessionId: Int): DumpsysExtract {
        if (text == null) return DumpsysExtract("unavailable", "unavailable")
        return try {
            val outputThreads = LinkedHashSet<String>()
            val sessionLines = mutableListOf<String>()
            val effectLines = mutableListOf<String>()
            val sessionPattern = Regex("\\b$sessionId\\b")
            var currentThread = "unknown"
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith("Output thread") || line.contains("Output thread")) {
                    currentThread = line.substringBefore(":").trim().ifEmpty { line }
                    outputThreads.add(currentThread)
                }
                if (sessionPattern.containsMatchIn(line)) {
                    sessionLines.add("[$currentThread] $line")
                }
                if (line.contains("Dynamics", ignoreCase = true) ||
                    line.contains(DP_UUID, ignoreCase = true)
                ) {
                    effectLines.add("[$currentThread] $line")
                }
            }
            val threads = buildString {
                append("sessionLines=")
                append(if (sessionLines.isEmpty()) "none" else sessionLines.joinToString(" | "))
                append("; outputThreads=")
                append(if (outputThreads.isEmpty()) "none" else outputThreads.joinToString(" | "))
            }
            val effect = if (effectLines.isEmpty()) {
                "no Dynamics lines (uuid=$DP_UUID)"
            } else {
                effectLines.joinToString(" | ")
            }
            DumpsysExtract(threads, effect)
        } catch (t: Throwable) {
            DumpsysExtract(
                "extract-failed(${t.javaClass.simpleName}: ${t.message})",
                "extract-failed",
            )
        }
    }

    private fun extractSessionEffects(text: String?, sessionId: Int): String {
        if (text == null) return "unavailable"
        return try {
            val lines = text.lines()
            val matches = mutableListOf<String>()
            val sessionIdPattern = Regex("(?i)session\\s*id\\s*[:=]?\\s*$sessionId\\b")
            var currentThread = "unknown"
            for ((index, raw) in lines.withIndex()) {
                val line = raw.trim()
                if (line.startsWith("Output thread") || line.contains("Output thread")) {
                    currentThread = line.substringBefore(":").trim().ifEmpty { line }
                }
                if (!sessionIdPattern.containsMatchIn(line)) continue
                var effectName = ""
                var above = index - 1
                while (above >= 0 && index - above <= 6) {
                    val previous = lines[above].trim()
                    if (previous.contains("Effect", ignoreCase = true) &&
                        !previous.contains("Session", ignoreCase = true)
                    ) {
                        effectName = previous.substringBefore(":").trim()
                        break
                    }
                    above--
                }
                val prefix = if (effectName.isEmpty()) "" else "$effectName "
                matches.add("$prefix[thread=$currentThread] $line")
            }
            if (matches.isEmpty()) "no session-$sessionId effect lines" else matches.joinToString(" | ")
        } catch (t: Throwable) {
            "extract-failed(${t.javaClass.simpleName}: ${t.message})"
        }
    }

    private fun extractTrackSessions(text: String?, pid: Int): String {
        if (text == null) return "unavailable"
        return try {
            val pidPattern = Regex("\\b$pid\\b")
            val tokenPattern = Regex("\\d+")
            val sessions = LinkedHashSet<Int>()
            for (raw in text.lines()) {
                val line = raw.trim()
                val pidMatch = pidPattern.find(line) ?: continue
                val afterPid = line.substring(pidMatch.range.last + 1)
                val session = tokenPattern.find(afterPid)?.value?.toIntOrNull() ?: continue
                sessions.add(session)
            }
            if (sessions.isEmpty()) "none (pid=$pid)" else sessions.joinToString(",")
        } catch (t: Throwable) {
            "extract-failed(${t.javaClass.simpleName}: ${t.message})"
        }
    }

    private fun extractChainThread(text: String?, sessionId: Int): String {
        if (text == null) return "unavailable"
        return try {
            val sessionPattern = Regex("\\b$sessionId\\b")
            val attached = LinkedHashSet<String>()
            val orphanHits = LinkedHashSet<String>()
            var currentThread = "unknown"
            var inEffectSection = false
            var orphan = false
            for (raw in text.lines()) {
                val line = raw.trim()
                when {
                    line.startsWith("Output thread") || line.contains("Output thread") -> {
                        currentThread = line.substringBefore(":").trim().ifEmpty { line }
                        inEffectSection = false
                        orphan = false
                    }
                    line.contains("Orphan Effect Chains", ignoreCase = true) -> {
                        inEffectSection = true
                        orphan = true
                    }
                    line.contains("Effect Chain", ignoreCase = true) &&
                        !line.contains("Orphan", ignoreCase = true) -> {
                        inEffectSection = true
                        orphan = false
                    }
                }
                if (inEffectSection && sessionPattern.containsMatchIn(line)) {
                    if (orphan) {
                        orphanHits.add("[$currentThread] $line")
                    } else {
                        attached.add("[$currentThread] $line")
                    }
                }
            }
            when {
                attached.isNotEmpty() -> attached.joinToString(" | ")
                orphanHits.isNotEmpty() -> "orphan: ${orphanHits.joinToString(" | ")}"
                else -> "session $sessionId not under any Effect Chains"
            }
        } catch (t: Throwable) {
            "extract-failed(${t.javaClass.simpleName}: ${t.message})"
        }
    }

    private fun report() {
        val overlap = extractDumpsys(captures["overlap"], audioSessionId)
        val hires = extractDumpsys(captures["hires"], audioSessionId)
        val overlapSessionEffects = extractSessionEffects(captures["overlap"], audioSessionId)
        val hiresSessionEffects = extractSessionEffects(captures["hires"], audioSessionId)
        val pid = android.os.Process.myPid()
        val overlapTrackSessions = extractTrackSessions(captures["overlap"], pid)
        val hiresTrackSessions = extractTrackSessions(captures["hires"], pid)
        val overlapChainThread = extractChainThread(captures["overlap"], audioSessionId)
        val hiresChainThread = extractChainThread(captures["hires"], audioSessionId)

        val singleOn = phaseResults["singleOn"]?.peakMb
        val singleOff = phaseResults["singleOff"]?.peakMb
        val quietSumOn = phaseResults["quietSumOn"]?.peakMb
        val quietSumOff = phaseResults["quietSumOff"]?.peakMb
        val loudSumOn = phaseResults["loudSumOn"]?.peakMb
        val loudSumOff = phaseResults["loudSumOff"]?.peakMb
        val dpOnOffSingle = difference(singleOn, singleOff)
        val dpOnOffQuietSum = difference(quietSumOn, quietSumOff)
        val limitedVsSingle = difference(loudSumOn, singleOff)
        val bypassedVsSingle = difference(loudSumOff, singleOff)

        val summary = buildString {
            append("S2RESULT sessionId=$audioSessionId frameMs=$frameMs dpAvailable=$dpAvailable ")
            append("dpError=$dpError visualizer=$visualizerStatus visualizerSession=$visualizerSession")
            append(" playerASession=$playerASession playerBSession=$playerBSession")
            for ((name, result) in phaseResults) {
                append(" ${name}PeakMb=${result.peakMb} ${name}RmsMb=${result.rmsMb}")
            }
            append(" dpOnOffSingle=$dpOnOffSingle dpOnOffQuietSum=$dpOnOffQuietSum")
            append(" limitedVsSingle=$limitedVsSingle bypassedVsSingle=$bypassedVsSingle")
            append(" overlapTrackSessions=$overlapTrackSessions hiresTrackSessions=$hiresTrackSessions")
            append(" overlapChainThread=$overlapChainThread hiresChainThread=$hiresChainThread")
        }
        Log.i(TAG, summary)

        val bundle = Bundle().apply {
            putInt("sessionId", audioSessionId)
            putFloat("frameMs", frameMs)
            putBoolean("dpAvailable", dpAvailable)
            putString("dpError", dpError)
            putString("visualizer", visualizerStatus)
            putInt("visualizerSession", visualizerSession)
            putInt("playerASession", playerASession)
            putInt("playerBSession", playerBSession)
            for ((name, result) in phaseResults) {
                result.peakMb?.let { putLong("${name}PeakMb", it) }
                result.rmsMb?.let { putLong("${name}RmsMb", it) }
            }
            dpOnOffSingle?.let { putLong("dpOnOffSingle", it) }
            dpOnOffQuietSum?.let { putLong("dpOnOffQuietSum", it) }
            limitedVsSingle?.let { putLong("limitedVsSingle", it) }
            bypassedVsSingle?.let { putLong("bypassedVsSingle", it) }
            putString("overlapThreads", overlap.threads)
            putString("overlapEffect", overlap.effect)
            putString("overlapSessionEffects", overlapSessionEffects)
            putString("overlapTrackSessions", overlapTrackSessions)
            putString("overlapChainThread", overlapChainThread)
            putString("hiresThreads", hires.threads)
            putString("hiresEffect", hires.effect)
            putString("hiresSessionEffects", hiresSessionEffects)
            putString("hiresTrackSessions", hiresTrackSessions)
            putString("hiresChainThread", hiresChainThread)
        }
        try {
            InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
        } catch (t: Throwable) {
            Log.i(TAG, "sendStatus failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun difference(minuend: Long?, subtrahend: Long?): Long? =
        if (minuend != null && subtrahend != null) minuend - subtrahend else null

    private fun releaseAll() {
        try {
            visualizer?.let {
                it.enabled = false
                it.release()
            }
        } catch (t: Throwable) {
            Log.i(TAG, "releasing visualizer failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            dp?.release()
        } catch (t: Throwable) {
            Log.i(TAG, "releasing DynamicsProcessing failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            if (::thread.isInitialized) {
                call {
                    playerA?.release()
                    playerB?.release()
                }
            }
        } catch (t: Throwable) {
            Log.i(TAG, "releasing players failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            if (::thread.isInitialized) thread.quitSafely()
        } catch (t: Throwable) {
            Log.i(TAG, "quitting thread failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> call(block: () -> T): T {
        if (handler.looper.thread === Thread.currentThread()) return block()
        val latch = CountDownLatch(1)
        val value = AtomicReference<T?>()
        val error = AtomicReference<Throwable?>()
        handler.post {
            try {
                value.set(block())
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw AssertionError("timed out waiting for the s2-player thread")
        }
        error.get()?.let { throw it }
        return value.get() as T
    }

    private fun writeSineWav(file: File, frequency: Double, amplitude: Double) {
        val sampleRate = 48_000
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = sampleRate * TONE_SECONDS
        val dataSize = numFrames * blockAlign
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(buffer, channels, sampleRate, bitsPerSample, dataSize)
        for (i in 0 until numFrames) {
            val value = (sin(2.0 * PI * frequency * i / sampleRate) * amplitude * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(value)
            buffer.putShort(value)
        }
        file.outputStream().use { it.write(buffer.array()) }
    }

    private fun writeHiresWav(file: File) {
        val sampleRate = 96_000
        val channels = 2
        val bitsPerSample = 24
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = sampleRate * HIRES_SECONDS
        val dataSize = numFrames * blockAlign
        val amplitude = 10.0.pow(-6.0 / 20.0)
        val frequency = 1_000.0
        val maxSample = (1 shl 23) - 1
        val minSample = -(1 shl 23)
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(buffer, channels, sampleRate, bitsPerSample, dataSize)
        for (i in 0 until numFrames) {
            val value = (sin(2.0 * PI * frequency * i / sampleRate) * amplitude * maxSample)
                .roundToInt()
                .coerceIn(minSample, maxSample)
            put24Le(buffer, value)
            put24Le(buffer, value)
        }
        file.outputStream().use { it.write(buffer.array()) }
    }

    private fun writeHeader(
        buffer: ByteBuffer,
        channels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        dataSize: Int,
    ) {
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
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
    }

    private fun put24Le(buffer: ByteBuffer, value: Int) {
        buffer.put((value and 0xFF).toByte())
        buffer.put(((value shr 8) and 0xFF).toByte())
        buffer.put(((value shr 16) and 0xFF).toByte())
    }
}
