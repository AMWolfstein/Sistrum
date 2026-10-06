package me.misa198.airmedy.player

import kotlinx.coroutines.flow.MutableStateFlow
import me.misa198.airmedy.mood.MoodRadioTrack
import me.misa198.airmedy.sync.LibraryTrack

internal enum class TransportState { None, Buffering, Playing, Paused, Stopped, Error }

internal interface NowPlayingPort {
    fun publishNowPlaying(item: PlaybackItem, state: TransportState, positionMs: Long, durationMs: Long, activeQueueItemId: Long)
    fun setTransportState(state: TransportState, positionMs: Long, activeQueueItemId: Long)
    fun deactivate()
    suspend fun publishQueue(snapshot: PlaybackQueueSnapshot, window: List<QueueWindowEntry>)
    fun showForeground(item: PlaybackItem)
    fun updateNotification(item: PlaybackItem)
    fun stopForeground()
}

internal interface FocusPort { fun request(): Boolean; fun abandon() }

internal fun interface ListeningSink { fun offer(write: ListeningWrite): Boolean }

internal interface ScrobbleSink {
    fun startPlayback(trackId: String, positionMs: Long)
    fun seek(positionMs: Long)
    fun reportPlayback(item: PlaybackItem, positionMs: Long, durationMs: Long)
}

internal interface SessionStorePort {
    suspend fun load(): PlaybackSession?
    suspend fun save(session: PlaybackSession)
    suspend fun clear()
}

internal interface Clock { fun nowMs(): Long; fun elapsedMs(): Long }

internal interface PlaybackLog { fun d(message: String); fun w(message: String, error: Throwable? = null); fun e(message: String) }

internal interface LibraryPort {
    suspend fun activeAnalyses(): Map<String, TrackAnalysis>
    suspend fun tracks(): List<LibraryTrack>
    suspend fun disableNormalization()
    suspend fun libraryAnalysisEnabled(): Boolean
    suspend fun moodRadioTracks(): List<MoodRadioTrack>
}

internal class PlaybackFlows(
    val state: MutableStateFlow<PlaybackState>,
    val queueState: MutableStateFlow<PlaybackQueueSnapshot>,
    val crossfadeSeconds: MutableStateFlow<Int>,
    val blendArtworkDuringCrossfade: MutableStateFlow<Boolean>,
    val artworkCrossfade: MutableStateFlow<ArtworkCrossfadeTransition?>,
    val moodRadioActive: MutableStateFlow<Boolean>,
)
