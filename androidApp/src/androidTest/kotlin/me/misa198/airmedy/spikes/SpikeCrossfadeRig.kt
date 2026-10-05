package me.misa198.airmedy.spikes

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Spike S1 test code for specs/001-media3-migration (ADR-003), not app code.
 *
 * A `TeeAudioProcessor` sink that records per-buffer peak samples together with a monotonic
 * "flush generation" counter, so a test can distinguish buffers produced after a sink flush.
 */
@OptIn(UnstableApi::class)
class TeeRecording : TeeAudioProcessor.AudioBufferSink {

    data class BufferRecord(val generation: Int, val peak: Float)

    val totalFrames = AtomicLong(0L)
    val flushGeneration = AtomicInteger(0)
    val firstBufferElapsedMs = AtomicLong(-1L)

    /** Set by the rig just before the incoming player starts, to measure the start offset. */
    val startElapsedMs = AtomicLong(-1L)

    private val records = CopyOnWriteArrayList<BufferRecord>()

    @Volatile
    var sampleRate: Int = 0
        private set

    @Volatile
    private var channelCount = 0

    @Volatile
    private var encoding = C.ENCODING_INVALID

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        flushGeneration.incrementAndGet()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val remaining = buffer.remaining()
        val start = startElapsedMs.get()
        if (firstBufferElapsedMs.get() < 0L && start >= 0L) {
            firstBufferElapsedMs.set(SystemClock.elapsedRealtime() - start)
        }
        records.add(BufferRecord(flushGeneration.get(), peakAbs(buffer)))
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val channels = max(1, channelCount)
        totalFrames.addAndGet((remaining / (bytesPerSample * channels)).toLong())
    }

    private fun peakAbs(buffer: ByteBuffer): Float {
        val enc = encoding
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0f
        if (enc == C.ENCODING_PCM_FLOAT) {
            while (dup.remaining() >= 4) {
                peak = max(peak, abs(dup.getFloat()))
            }
        } else {
            while (dup.remaining() >= 2) {
                peak = max(peak, abs(dup.getShort().toFloat()) / 32768f)
            }
        }
        return peak
    }

    fun records(): List<BufferRecord> = records.toList()

    fun maxPeak(): Float = records.fold(0f) { acc, record -> max(acc, record.peak) }

    fun reset() {
        records.clear()
        totalFrames.set(0L)
        firstBufferElapsedMs.set(-1L)
    }
}

/**
 * Spike S1 test code for specs/001-media3-migration (ADR-003), not app code.
 *
 * Drives two [ExoPlayer] instances on a single `HandlerThread` ("spike-player") with one shared
 * audio session id, each wired with its own [SpikeFadeProcessor] followed by a [TeeAudioProcessor].
 * Supports a processor-based crossfade and a stepped-volume crossfade, plus a `snap` that flushes
 * the incoming player so faded audio already queued is discarded.
 */
@OptIn(UnstableApi::class)
class SpikeCrossfadeRig(private val context: Context) {

    enum class Mode { Processor, Volume }

    val fadeA = SpikeFadeProcessor()
    val fadeB = SpikeFadeProcessor()
    val teeA = TeeRecording()
    val teeB = TeeRecording()

    @Volatile
    var mode: Mode = Mode.Processor

    private val thread = HandlerThread("spike-player").apply { start() }
    private val handler = Handler(thread.looper)
    private val audioSessionId: Int =
        context.getSystemService(AudioManager::class.java).generateAudioSessionId()

    private val playerA: ExoPlayer
    private val playerB: ExoPlayer

    @Volatile
    private var volumeLoopActive = false

    @Volatile
    private var crossfadeStartMs = 0L

    @Volatile
    private var crossfadeMs = 0L

    @Volatile
    private var startPositionA = 0L

