package com.amwolfstein.sistrum.analysis

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureLog
import com.amwolfstein.sistrum.player.DecodeFailureSink
import java.util.concurrent.CancellationException
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.amwolfstein.sistrum.player.decoders.DecoderProvider
import com.amwolfstein.sistrum.player.decoders.DecoderRegistry
import com.amwolfstein.sistrum.player.decoders.FormatKey
import com.amwolfstein.sistrum.player.decoders.ProcessCodecProbe
import com.amwolfstein.sistrum.player.decoders.defaultDecoderProviders
import com.amwolfstein.sistrum.player.decoders.TableDecoderRegistry
import com.amwolfstein.sistrum.player.decoders.DefaultDecoderTable
import com.amwolfstein.sistrum.player.media3.ProviderExtractorsFactory
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import com.amwolfstein.sistrum.player.media3.replacePlatformAudioRenderer

/**
 * Decodes one audio file into interleaved float PCM using the same extractors and
 * renderers as playback, but with its own ExoPlayer and a capture [AudioSink]: no
 * AudioTrack, no focus, no media session. Each [decode] runs on a fresh pair of
 * [HandlerThread]s — an application thread and a playback thread, both started at
 * [threadPriority] so the renderers and consumer run at our priority instead of
 * ExoPlayer's audio priority — and blocks the caller until the file is fully
 * decoded, fails, or [isCancelled] turns true (checked per buffer).
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
internal open class OfflineDecoder(
    private val context: Context,
    private val providers: List<DecoderProvider> = defaultDecoderProviders(ProcessCodecProbe),
    private val registry: DecoderRegistry = TableDecoderRegistry(DefaultDecoderTable, providers),
    private val threadPriority: Int = Process.THREAD_PRIORITY_BACKGROUND,
    private val decodeFailures: DecodeFailureSink = DecodeFailureLog.forContext(context),
) : PcmDecoder {

    /** The [HandlerThread]s of the most recent [decode]: application first, playback second. */
    @Volatile
    internal var decodeThreads: List<HandlerThread> = emptyList()
        private set

    /**
     * Blocks the caller until the file is fully decoded, fails, or [isCancelled] turns
     * true (checked per buffer).
     */
    override fun decode(
        path: String,
        key: FormatKey?,
        consumer: PcmConsumer,
        isCancelled: () -> Boolean,
        startUs: Long,
    ): DecodeOutcome {
        return recordOutcome(path, key, decodeFile(path, key, consumer, isCancelled, startUs))
    }

    /** Records only failures; the existing analysis job still receives the same typed outcome. */
    internal fun recordOutcome(path: String, key: FormatKey?, result: DecodeOutcome): DecodeOutcome {
        if (result is DecodeOutcome.Failed) {
            try {
                decodeFailures.record(DecodeFailureEntry(
                    System.currentTimeMillis(), File(path).name, key?.format.orEmpty(), key?.codec.orEmpty(),
                    result.providerId ?: "unknown", result.reason,
                ))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("OfflineDecoder", "${result.providerId ?: "unknown"}: could not record decode failure", error)
            }
        }
        return result
    }

    internal fun extractorsFactory(
        recorder: ProviderRecorder,
        base: ExtractorsFactory = DefaultExtractorsFactory(),
    ): ProviderExtractorsFactory = ProviderExtractorsFactory(providers, base, recorder)

    internal fun audioRenderers(sink: AudioSink, handler: Handler, listener: AudioRendererEventListener): List<Renderer> =
        providers.flatMap { it.audioRenderers(context, sink, handler, listener) }

    internal fun failure(error: Throwable, providerId: String?): DecodeOutcome {
        if (error is CancellationException) return DecodeOutcome.Cancelled
        val cause = generateSequence(error) { it.cause }.last()
        if (cause is CancellationException) return DecodeOutcome.Cancelled
        return DecodeOutcome.Failed(providerId, cause.message ?: cause.javaClass.simpleName)
    }

    internal open fun decodeFile(
        path: String,
        key: FormatKey?,
        consumer: PcmConsumer,
        isCancelled: () -> Boolean,
        startUs: Long,
    ): DecodeOutcome {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "OfflineDecoder.decode must not run on the main thread"
        }
        if (key != null) {
            val reason = registry.skipReason(key)
            if (reason != null || registry.resolve(key) == null) {
                return DecodeOutcome.Unsupported(reason ?: "no provider for $key")
            }
        }
        val appThread = HandlerThread("sistrum-offline-decode", threadPriority).apply { start() }
        val playbackThread = HandlerThread("sistrum-offline-playback", threadPriority).apply { start() }
        val appLooper = appThread.looper
        val playbackLooper = playbackThread.looper
        decodeThreads = listOf(appThread, playbackThread)
        val handler = Handler(appLooper)
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<DecodeOutcome?>()
        val cancelRequested = AtomicBoolean(false)
        val playerRef = AtomicReference<ExoPlayer?>()
        handler.post {
            runDecodeOnThread(
                appThread,
                playbackThread,
                appLooper,
                playbackLooper,
                handler,
                latch,
                outcome,
                path,
                consumer,
                isCancelled,
                cancelRequested,
                playerRef,
                startUs,
                key?.let { registry.resolve(it)?.id },
            )
        }
        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            cancelRequested.set(true)
            playerRef.get()?.let { player -> handler.post { player.stop() } }
            return DecodeOutcome.Failed(null, "decode interrupted")
        }
        return outcome.get() ?: DecodeOutcome.Failed(null, "decode ended without an outcome")
    }

    /** Builds, runs and releases one ExoPlayer on the two decode threads; completes [outcome] exactly once. */
    private fun runDecodeOnThread(
        appThread: HandlerThread,
        playbackThread: HandlerThread,
        appLooper: Looper,
        playbackLooper: Looper,
        handler: Handler,
        latch: CountDownLatch,
        outcome: AtomicReference<DecodeOutcome?>,
        path: String,
        consumer: PcmConsumer,
        isCancelled: () -> Boolean,
        cancelRequested: AtomicBoolean,
        playerRef: AtomicReference<ExoPlayer?>,
        startUs: Long,
        selectedProviderId: String?,
    ) {
        val recorder = ProviderRecorder()
        val uriString = Uri.fromFile(File(path)).toString()
        val cancelled = AtomicBoolean(false)
        val lastProgressMs = AtomicLong(SystemClock.elapsedRealtime())

        val sink = CaptureAudioSink(
            consumer = PcmConsumer { samples, frames, rate, channels ->
                lastProgressMs.set(SystemClock.elapsedRealtime())
                if (isCancelled() || cancelRequested.get()) {
                    cancelled.set(true)
                    handler.post { playerRef.get()?.stop() }
                } else {
                    consumer.accept(samples, frames, rate, channels)
                }
            },
            startPositionUs = startUs,
        )

        fun complete(result: DecodeOutcome) {
            if (!outcome.compareAndSet(null, result)) return
            try {
                playerRef.get()?.release()
            } catch (t: Throwable) {
                Log.w("OfflineDecoder", "${recorder.providerFor(uriString) ?: selectedProviderId}: release failed", t)
            }
            playbackThread.quitSafely()
            appThread.quitSafely()
            latch.countDown()
        }

        val stallWatchdog = object : Runnable {
            override fun run() {
                if (outcome.get() != null) return
                if (SystemClock.elapsedRealtime() - lastProgressMs.get() > STALL_TIMEOUT_MS) {
                    complete(DecodeOutcome.Failed(recorder.providerFor(uriString) ?: selectedProviderId, "decode stalled"))
                } else {
                    handler.postDelayed(this, STALL_CHECK_INTERVAL_MS)
                }
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> handler.post { complete(buildCompleted(sink, recorder.providerFor(uriString) ?: selectedProviderId)) }
                    Player.STATE_IDLE -> if (cancelled.get() || cancelRequested.get()) handler.post { complete(DecodeOutcome.Cancelled) }
                }
            }

            /** An audio track no renderer can decode (e.g. no codec on this device) would "play" silently forever. */
            override fun onTracksChanged(tracks: Tracks) {
                val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audio.isNotEmpty() && audio.none { it.isSelected }) {
                    val format = audio.first().getTrackFormat(0).sampleMimeType
                    handler.post { complete(DecodeOutcome.Failed(recorder.providerFor(uriString) ?: selectedProviderId, "no decoder for $format")) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handler.post { complete(failure(error, recorder.providerFor(uriString) ?: selectedProviderId)) }
            }
        }

        try {
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioOutputPlaybackParams: Boolean,
                ): AudioSink = sink

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
                    replacePlatformAudioRenderer(
                        context,
                        getCodecAdapterFactory(),
                        mediaCodecSelector,
                        enableDecoderFallback,
                        audioSink,
                        eventHandler,
                        eventListener,
                        out,
                    )
                    out.addAll(audioRenderers(audioSink, eventHandler, eventListener))
                }
            }
            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    DefaultTrackSelector.Parameters.Builder()
                        .setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                .setAudioOffloadMode(
                                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                                )
                                .build(),
                        )
                        .build(),
                )
            }
            val p = ExoPlayer.Builder(context)
                .setLooper(appLooper)
                .setPlaybackLooper(playbackLooper)
                .setTrackSelector(trackSelector)
                .setHandleAudioBecomingNoisy(false)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context, extractorsFactory(recorder)),
                )
                .experimentalSetDynamicSchedulingEnabled(false)
                .setRenderersFactory(renderersFactory)
                .build()
            playerRef.set(p)
            p.addListener(listener)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(path)))
            p.setMediaItem(mediaItem, if (startUs > 0L) startUs / 1000L else C.TIME_UNSET)
            p.prepare()
            p.playWhenReady = true
            handler.postDelayed(stallWatchdog, STALL_CHECK_INTERVAL_MS)
        } catch (t: Throwable) {
            complete(failure(t, recorder.providerFor(uriString) ?: selectedProviderId))
        }
    }

    private fun buildCompleted(sink: CaptureAudioSink, providerId: String?): DecodeOutcome =
        if (sink.formatChanged) {
            DecodeOutcome.Failed(providerId, "audio format changed mid-stream")
        } else {
            DecodeOutcome.Completed(sink.sampleRate, sink.channelCount, sink.framesWritten, providerId)
        }

    private companion object {
        const val STALL_TIMEOUT_MS = 30_000L
        const val STALL_CHECK_INTERVAL_MS = 1_000L
    }
}
