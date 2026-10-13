package com.amwolfstein.sistrum.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.booleanOrNull
import com.amwolfstein.sistrum.player.PlaybackController
import com.amwolfstein.sistrum.player.PlaybackRequest
import com.amwolfstein.sistrum.sync.AndroidLibrarySyncStore
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.durationMillis
import com.amwolfstein.sistrum.sync.metadataObject
import com.amwolfstein.sistrum.sync.PlaylistMutation
import com.amwolfstein.sistrum.sync.PlaylistMutationOperation
import com.amwolfstein.sistrum.sync.PlaylistMutationPayload
import java.util.UUID
import kotlinx.coroutines.launch

internal data class PlaylistDetailsUiState(
    val playlist: LibraryPlaylist? = null,
    val tracks: List<LibraryTrack> = emptyList(),
    val artworkPaths: List<String> = emptyList(),
    val customArtworkPath: String? = null,
    internal val playlists: List<LibraryPlaylist> = emptyList(),
    internal val allTracks: List<LibraryTrack> = emptyList(),
    internal val artworkPathByKey: Map<String, String> = emptyMap(),
    internal val favoritesMetadata: String = "{}",
) {
    /** Favorited track ids in library order. Outside equals() and forced on Dispatchers.Default by the ViewModel. */
    internal val favoriteTrackIds: List<String> by lazy { allTracks.filter(LibraryTrack::isFavorite).map(LibraryTrack::id) }
}

internal class PlaylistDetailsViewModel(
    private val context: Context,
    private val syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val store: AndroidLibrarySyncStore,
        private val playback: PlaybackController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistDetailsViewModel(context, store, playback) as T
    }

    val uiState: StateFlow<PlaylistDetailsUiState> = combine(
        syncStore.playlists,
        syncStore.tracks,
        syncStore.artworkPaths,
        syncStore.favoritesMetadata,
    ) { playlists, tracks, artworkPaths, favoritesMetadata ->
        PlaylistDetailsUiState(
            playlists = playlists,
            allTracks = tracks,
            artworkPathByKey = artworkPaths,
            favoritesMetadata = favoritesMetadata,
        )
    }
        // Build the lazy index here, on Dispatchers.Default, not on first read in composition.
        .onEach { it.favoriteTrackIds }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailsUiState())

    fun play(playlistId: String, shuffle: Boolean) {
        val tracks = playlistDetailsUiStateFor(uiState.value, playlistId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    fun playTrack(playlistId: String, trackId: String) {
        val tracks = playlistDetailsUiStateFor(uiState.value, playlistId).tracks
        albumPlaybackRequestFor(tracks, trackId)?.let(playbackController::play)
    }

    fun removeTrack(playlistId: String, trackId: String) {
        if (playlistId == FavoritesPlaylistId || trackId.isBlank()) return
        viewModelScope.launch {
            runPlaylistWrite(context) {
                syncStore.queuePlaylistMutation(
                    PlaylistMutation(
                        mutationId = UUID.randomUUID().toString(),
                        playlistId = playlistId,
                        operation = PlaylistMutationOperation.REMOVE_TRACK,
                        updatedAt = System.currentTimeMillis(),
                        payload = PlaylistMutationPayload(trackId = trackId),
                    ),
                )
            }
        }
    }

    fun moveTrack(playlistId: String, trackId: String, previousTrackId: String?, nextTrackId: String?) {
        if (playlistId == FavoritesPlaylistId || trackId.isBlank()) return
        viewModelScope.launch {
            runPlaylistWrite(context) {
                syncStore.queuePlaylistMutation(
                    PlaylistMutation(
                        mutationId = UUID.randomUUID().toString(),
                        playlistId = playlistId,
                        operation = PlaylistMutationOperation.MOVE_TRACK,
                        updatedAt = System.currentTimeMillis(),
                        payload = PlaylistMutationPayload(trackId = trackId, previousTrackId = previousTrackId, nextTrackId = nextTrackId),
                    ),
                )
            }
        }
    }
}

internal fun playlistMoveAnchors(trackIds: List<String>, trackId: String): Pair<String?, String?> {
    val index = trackIds.indexOf(trackId)
    return trackIds.getOrNull(index - 1) to trackIds.getOrNull(index + 1)
}

internal fun playlistDetailsUiStateFor(
    state: PlaylistDetailsUiState,
    playlistId: String,
): PlaylistDetailsUiState {
    val basePlaylist = playlistsWithFavorites(state.playlists, favoritesMetadata = state.favoritesMetadata).firstOrNull { it.id == playlistId }
        ?: return PlaylistDetailsUiState()
    val tracksById = state.allTracks.associateBy { it.id }
    val playlist = if (basePlaylist.id == FavoritesPlaylistId) {
        basePlaylist.copy(trackIds = state.favoriteTrackIds)
    } else {
        basePlaylist
    }
    val tracks = playlist.trackIds.mapNotNull(tracksById::get)
    return PlaylistDetailsUiState(
        playlist = playlist,
        tracks = tracks,
        artworkPaths = playlistArtworkPaths(playlist, state.allTracks, state.artworkPathByKey),
        customArtworkPath = playlistManualArtworkPath(playlist, state.artworkPathByKey),
    )
}

internal fun LibraryTrack.isFavorite(): Boolean = (metadataObject()?.get("is_favorite") as? kotlinx.serialization.json.JsonPrimitive)
    ?.booleanOrNull == true

/** Sums the tracks' millisecond durations, converting to seconds once at the end so
 *  per-track truncation doesn't accumulate. */
internal fun playlistTotalDurationSeconds(tracks: List<LibraryTrack>): Long =
    tracks.sumOf { track -> track.durationMillis() ?: 0L } / 1000

internal fun formatPlaylistTotalDuration(
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
