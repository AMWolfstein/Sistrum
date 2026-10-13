package com.amwolfstein.sistrum.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import com.amwolfstein.sistrum.player.PlaybackController
import com.amwolfstein.sistrum.player.PlaybackRequest
import com.amwolfstein.sistrum.sync.AndroidLibrarySyncStore
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.durationMillis
import com.amwolfstein.sistrum.sync.metadataObject

data class AlbumDetailsUiState(
    val album: LibraryAlbum? = null,
    val tracks: List<LibraryTrack> = emptyList(),
    internal val albums: List<LibraryAlbum> = emptyList(),
) {
    /**
     * Tracks per album id. Kept out of the constructor so it isn't part of equals(): the
     * StateFlow and remember() compare states on the main thread. The ViewModel forces it on
     * Dispatchers.Default (see indexByKeys).
     */
    internal val tracksByAlbumId: Map<String, List<LibraryTrack>> by lazy { tracks.indexByKeys { it.albumIds() } }
}

internal class AlbumDetailsViewModel(syncStore: AndroidLibrarySyncStore, private val playbackController: PlaybackController) : ViewModel() {
    class Factory(private val store: AndroidLibrarySyncStore, private val playback: PlaybackController) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AlbumDetailsViewModel(store, playback) as T
    }
    val uiState: StateFlow<AlbumDetailsUiState> = combine(syncStore.albums, syncStore.tracks) { albums, tracks ->
        AlbumDetailsUiState(tracks = tracks, albums = albums)
    }
        // Build the lazy index here, on Dispatchers.Default, not on first read in composition.
        .onEach { it.tracksByAlbumId }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumDetailsUiState())

    fun play(albumId: String, shuffle: Boolean) {
        val tracks = albumDetailsUiStateFor(uiState.value, albumId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    fun playTrack(albumId: String, trackId: String) {
        val tracks = albumDetailsUiStateFor(uiState.value, albumId).tracks
        albumPlaybackRequestFor(tracks, trackId)?.let(playbackController::play)
    }
}

internal fun albumPlaybackRequestFor(tracks: List<LibraryTrack>, trackId: String): PlaybackRequest? {
    val startIndex = tracks.indexOfFirst { it.id == trackId }
    return startIndex.takeIf { it >= 0 }?.let { PlaybackRequest(tracks.map { it.id }, it) }
}

internal fun albumDetailsUiStateFor(state: AlbumDetailsUiState, albumId: String): AlbumDetailsUiState {
    val tracks = state.tracksByAlbumId[albumId].orEmpty().sortedWith(
        compareBy<LibraryTrack> { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.syncOrder },
    )
    return AlbumDetailsUiState(state.albums.firstOrNull { it.id == albumId }, tracks)
}

/** A track belongs to its albumId column and to the album id in its metadata (usually the same). */
private fun LibraryTrack.albumIds(): Set<String> = setOfNotNull(
    albumId.takeIf(String::isNotBlank),
    metadataObject()
        ?.get("album")
        ?.let { it as? kotlinx.serialization.json.JsonObject }
        ?.get("id")
        ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
        ?.content
        ?.takeIf(String::isNotBlank),
)

/** Sums the tracks' millisecond durations, converting to seconds once at the end so
 *  per-track truncation doesn't accumulate. */
internal fun albumTotalDurationSeconds(tracks: List<LibraryTrack>): Long =
    tracks.sumOf { track -> track.durationMillis() ?: 0L } / 1000

internal fun formatAlbumTotalDuration(
    totalSeconds: Long,
    day: (Long) -> String,
    hour: (Long) -> String,
    minute: (Long) -> String,
    second: (Long) -> String,
): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val days = seconds / 86_400L
    val hours = seconds % 86_400L / 3_600L
    val minutes = seconds % 3_600L / 60L
    val remainder = seconds % 60L
    return when {
        days > 0 -> "${day(days)} ${hour(hours)}"
        hours > 0 -> "${hour(hours)} ${minute(minutes)}"
        minutes > 0 -> minute(minutes)
        else -> second(remainder)
    }
}