    @Volatile
    private var outgoingLeadMs = 0L

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        playerA = call { buildPlayer(fadeA, TeeAudioProcessor(teeA), attributes) }
        playerB = call { buildPlayer(fadeB, TeeAudioProcessor(teeB), attributes) }
    }

    private fun buildPlayer(
        fade: SpikeFadeProcessor,
        tee: TeeAudioProcessor,
        attributes: AudioAttributes,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(fade, tee))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
        }
        val trackSelector = DefaultTrackSelector(context).apply {
            val parameters = DefaultTrackSelector.Parameters.Builder()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                        .build()
                )
                .build()
            setParameters(parameters)
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setLooper(thread.looper)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(attributes, false)
            .build()
            .apply { setAudioSessionId(audioSessionId) }
    }

    /** Sets and prepares the two media items. Blocks until the call has been posted and run. */
    fun setMedia(trackA: String, trackB: String) {
        call {
            playerA.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(trackA))))
            playerB.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(trackB))))
            playerA.prepare()
            playerB.prepare()
        }
    }

    fun awaitReady(timeoutMs: Long = 20_000L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val a = call { playerA.playbackState }
            val b = call { playerB.playbackState }
            if (a == Player.STATE_READY && b == Player.STATE_READY) return
            if (a == Player.STATE_IDLE || b == Player.STATE_IDLE) break
            SystemClock.sleep(100)
        }
        throw AssertionError(
            "players not ready: A=${call { playerA.playbackState }} B=${call { playerB.playbackState }}"
        )
    }

    fun playA(positionMs: Long) {
        call {
            startPositionA = positionMs
            playerA.seekTo(positionMs)
            teeA.reset()
            playerA.play()
        }
    }

    fun durationA(): Long = call { playerA.duration }

    fun durationB(): Long = call { playerB.duration }

    fun currentPositionA(): Long = call { playerA.currentPosition }

    /** Starts the crossfade: A fades out, B fades in (or volume is stepped), and B begins playing. */
    fun startCrossfade(fadeMs: Long) {
        call {
            val sampleRateA = teeA.sampleRate
            outgoingLeadMs = if (sampleRateA > 0) {
                (startPositionA + teeA.totalFrames.get() * 1000L / sampleRateA) - playerA.currentPosition
            } else {
                0L
            }
            crossfadeMs = fadeMs
            crossfadeStartMs = SystemClock.elapsedRealtime()
            teeB.startElapsedMs.set(crossfadeStartMs)
            if (mode == Mode.Processor) {
                fadeA.startFade(SpikeFadeProcessor.Role.Outgoing, fadeMs)
                fadeB.startFade(SpikeFadeProcessor.Role.Incoming, fadeMs)
            }
            playerB.play()
            if (mode == Mode.Volume) {
                volumeLoopActive = true
                scheduleVolumeStep()
            }
        }
    }

    private fun scheduleVolumeStep() {
        handler.post {
            if (!volumeLoopActive) return@post
            val elapsed = SystemClock.elapsedRealtime() - crossfadeStartMs
            val t = min(1f, elapsed.toFloat() / crossfadeMs.toFloat())
            val phase = t * (PI / 2).toFloat()
            playerA.volume = cos(phase)
            playerB.volume = sin(phase)
            if (elapsed < crossfadeMs) {
                handler.postDelayed({ scheduleVolumeStep() }, 16)
            }
        }
    }

    /**
     * Stops the outgoing player and snaps the incoming player to full gain, then flushes the
     * incoming player so already-processed faded audio is discarded. [kind] is one of `pause`
     * (also pause incoming), `seek` (incoming seeks to +10 s) or `next` (incoming restarts at 0).
     */
    fun snap(kind: String) {
        call {
            volumeLoopActive = false
            playerA.pause()
            if (mode == Mode.Processor) fadeB.snap() else playerB.volume = 1.0f
            val position = playerB.currentPosition
            playerB.seekTo(position)
            when (kind) {
                "pause" -> playerB.pause()
                "seek" -> playerB.seekTo(position + 10_000L)
                "next" -> playerB.seekTo(0L)
            }
        }
    }

    fun incomingStartOffsetMs(): Long = teeB.firstBufferElapsedMs.get()

    fun outgoingLeadMs(): Long = outgoingLeadMs

    fun release() {
        call {
            volumeLoopActive = false
            playerA.release()
            playerB.release()
        }
        thread.quitSafely()
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
}
