package me.misa198.airmedy.spikes

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spike S3 test code for specs/001-media3-migration (ADR-003, ADR-004), not app code.
 *
 * Plays a track list on Media3 [ExoPlayer] in four modes and reports, per mode, the process CPU
 * usage and memory, so the orchestrator can decide the pre-buffer length and whether the per-player
 * processors run at track rate or at a fixed output rate.
 *
 * - `single`: one player, the whole list as a `REPEAT_MODE_ALL` playlist.
 * - `abCycle`: cycles of `cycleSec`; the next player is created and prepared `prebufferSec` before
 *   the equal-power fade, the fade runs for `fadeSec`, then the outgoing player is released.
 * - `abCycleProcessors`: same, with each sink chain adding the S3 load processors
 *   (fade -> gain -> width -> EQ).
 * - `abCycleProcessorsResampled`: same, with a [SonicAudioProcessor] at the device output rate first.
 *
 * [smokeAllModes] always runs in the normal gate against generated WAV tracks. [measureRun] runs the
 * long measurement and is skipped unless the `tracks` instrumentation arg is given. Every ExoPlayer
 * call is posted to one `HandlerThread`; players and the thread are released in `finally`.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ResourceSpikeTest {

    private companion object {
        const val TAG = "S3"
        const val SAMPLE_INTERVAL_MS = 10_000L
        const val INTER_MODE_PAUSE_MS = 2_000L
        const val TRACK_SECONDS = 20
        const val WAV_SAMPLE_RATE = 44_100
        const val MINUS_12_DBFS = 0.25118864
        const val DEFAULT_MINUTES = 10.0
        const val DEFAULT_CYCLE_SEC = 60.0
        const val DEFAULT_FADE_SEC = 12.0
        const val DEFAULT_PREBUFFER_SEC = 5.0
        val ALL_MODES = listOf("single", "abCycle", "abCycleProcessors", "abCycleProcessorsResampled")
    }

    private enum class ProcessorChain { FadeOnly, Full, Resampled }

    private data class ModeResult(
        val mode: String,
        val minutes: Double,
        val cpuPercent: Double,
        val pssMeanKb: Long,
        val pssMaxKb: Long,
        val playersMean: Double,
        val cycles: Int,
    )

    private data class PlayerHandle(val player: ExoPlayer, val fade: SpikeFadeProcessor)

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val deviceOutputRate: Int by lazy {
        context.getSystemService(AudioManager::class.java)
            .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48_000
    }

    private val livePlayers = AtomicInteger(0)

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var audioSessionId = 0

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var fadeA: SpikeFadeProcessor? = null
    private var fadeB: SpikeFadeProcessor? = null

    @Test(timeout = 240_000)
    fun smokeAllModes() {
        val tracks = listOf(440.0, 550.0, 660.0).mapIndexed { index, frequency ->
            File(context.cacheDir, "s3_track_${index + 1}_${frequency.toInt()}Hz.wav").also {
                writeSineWav(it, frequency, MINUS_12_DBFS)
            }
        }
        for (mode in ALL_MODES) {
            val result = runMode(
                mode = mode,
                mix = "smoke",
                tracks = tracks,
                minutes = 0.5,
                cycleSec = 20.0,
                fadeSec = 4.0,
                prebufferSec = 2.0,
            )
            assertTrue("$mode reported cpuPercent=${result.cpuPercent}", result.cpuPercent > 0.0)
            assertTrue("$mode reported pssMaxKb=${result.pssMaxKb}", result.pssMaxKb > 0L)
            if (mode != "single") {
                assertTrue("$mode completed ${result.cycles} cycles", result.cycles >= 1)
            }
        }
    }

    @Test
    fun measureRun() {
        val args = InstrumentationRegistry.getArguments()
        val tracksArg = args.getString("tracks")
        assumeTrue("measureRun requires the 'tracks' instrumentation arg", tracksArg != null)
        val tracks = tracksArg!!.split('|').filter { it.isNotBlank() }.map { File(it) }
        require(tracks.isNotEmpty()) { "tracks arg produced no files" }

        val mix = args.getString("mix") ?: "custom"
        val minutes = args.getString("minutes")?.toDoubleOrNull() ?: DEFAULT_MINUTES
        val modes = (args.getString("modes") ?: ALL_MODES.joinToString(","))
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val cycleSec = args.getString("cycleSec")?.toDoubleOrNull() ?: DEFAULT_CYCLE_SEC
        val fadeSec = args.getString("fadeSec")?.toDoubleOrNull() ?: DEFAULT_FADE_SEC
        val prebufferSec = args.getString("prebufferSec")?.toDoubleOrNull() ?: DEFAULT_PREBUFFER_SEC

        for (mode in modes) {
            runMode(mode, mix, tracks, minutes, cycleSec, fadeSec, prebufferSec)
        }
    }

    private fun runMode(
        mode: String,
        mix: String,
        tracks: List<File>,
        minutes: Double,
        cycleSec: Double,
        fadeSec: Double,
        prebufferSec: Double,
    ): ModeResult {
        thread = HandlerThread("s3-$mode").apply { start() }
        handler = Handler(thread.looper)
        audioSessionId = context.getSystemService(AudioManager::class.java).generateAudioSessionId()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val measurement = ModeMeasurement()
        val wallStartMs = SystemClock.elapsedRealtime()
        val cpuStartMs = Process.getElapsedCpuTime()
        measurement.markStart(wallStartMs)

        val cycles: Int
        try {
            cycles = when (mode) {
                "single" -> runSingle(tracks, attributes, minutes, measurement)
                "abCycle" -> runAbCycle(
                    tracks, attributes, minutes, cycleSec, fadeSec, prebufferSec,
                    ProcessorChain.FadeOnly, measurement,
                )
                "abCycleProcessors" -> runAbCycle(
                    tracks, attributes, minutes, cycleSec, fadeSec, prebufferSec,
                    ProcessorChain.Full, measurement,
                )
                "abCycleProcessorsResampled" -> runAbCycle(
                    tracks, attributes, minutes, cycleSec, fadeSec, prebufferSec,
                    ProcessorChain.Resampled, measurement,
                )
                else -> throw IllegalArgumentException("unknown mode '$mode'")
            }
        } finally {
            measurement.sampleNow()
            releaseAll()
        }

        val wallEndMs = SystemClock.elapsedRealtime()
        val cpuEndMs = Process.getElapsedCpuTime()
        val deltaWallMs = wallEndMs - wallStartMs
        val deltaCpuMs = cpuEndMs - cpuStartMs
        val cpuPercent = if (deltaWallMs > 0L) deltaCpuMs * 100.0 / deltaWallMs else 0.0

        val result = ModeResult(
            mode = mode,
            minutes = minutes,
            cpuPercent = cpuPercent,
            pssMeanKb = measurement.pssMeanKb(),
            pssMaxKb = measurement.pssMaxKb(),
            playersMean = measurement.playersMean(),
            cycles = cycles,
        )
        report(mix, result)

        System.gc()
        SystemClock.sleep(INTER_MODE_PAUSE_MS)
        return result
    }

    private fun runSingle(
        tracks: List<File>,
        attributes: AudioAttributes,
        minutes: Double,
        measurement: ModeMeasurement,
    ): Int {
        val player = call { createPlayer(emptyArray(), attributes) }
        playerA = player
        call {
            player.setMediaItems(tracks.map { MediaItem.fromUri(Uri.fromFile(it)) })
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
        }
        awaitReady(player, "single")
        ensureSharedSession(player, "single")
        call { player.play() }
        measurement.sampleNow()

        val modeEndMs = SystemClock.elapsedRealtime() + (minutes * 60_000.0).toLong()
        sleepUntil(modeEndMs, modeEndMs, measurement)
        return 0
    }

    private fun runAbCycle(
        tracks: List<File>,
        attributes: AudioAttributes,
        minutes: Double,
        cycleSec: Double,
        fadeSec: Double,
        prebufferSec: Double,
        chain: ProcessorChain,
        measurement: ModeMeasurement,
    ): Int {
        val modeEndMs = SystemClock.elapsedRealtime() + (minutes * 60_000.0).toLong()
        val cycleMs = (cycleSec * 1_000.0).toLong()
        val fadeMs = (fadeSec * 1_000.0).toLong()
        val bCreateOffsetMs = cycleMs - fadeMs - (prebufferSec * 1_000.0).toLong()
        val fadeOffsetMs = cycleMs - fadeMs

        var trackIndex = 0
        val initial = call { newFadingPlayer(chain, attributes) }
        playerA = initial.player
        fadeA = initial.fade
        prepareSingleTrack(initial.player, tracks[0])
        ensureSharedSession(initial.player, "A@0")
        call { initial.player.play() }
        measurement.sampleNow()

        var cycles = 0
        while (true) {
            val cycleStartMs = SystemClock.elapsedRealtime()
            val cycleEndMs = cycleStartMs + cycleMs
            val bCreateAtMs = cycleStartMs + bCreateOffsetMs
            val fadeAtMs = cycleStartMs + fadeOffsetMs

            if (bCreateAtMs >= modeEndMs) break
            sleepUntil(bCreateAtMs, modeEndMs, measurement)
            if (SystemClock.elapsedRealtime() >= modeEndMs) break

            val nextIndex = (trackIndex + 1) % tracks.size
            val incoming = call { newFadingPlayer(chain, attributes) }
            playerB = incoming.player
            fadeB = incoming.fade
            prepareSingleTrack(incoming.player, tracks[nextIndex])
            ensureSharedSession(incoming.player, "B@$cycles")

            if (fadeAtMs >= modeEndMs) break
            sleepUntil(fadeAtMs, modeEndMs, measurement)
            if (SystemClock.elapsedRealtime() >= modeEndMs) break

            fadeA?.startFade(SpikeFadeProcessor.Role.Outgoing, fadeMs)
            fadeB?.startFade(SpikeFadeProcessor.Role.Incoming, fadeMs)
            call { incoming.player.play() }

            sleepUntil(cycleEndMs, modeEndMs, measurement)
            if (SystemClock.elapsedRealtime() < modeEndMs) {
                releasePlayer(playerA)
                playerA = playerB
                fadeA = fadeB
                playerB = null
                fadeB = null
                trackIndex = nextIndex
                cycles++
            }
            if (SystemClock.elapsedRealtime() >= modeEndMs) break
        }
        return cycles
    }

    private fun prepareSingleTrack(player: ExoPlayer, file: File) {
        call {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        }
        awaitReady(player, "prepare")
    }

    private fun newFadingPlayer(chain: ProcessorChain, attributes: AudioAttributes): PlayerHandle {
        val fade = SpikeFadeProcessor()
        val processors = ArrayList<AudioProcessor>()
        if (chain == ProcessorChain.Resampled) {
            processors.add(
                SonicAudioProcessor().apply { setOutputSampleRateHz(deviceOutputRate) }
            )
        }
        processors.add(fade)
        if (chain != ProcessorChain.FadeOnly) {
            processors.add(GainLoadProcessor())
            processors.add(WidthLoadProcessor())
            processors.add(EqLoadProcessor())
        }
        val player = createPlayer(processors.toTypedArray(), attributes)
        return PlayerHandle(player, fade)
    }

    private fun createPlayer(processors: Array<AudioProcessor>, attributes: AudioAttributes): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(processors)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
        }
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
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLooper(thread.looper)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(attributes, false)
            .build()
        call { player.setAudioSessionId(audioSessionId) }
        livePlayers.incrementAndGet()
        return player
    }

    private fun awaitReady(player: ExoPlayer, label: String, timeoutMs: Long = 20_000L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = call { player.playbackState }
            if (state == Player.STATE_READY) return
            if (state == Player.STATE_IDLE) break
            SystemClock.sleep(100)
        }
        throw AssertionError("$label not ready: ${call { player.playbackState }}")
    }

    private fun ensureSharedSession(player: ExoPlayer, label: String) {
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
    }

    private fun sleepUntil(targetMs: Long, modeEndMs: Long, measurement: ModeMeasurement) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (now >= targetMs || now >= modeEndMs) return
            val step = minOf(200L, targetMs - now, modeEndMs - now)
            SystemClock.sleep(step)
            measurement.maybeSample(SystemClock.elapsedRealtime())
        }
    }

    private fun releasePlayer(player: ExoPlayer?) {
        if (player == null) return
        call { player.release() }
        livePlayers.decrementAndGet()
    }

    private fun releaseAll() {
        try {
            if (::thread.isInitialized) {
                call {
                    playerA?.release()
                    playerB?.release()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "releasing players failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        playerA = null
        playerB = null
        fadeA = null
        fadeB = null
        livePlayers.set(0)
        try {
            if (::thread.isInitialized) thread.quitSafely()
        } catch (t: Throwable) {
            Log.w(TAG, "quitting player thread failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun report(mix: String, result: ModeResult) {
        val line = String.format(
            Locale.US,
            "S3RESULT mix=%s mode=%s minutes=%s cpuPercent=%.2f pssMeanKb=%d pssMaxKb=%d " +
                "playersMean=%.3f cycles=%d",
            mix,
            result.mode,
            result.minutes,
            result.cpuPercent,
            result.pssMeanKb,
            result.pssMaxKb,
            result.playersMean,
            result.cycles,
        )
        Log.i(TAG, line)
        val bundle = Bundle().apply {
            putString("mix", mix)
            putString("mode", result.mode)
            putDouble("minutes", result.minutes)
            putDouble("cpuPercent", result.cpuPercent)
            putLong("pssMeanKb", result.pssMeanKb)
            putLong("pssMaxKb", result.pssMaxKb)
            putDouble("playersMean", result.playersMean)
            putInt("cycles", result.cycles)
        }
        try {
            InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
        } catch (t: Throwable) {
            Log.w(TAG, "sendStatus failed: ${t.javaClass.simpleName}: ${t.message}")
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
            throw AssertionError("timed out waiting for the spike-player thread")
        }
        error.get()?.let { throw it }
        return value.get() as T
    }

    private fun writeSineWav(file: File, frequency: Double, amplitude: Double) {
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = WAV_SAMPLE_RATE * TRACK_SECONDS
        val dataSize = numFrames * blockAlign
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(WAV_SAMPLE_RATE)
        buffer.putInt(WAV_SAMPLE_RATE * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (i in 0 until numFrames) {
            val value = (sin(2.0 * PI * frequency * i / WAV_SAMPLE_RATE) * amplitude * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(value)
            buffer.putShort(value)
        }
        file.outputStream().use { it.write(buffer.array()) }
    }

    private inner class ModeMeasurement {
        private val pssSamples = ArrayList<Long>()
        private val playerSamples = ArrayList<Int>()
        private var lastSampleMs = 0L
        private var started = false

        fun markStart(nowMs: Long) {
            lastSampleMs = nowMs
            started = true
        }

        fun sampleNow() {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            pssSamples.add(info.totalPss.toLong())
            playerSamples.add(livePlayers.get())
            lastSampleMs = SystemClock.elapsedRealtime()
        }

        fun maybeSample(nowMs: Long) {
            if (!started) return
            if (nowMs - lastSampleMs >= SAMPLE_INTERVAL_MS) {
                sampleNow()
            }
        }

        fun pssMeanKb(): Long =
            if (pssSamples.isEmpty()) 0L else pssSamples.sum() / pssSamples.size

        fun pssMaxKb(): Long = pssSamples.maxOrNull() ?: 0L

        fun playersMean(): Double =
            if (playerSamples.isEmpty()) 0.0 else playerSamples.average()
    }
}
