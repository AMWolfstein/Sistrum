package me.misa198.airmedy.player.media3

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.engine.ItemGain
import me.misa198.airmedy.player.engine.PlayerEngine

/**
 * Media3 single-player engine (T020). Every seam call marshals through
 * [Media3PlayerFactory.call] onto the process-wide playback looper; player listeners run
 * on that looper and append to a thread-safe buffer that [pollEvents] drains, so
 * [events] stays empty. [EngineEvent.OutputStarted] is emitted once on the first advance
 * of the rendered position (FR-090); [EngineEvent.Ended] and [EngineEvent.Error] are
 * edge events from the listener. A failed [prepare] throws after releasing the player
 * it created (FR-086).
 */
@OptIn(UnstableApi::class)
internal class Media3Engine(
    private val factory: Media3PlayerFactory,
) : PlayerEngine {

    override val kind: EngineKind = EngineKind.Media3

    override val events: Flow<EngineEvent> = emptyFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && endedArmed) {
                endedArmed = false
                pendingEvents += EngineEvent.Ended
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
            val p = player ?: return
            val mediaId = mediaItem?.mediaId
            val incoming = if (mediaId != null) preloadedByMediaId[mediaId] else null
            if (incoming != null) {
                pendingEvents += EngineEvent.GaplessAdvanced(incoming)
            }
            endedArmed = true
            rawMs = p.currentPosition.coerceAtLeast(0L)
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = rawMs
            val index = p.currentMediaItemIndex
            if (index > 0) {
                p.removeMediaItems(0, index)
            }
            preloadedByMediaId.clear()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (prepared) {
                val extension = currentItem?.audioPath?.substringAfterLast('.', "") ?: ""
                val format = player?.audioFormat?.sampleMimeType ?: extension.ifEmpty { "unknown" }
                pendingEvents += EngineEvent.Error("platform", format, error)
            }
        }
    }

    private var player: ExoPlayer? = null
    private var currentItem: PlaybackItem? = null
    private var prepared = false
    private var awaitingOutput = false
    private var outputBaseMs = 0L
    private var endedArmed = false
    private var rawMs = 0L
    private var rawAtMs = 0L
    private var lastReturnedMs = 0L
    private val preloadedByMediaId = mutableMapOf<String, PlaybackItem>()
    @Volatile
    private var closed = false
    private val pendingEvents = ConcurrentLinkedQueue<EngineEvent>()

    override suspend fun prepare(
        item: PlaybackItem,
        gain: ItemGain,
        startPositionMs: Long,
        startPaused: Boolean,
    ) {
        check(!closed) { "engine is closed" }
        releaseCurrentPlayer()
        val candidate = try {
            factory.call {
                val p = factory.newPlayer()
                player = p
                currentItem = item
                prepared = false
                endedArmed = false
                p.addListener(listener)
                p.setMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(File(item.audioPath)))
                        .setMediaId(item.trackId)
                        .build(),
                    startPositionMs,
                )
                p.playWhenReady = false
                p.prepare()
                p
            }
        } catch (error: Throwable) {
            releaseCurrentPlayer()
            throw error
        }
        try {
            awaitReady(candidate)
        } catch (error: Throwable) {
            releaseCurrentPlayer()
            throw error
        }
        factory.call {
            prepared = true
            endedArmed = true
            rawMs = candidate.currentPosition.coerceAtLeast(0L)
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = rawMs
        }
        if (!startPaused) play()
    }

    private suspend fun awaitReady(candidate: ExoPlayer) {
        val deadline = SystemClock.elapsedRealtime() + PREPARE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val error = factory.call { candidate.playerError }
            if (error != null) throw IOException("prepare failed: ${error.errorCodeName}", error)
            val state = factory.call { candidate.playbackState }
            if (state == Player.STATE_READY) return
            delay(PREPARE_POLL_MS)
        }
        throw IllegalStateException("timed out waiting for playback to become ready")
    }

    override suspend fun preloadNext(item: PlaybackItem, gain: ItemGain) {
        val file = File(item.audioPath)
        val added = factory.call {
            val p = player ?: return@call false
            clearPreloadedOnLooper(p)
            p.addMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setMediaId(item.trackId)
                    .build(),
            )
            preloadedByMediaId[item.trackId] = item
            true
        }
        if (added && !file.exists()) {
            clearPreloaded()
            throw IOException("preloadNext: audio file does not exist: ${item.audioPath}")
        }
    }

    override fun clearPreloaded() {
        factory.call {
            val p = player ?: return@call
            clearPreloadedOnLooper(p)
        }
    }

    override fun hasPreloaded(): Boolean = factory.call {
        val p = player ?: return@call false
        val index = p.currentMediaItemIndex
        index >= 0 && index + 1 < p.mediaItemCount
    }

    override fun play() {
        factory.call {
            val p = player ?: return@call
            p.playWhenReady = true
            awaitingOutput = true
            outputBaseMs = p.currentPosition
        }
    }

    override fun pause() {
        factory.call {
            val p = player ?: return@call
            p.playWhenReady = false
            awaitingOutput = false
            rawMs = p.currentPosition.coerceAtLeast(0L)
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = rawMs
        }
    }

    override fun seekTo(positionMs: Long) {
        factory.call {
            val p = player ?: return@call
            p.seekTo(positionMs)
            endedArmed = true
            if (awaitingOutput) outputBaseMs = positionMs
            rawMs = positionMs
            rawAtMs = SystemClock.elapsedRealtime()
            lastReturnedMs = positionMs
        }
    }

    override fun positionMs(): Long = factory.call {
        val p = player ?: return@call 0L
        val now = SystemClock.elapsedRealtime()
        val current = p.currentPosition.coerceAtLeast(0L)
        if (current != rawMs) {
            rawMs = current
            rawAtMs = now
        }
        if (!p.isPlaying) {
            lastReturnedMs = rawMs
            return@call rawMs
        }
        val speed = p.playbackParameters.speed
        val extrapolated = rawMs + (min(now - rawAtMs, MAX_EXTRAPOLATION_MS) * speed).toLong()
        val result = max(lastReturnedMs, extrapolated)
        val duration = p.duration
        val clamped = if (duration != C.TIME_UNSET && duration > 0L) {
            result.coerceIn(0L, duration)
        } else {
            result.coerceAtLeast(0L)
        }
        lastReturnedMs = clamped
        clamped
    }

    override fun durationMs(): Long = factory.call {
        val p = player ?: return@call 0L
        val duration = p.duration
        if (duration == C.TIME_UNSET) 0L else duration
    }

    override fun beginCrossfade(durationMs: Long): Boolean = false

    override fun isCrossfading(): Boolean = false

    override fun snapCrossfade() = Unit

    override fun setFocusGain(gain: Float) {
        factory.call { player?.volume = gain }
    }

    override fun setDsp(settings: EqualizerSettings) = Unit

    override fun setGains(current: ItemGain, preloaded: ItemGain?) = Unit

    override fun setNormalization(settings: NormalizationSettings) = Unit

    override fun pollEvents(): List<EngineEvent> = factory.call {
        val p = player
        if (p != null && awaitingOutput && p.isPlaying && p.currentPosition > outputBaseMs) {
            awaitingOutput = false
            pendingEvents += EngineEvent.OutputStarted
        }
        val drained = mutableListOf<EngineEvent>()
        while (true) {
            val event = pendingEvents.poll() ?: break
            drained += event
        }
        drained
    }

    override fun close() {
        if (closed) return
        closed = true
        releaseCurrentPlayer()
        factory.call { pendingEvents.clear() }
    }

    private fun releaseCurrentPlayer() {
        val previous = factory.call {
            val p = player
            player = null
            currentItem = null
            prepared = false
            awaitingOutput = false
            endedArmed = false
            preloadedByMediaId.clear()
            p
        }
        if (previous != null) factory.release(previous)
    }

    /** Removes every playlist item after the current one and forgets any preloaded mapping. */
    private fun clearPreloadedOnLooper(p: ExoPlayer) {
        val index = p.currentMediaItemIndex
        if (index >= 0 && index + 1 < p.mediaItemCount) {
            p.removeMediaItems(index + 1, p.mediaItemCount)
        }
        preloadedByMediaId.clear()
    }

    private companion object {
        const val PREPARE_TIMEOUT_MS = 10_000L
        const val PREPARE_POLL_MS = 10L
        const val MAX_EXTRAPOLATION_MS = 500L
    }
}
