package me.misa198.airmedy.spikes

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Spike S5 test code for specs/001-media3-migration (ADR-005), not app code.
 *
 * Answers the ADR-005 question: does the platform Opus decoder apply the OpusHead `output gain`
 * field, and exactly once? It decodes three Ogg/Opus fixtures under
 * `<externalFilesDir>/s5/` (`s5_gain0.opus`, `s5_gain_plus6.opus`, `s5_gain_minus6.opus`) that
 * differ only in that field, captures the decoded PCM [1.0 s, 4.0 s) of media time through a
 * [TeeAudioProcessor] in the player's [DefaultAudioSink], and reports the RMS of each in dBFS.
 *
 * A `diffPlus6`/`diffMinus6` of roughly ±6 dB means the decoder applied the gain once (the
 * `GainProcessor` must add nothing); roughly ±12 dB means twice; roughly 0 dB means never.
 *
 * The fixtures are generated on a host by `scripts/spikes/opus-header-gain.py` and pushed to the
 * device. This test only asserts harness sanity; the level differences are reported via `Log.i`
 * and `sendStatus`.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class OpusHeaderGainSpikeTest {

    private companion object {
        const val TAG = "S5"
        const val CAPTURE_START_SECONDS = 1.0
        const val CAPTURE_END_SECONDS = 4.0
        const val PLAY_TIMEOUT_MS = 25_000L
        const val READY_TIMEOUT_MS = 20_000L
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    @Test(timeout = 120_000)
    fun headerGainAppliedOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val external = context.getExternalFilesDir(null)
        assumeTrue("target context has no external files dir", external != null)
        val dir = File(external, "s5")
        val fixtures = listOf(
            "gain0" to File(dir, "s5_gain0.opus"),
            "plus6" to File(dir, "s5_gain_plus6.opus"),
            "minus6" to File(dir, "s5_gain_minus6.opus"),
        )
        val missing = fixtures.filter { !it.second.isFile }.map { it.second.name }
        assumeTrue(
            "Missing S5 fixture(s) $missing. Generate them with " +
                "`python3 scripts/spikes/opus-header-gain.py --out <dir>` and push them with " +
                "`adb push <dir>/. ${dir.absolutePath}/`",
            missing.isEmpty(),
        )

        thread = HandlerThread("s5-player").apply { start() }
        handler = Handler(thread.looper)

        val rmsDb = linkedMapOf<String, Double>()
        val framesAtEnd = linkedMapOf<String, Long>()
        val sampleRates = linkedMapOf<String, Int>()
        val decoderName = AtomicReference("unavailable")
        var thrown: Throwable? = null

        try {
            for ((label, file) in fixtures) {
                val tee = RmsTeeAudioSink(CAPTURE_START_SECONDS, CAPTURE_END_SECONDS)
                val player = buildPlayer(context, tee, decoderName)
                try {
                    call {
                        player.volume = 0f
                        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        player.prepare()
                    }
                    awaitReady(player)
                    call {
                        player.seekTo(0L)
                        player.play()
                    }
                    awaitCapture(tee)
                    rmsDb[label] = tee.rmsDbfs()
                    framesAtEnd[label] = tee.totalFrames()
                    sampleRates[label] = tee.sampleRate
                    Log.i(
                        TAG,
                        "file=${file.name} sampleRate=${tee.sampleRate} channels=${tee.channelCount} " +
                            "encoding=${tee.encoding} frames=${tee.totalFrames()} " +
                            "rmsDb=${format(rmsDb[label])}",
                    )
                } finally {
                    call { player.release() }
                }
            }
        } catch (t: Throwable) {
            thrown = t
        } finally {
            thread.quitSafely()
        }

        report(rmsDb, decoderName.get())
        assertHarnessSanity(fixtures, framesAtEnd, sampleRates, rmsDb)
        thrown?.let { throw it }
    }

    private fun buildPlayer(
        context: Context,
        tee: RmsTeeAudioSink,
        decoderNameRef: AtomicReference<String>,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(tee)))
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
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLooper(thread.looper)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(attributes, false)
            .build()
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializationDurationMs: Long,
                decoderInitializedTimestampMs: Long,
            ) {
                decoderNameRef.set(decoderName)
                Log.i(
                    TAG,
                    "decoderName=$decoderName initializationDurationMs=$initializationDurationMs " +
                        "decoderInitializedTimestampMs=$decoderInitializedTimestampMs",
                )
            }
        })
        return player
    }

    private fun awaitReady(player: ExoPlayer) {
        val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = call { player.playbackState }
            if (state == Player.STATE_READY) return
            if (state == Player.STATE_IDLE) break
            SystemClock.sleep(100)
        }
        throw AssertionError("player not ready: ${call { player.playbackState }}")
    }

    /** Waits until the tee has seen samples past the capture window (plus a safety margin). */
    private fun awaitCapture(tee: RmsTeeAudioSink) {
        val deadline = SystemClock.elapsedRealtime() + PLAY_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val rate = tee.sampleRate
            if (rate > 0) {
                val target = ((CAPTURE_END_SECONDS + 0.5) * rate).toLong()
                if (tee.totalFrames() >= target) return
            }
            SystemClock.sleep(50)
        }
        throw AssertionError(
            "timed out capturing S5 audio: sampleRate=${tee.sampleRate} frames=${tee.totalFrames()}"
        )
    }

    private fun report(rmsDb: Map<String, Double>, decoderName: String) {
        val gain0 = rmsDb["gain0"]
        val plus6 = rmsDb["plus6"]
        val minus6 = rmsDb["minus6"]
        val diffPlus6 = if (gain0 != null && plus6 != null) plus6 - gain0 else Double.NaN
        val diffMinus6 = if (gain0 != null && minus6 != null) minus6 - gain0 else Double.NaN

        val line = "S5RESULT gain0Db=${format(gain0)} plus6Db=${format(plus6)} " +
            "minus6Db=${format(minus6)} diffPlus6=${format(diffPlus6)} " +
            "diffMinus6=${format(diffMinus6)} decoderName=$decoderName"
        Log.i(TAG, line)

        val bundle = Bundle().apply {
            gain0?.let { putDouble("gain0Db", it) }
            plus6?.let { putDouble("plus6Db", it) }
            minus6?.let { putDouble("minus6Db", it) }
            putDouble("diffPlus6", diffPlus6)
            putDouble("diffMinus6", diffMinus6)
            putString("decoderName", decoderName)
        }
        try {
            InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
        } catch (t: Throwable) {
            Log.w(TAG, "sendStatus failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun assertHarnessSanity(
        fixtures: List<Pair<String, File>>,
        framesAtEnd: Map<String, Long>,
        sampleRates: Map<String, Int>,
        rmsDb: Map<String, Double>,
    ) {
        for ((label, _) in fixtures) {
            val frames = framesAtEnd[label] ?: continue
            val rate = sampleRates[label] ?: 0
            assertTrue(
                "$label produced $frames frames at ${rate}Hz (need >= 2s of audio)",
                rate > 0 && frames >= 2L * rate,
            )
            val rms = rmsDb[label]
            assertTrue("$label RMS is not finite: $rms", rms != null && rms.isFinite())
        }
    }

    private fun format(value: Double?): String =
        if (value == null) "unavailable" else String.format(Locale.US, "%.3f", value)

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
            throw AssertionError("timed out waiting for the s5-player thread")
        }
        error.get()?.let { throw it }
        return value.get() as T
    }

    /**
     * A [TeeAudioProcessor.AudioBufferSink] that accumulates the sum of squares of the decoded
     * samples whose frame index falls inside `[startSeconds, endSeconds)` of media time, so the
     * test can report the RMS of a fixed, codec-independent window.
     */
    @OptIn(UnstableApi::class)
    private class RmsTeeAudioSink(
        private val startSeconds: Double,
        private val endSeconds: Double,
    ) : TeeAudioProcessor.AudioBufferSink {

        @Volatile
        var sampleRate: Int = 0
            private set

        @Volatile
        var channelCount: Int = 0
            private set

        @Volatile
        var encoding: Int = C.ENCODING_INVALID
            private set

        private val frames = AtomicLong(0L)
        private var windowSamples = 0L
        private var windowSumSquares = 0.0

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            sampleRate = sampleRateHz
            this.channelCount = channelCount
            this.encoding = encoding
            frames.set(0L)
            synchronized(this) {
                windowSamples = 0L
                windowSumSquares = 0.0
            }
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val enc = encoding
            val channels = max(1, channelCount)
            val bytesPerSample = if (enc == C.ENCODING_PCM_FLOAT) 4 else 2
            val bytesPerFrame = bytesPerSample * channels
            val rate = sampleRate
            val startFrame = if (rate > 0) (startSeconds * rate).toLong() else 0L
            val endFrame = if (rate > 0) (endSeconds * rate).toLong() else Long.MAX_VALUE

            val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            var frame = frames.get()
            val firstFrame = frame
            var localSamples = 0L
            var localSumSquares = 0.0
            while (duplicate.remaining() >= bytesPerFrame) {
                val inWindow = frame in startFrame until endFrame
                for (channel in 0 until channels) {
                    val sample = if (enc == C.ENCODING_PCM_FLOAT) {
                        duplicate.getFloat().toDouble()
                    } else {
                        duplicate.getShort().toDouble() / 32768.0
                    }
                    if (inWindow) {
                        localSumSquares += sample * sample
                        localSamples++
                    }
                }
                frame++
            }
            frames.addAndGet(frame - firstFrame)
            synchronized(this) {
                windowSamples += localSamples
                windowSumSquares += localSumSquares
            }
        }

        fun totalFrames(): Long = frames.get()

        fun rmsDbfs(): Double {
            val samples: Long
            val sumSquares: Double
            synchronized(this) {
                samples = windowSamples
                sumSquares = windowSumSquares
            }
            if (samples <= 0L) return Double.NEGATIVE_INFINITY
            val rms = sqrt(sumSquares / samples.toDouble())
            if (rms <= 0.0) return Double.NEGATIVE_INFINITY
            return 20.0 * log10(rms)
        }
    }
}
