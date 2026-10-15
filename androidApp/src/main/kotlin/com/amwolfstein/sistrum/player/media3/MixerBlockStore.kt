package com.amwolfstein.sistrum.player.media3

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.amwolfstein.sistrum.diagnostics.Diagnostics
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Stable key for an output route: device type plus product name (e.g. "8:WH-1000XM4" for A2DP headphones). */
internal fun routeKey(device: AudioDeviceInfo?): String? =
    device?.let { "${it.type}:${it.productName}" }

internal fun routeKey(track: AudioTrack): String? = runCatching { routeKey(track.routedDevice) }.getOrNull()

internal fun formatMs(ms: Float): String = String.format(Locale.ROOT, "%.1f", ms)

/** A new measurement counts as a change when nothing was stored or it moved by at least half a millisecond. */
internal fun mixerBlockChanged(previous: Float?, measured: Float): Boolean =
    previous == null || kotlin.math.abs(previous - measured) >= 0.5f

/**
 * Measured mixer block (DynamicsProcessing frame duration, FR-052) per output route, kept across launches so the
 * limiter starts with the right value instead of Android's guess.
 */
internal class MixerBlockStore(private val prefs: SharedPreferences) {

    fun get(route: String): Float? = prefs.getFloat(route, Float.NaN).takeIf { it.isFinite() && it > 0f }

    /** Stores [ms] for [route]; true when it differs from what was stored (so callers log only real changes). */
    fun put(route: String?, ms: Float): Boolean {
        if (route == null || !ms.isFinite() || ms <= 0f) return false
        if (!mixerBlockChanged(get(route), ms)) return false
        prefs.edit().putFloat(route, ms).apply()
        return true
    }

    companion object {
        @Volatile private var shared: MixerBlockStore? = null

        fun forContext(context: Context): MixerBlockStore = shared ?: synchronized(this) {
            shared ?: MixerBlockStore(
                context.applicationContext.getSharedPreferences("mixer_blocks", Context.MODE_PRIVATE),
            ).also { shared = it }
        }
    }
}

/**
 * Measures the current output's mixer block with a short silent track when it has never been measured: at app
 * start and when an output device is added, so even the first song on a new phone or new headphones starts with
 * the right limiter frame duration. No audio focus, no session effect, no sound; skipped while any music plays
 * (a playing track measures itself).
 */
internal object SilentMixerProbe {
    private const val RATE = 48_000
    private const val WINDOW_MS = 1_500L
    private const val POLL_MS = 2L

    private val running = AtomicBoolean(false)
    private val handler: Handler by lazy {
        Handler(HandlerThread("sistrum-silent-probe").apply { start() }.looper)
    }

    fun measureIfUnknown(context: Context, delayMs: Long = 0L) {
        val app = context.applicationContext
        handler.postDelayed({ run(app) }, delayMs)
    }

    private fun run(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.isMusicActive || !running.compareAndSet(false, true)) return
        var track: AudioTrack? = null
        try {
            val store = MixerBlockStore.forContext(context)
            val minBytes = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minBytes <= 0) return
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBytes, RATE * 2 * 4 / 5))
                .build()
            val silence = FloatArray(RATE / 50 * 2)
            track.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
            track.play()
            val samples = ArrayList<MixerBlockEstimator.HeadSample>()
            val deadline = SystemClock.elapsedRealtime() + WINDOW_MS
            var checkedRoute = false
            while (SystemClock.elapsedRealtime() < deadline) {
                track.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
                if (!checkedRoute && track.playbackHeadPosition > 0) {
                    checkedRoute = true
                    val route = routeKey(track)
                    if (route == null || store.get(route) != null) return
                }
                samples += MixerBlockEstimator.HeadSample(SystemClock.elapsedRealtimeNanos(), track.playbackHeadPosition.toLong())
                SystemClock.sleep(POLL_MS)
            }
            val route = routeKey(track) ?: return
            val blockMs = MixerBlockEstimator.estimateBlockMs(samples, RATE) ?: return
            if (store.put(route, blockMs)) Diagnostics.record(Diagnostics.LEVEL_INFO, "audio", "mixer block measured silently ${formatMs(blockMs)} ms on $route")
        } catch (t: Throwable) {
            Diagnostics.record(Diagnostics.LEVEL_WARN, "audio", "silent mixer probe failed: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            running.set(false)
        }
    }
}
