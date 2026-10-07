package me.misa198.airmedy.player.media3

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import me.misa198.airmedy.player.GlobalDspConfig
import me.misa198.airmedy.player.decoders.DecoderProvider
import me.misa198.airmedy.player.decoders.ProcessCodecProbe
import me.misa198.airmedy.player.decoders.defaultDecoderProviders
import me.misa198.airmedy.player.dsp.EqualizerProcessor
import me.misa198.airmedy.player.dsp.GainProcessor
import me.misa198.airmedy.player.dsp.PreampProcessor
import me.misa198.airmedy.player.dsp.StereoWidthProcessor

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
 *
 * Every player built with [dspChainEnabled] runs its own DSP chain
 * `GainProcessor -> StereoWidthProcessor -> EqualizerProcessor -> PreampProcessor` (in that order)
 * through a custom [AudioProcessorChain] set on the sink, with float output disabled so the chain
 * always applies (hi-res input is never bypassed). [setDsp] fans a [GlobalDspConfig] out to every
 * live chain and seeds chains created later.
 */
@OptIn(UnstableApi::class)
internal class Media3PlayerFactory(
    private val context: Context,
    private val providers: List<DecoderProvider> = defaultDecoderProviders(ProcessCodecProbe),
    /** When false, players are built with no DSP chain (CPU baseline only). */
    private val dspChainEnabled: Boolean = true,
    /** Extra processors appended AFTER the DSP chain (test taps). */
    private val audioProcessors: () -> Array<AudioProcessor> = { emptyArray() },
) {

    private val looper: Looper = playbackThread.looper
    private val handler = Handler(looper)
    private val livePlayerCount = AtomicInteger(0)
    private val liveDspChainMap = ConcurrentHashMap<ExoPlayer, DspChain>()

    @Volatile
    private var dspConfig: GlobalDspConfig = GlobalDspConfig()

    /** Serializes the store-and-apply of a DSP config with chain registration/release (FR-050). */
    private val dspLock = Any()

    /** Number of players created by [newPlayer] and not yet released. */
    val livePlayers: Int get() = livePlayerCount.get()

    /** Number of per-player DSP chains registered by [newPlayer] and not yet released. */
    internal val liveDspChains: Int get() = liveDspChainMap.size

    /**
     * Applies [config] to every live DSP chain. Callable from any thread: the config store and the
     * fan-out happen atomically under [dspLock], so a concurrent [newPlayer] either registers its
     * chain before this fan-out (and is applied here) or after (and reads the stored config itself),
     * never interleaving a stale read with a newer apply.
     */
    fun setDsp(config: GlobalDspConfig) {
        synchronized(dspLock) {
            dspConfig = config
            liveDspChainMap.values.forEach { it.apply(config) }
        }
    }

    /** Names the provider whose extractor accepted a URI, for [Media3Engine] errors. */
    val providerRecorder = ProviderRecorder()

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
        val chain = buildDspChain(audioProcessors())
        val providerRenderers = providers.flatMap { it.audioRenderers(context) }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(chain)
                .setEnableFloatOutput(false)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>,
            ) {
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out,
                )
                out.addAll(providerRenderers)
            }
        }
        val builder = ExoPlayer.Builder(context)
            .setLooper(looper)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(attributes, false)
            .setHandleAudioBecomingNoisy(false)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    context,
                    ProviderExtractorsFactory(providers, recorder = providerRecorder),
                )
            )
            // On by default in 1.11: the playback loop then sleeps ~250 ms between updates, so the position the
            // coordinator reads at its 200 ms tick goes stale (FR-012, lyrics sync). Measured on the CPH2307 (T020).
            .experimentalSetDynamicSchedulingEnabled(false)
        val player = builder.setRenderersFactory(renderersFactory).build()
        if (dspChainEnabled) {
            synchronized(dspLock) {
                liveDspChainMap[player] = chain
                chain.apply(dspConfig)
            }
        }
        livePlayerCount.incrementAndGet()
        return player
    }

    /** Releases [player] on the playback looper, deregisters its chain and decrements the live count. */
    fun release(player: ExoPlayer) {
        call {
            player.release()
            synchronized(dspLock) { liveDspChainMap.remove(player) }
            livePlayerCount.decrementAndGet()
        }
    }

    /** Builds the per-player [DspChain]: the DSP processors (when enabled) followed by [extraProcessors]. */
    private fun buildDspChain(extraProcessors: Array<AudioProcessor>): DspChain {
        if (!dspChainEnabled) return DspChain(extraProcessors, null, null, null)
        val width = StereoWidthProcessor()
        val equalizer = EqualizerProcessor()
        val preamp = PreampProcessor()
        return DspChain(
            arrayOf<AudioProcessor>(GainProcessor(), width, equalizer, preamp) + extraProcessors,
            width,
            equalizer,
            preamp,
        )
    }
}

/**
 * A per-player [AudioProcessorChain] exposing the DSP processors (Gain → Width → EQ → Preamp,
 * then any extra tap processors) as the sink's audio processors. It reports no playback speed and
 * no skip-silence, so the default silence-skipping and speed processors are never appended.
 */
@OptIn(UnstableApi::class)
private class DspChain(
    private val processors: Array<AudioProcessor>,
    private val width: StereoWidthProcessor?,
    private val equalizer: EqualizerProcessor?,
    private val preamp: PreampProcessor?,
) : AudioProcessorChain {

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        PlaybackParameters.DEFAULT

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration

    override fun getSkippedOutputFrameCount(): Long = 0L

    /** Fans a [GlobalDspConfig] out to this chain's processors (safe from any thread). */
    fun apply(config: GlobalDspConfig) {
        width?.setWidth(config.stereoWidth)
        equalizer?.setGains(config.eqBandGainsDb.copyOf())
        preamp?.setPreampDb(config.preampGainDb)
    }
}
