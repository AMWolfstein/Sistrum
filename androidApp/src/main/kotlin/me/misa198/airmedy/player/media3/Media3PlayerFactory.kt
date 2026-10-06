package me.misa198.airmedy.player.media3

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide playback [HandlerThread], lazily started and shared by every
 * [Media3PlayerFactory] so all ExoPlayers of the app run their listeners on a
 * single looper (contract threading rule).
 */
private val playbackThread: HandlerThread by lazy {
    HandlerThread("sistrum-player").apply { start() }
}

/**
 * Creates single-item [ExoPlayer]s on the shared playback looper and marshals
 * synchronous seam calls onto it. Live players are reference-counted so tests can
 * assert every created player is released (FR-086).
 */
@OptIn(UnstableApi::class)
internal class Media3PlayerFactory(
    private val context: Context,
    private val audioProcessors: () -> Array<AudioProcessor> = { emptyArray() },
) {

    private val looper: Looper = playbackThread.looper
    private val handler = Handler(looper)
    private val livePlayerCount = AtomicInteger(0)

    /** Number of players created by [newPlayer] and not yet released. */
    val livePlayers: Int get() = livePlayerCount.get()

    /**
     * Runs [block] on the playback looper and returns its result, blocking the caller
     * for up to [timeoutMs]. Runs [block] inline when already on the looper, rethrows
     * its exception, and throws [IllegalStateException] on timeout.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> call(timeoutMs: Long = 5_000, block: () -> T): T {
        if (Looper.myLooper() == looper) return block()
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
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("timed out waiting for the sistrum-player looper")
        }
        error.get()?.let { throw it }
        return value.get() as T
    }

    /**
     * Builds a single-item player: offload disabled, media-usage attributes with no
     * audio focus or becoming-noisy handling. Must run on the looper (call through
     * [call]).
     */
    fun newPlayer(): ExoPlayer {
        check(Looper.myLooper() == looper) { "newPlayer must run on the playback looper" }
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                DefaultTrackSelector.Parameters.Builder()
                    .setAudioOffloadPreferences(
                        TrackSelectionParameters.AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(
                                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                            )
                            .build()
                    )
                    .build()
            )
        }
        val processors = audioProcessors()
        val renderersFactory = if (processors.isEmpty()) {
            null
        } else {
            object : DefaultRenderersFactory(context) {
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
        }
        val builder = ExoPlayer.Builder(context)
            .setLooper(looper)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(attributes, false)
            .setHandleAudioBecomingNoisy(false)
            // On by default in 1.11: the playback loop then sleeps ~250 ms between updates, so the position the
            // coordinator reads at its 200 ms tick goes stale (FR-012, lyrics sync). Measured on the CPH2307 (T020).
            .experimentalSetDynamicSchedulingEnabled(false)
        val player = (if (renderersFactory != null) builder.setRenderersFactory(renderersFactory) else builder)
            .build()
        livePlayerCount.incrementAndGet()
        return player
    }

    /** Releases [player] on the playback looper and decrements the live count. */
    fun release(player: ExoPlayer) {
        call {
            player.release()
            livePlayerCount.decrementAndGet()
        }
    }
}
