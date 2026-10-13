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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.amwolfstein.sistrum.player.PlaybackController
import com.amwolfstein.sistrum.player.PlaybackRequest
import com.amwolfstein.sistrum.sync.AndroidLibrarySyncStore
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.metadataObject
import com.amwolfstein.sistrum.ui.libraryAlphabeticalComparator

data class ArtistDetailsUiState(
    val artist: LibraryArtist? = null,
    val albums: List<LibraryAlbum> = emptyList(),
    val tracks: List<LibraryTrack> = emptyList(),
    internal val allArtists: List<LibraryArtist> = emptyList(),
    internal val allAlbums: List<LibraryAlbum> = emptyList(),
) {
    /**
     * Tracks per artist id. Kept out of the constructor so it isn't part of equals(): the
     * StateFlow and remember() compare states on the main thread. The ViewModel forces it on
     * Dispatchers.Default (see indexByKeys).
     */
    internal val tracksByArtistId: Map<String, List<LibraryTrack>> by lazy { tracks.indexByKeys { it.artistIds() } }
}

internal class ArtistDetailsViewModel(
    syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
) : ViewModel() {
    class Factory(
        private val store: AndroidLibrarySyncStore,
        private val playback: PlaybackController,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ArtistDetailsViewModel(store, playback) as T
    }

    val uiState: StateFlow<ArtistDetailsUiState> = combine(
        syncStore.artists,
        syncStore.albums,
        syncStore.tracks,
    ) { artists, albums, tracks ->
        ArtistDetailsUiState(
            tracks = tracks,
            allArtists = artists,
            allAlbums = albums,
        )
    }
        // Build the lazy index here, on Dispatchers.Default, not on first read in composition.
        .onEach { it.tracksByArtistId }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistDetailsUiState())

    fun play(artistId: String, shuffle: Boolean) {
        val tracks = artistDetailsUiStateFor(uiState.value, artistId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    fun orderedTrackIds(artistId: String): List<String> =
        artistDetailsUiStateFor(uiState.value, artistId).tracks.map(LibraryTrack::id)
}

internal fun artistDetailsUiStateFor(
    state: ArtistDetailsUiState,
    artistId: String,
): ArtistDetailsUiState {
    val artist = state.allArtists.firstOrNull { it.id == artistId }
    val artistTracks = state.tracksByArtistId[artistId].orEmpty()
    val tracksByAlbumId = artistTracks.groupBy { it.albumIdentifier() }
    val albums = state.allAlbums
        .filter { it.id in tracksByAlbumId }
        .sortedWith(
            compareBy<LibraryAlbum, String>(libraryAlphabeticalComparator) { it.sortTitle }
                .thenBy(libraryAlphabeticalComparator) { it.sortArtist }
                .thenBy { it.id },
        )
    val tracks = albums.flatMap { album ->
        tracksByAlbumId[album.id].orEmpty().sortedWith(albumTrackComparator)
    }
    return ArtistDetailsUiState(artist = artist, albums = albums, tracks = tracks)
}

private val albumTrackComparator = compareBy<LibraryTrack> {
    if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE
}.thenBy {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.syncOrder }

private fun LibraryTrack.albumIdentifier(): String? = albumId.takeIf(String::isNotBlank)
    ?: metadataObject()?.get("album")
        ?.let { it as? JsonObject }
        ?.get("id")
        ?.let { it as? JsonPrimitive }
        ?.content
        ?.takeIf(String::isNotBlank)

private fun LibraryTrack.artistIds(): Set<String> = metadataObject()
    ?.get("artists")
    ?.let { it as? JsonArray }
    .orEmpty()
    .mapNotNull { value ->
        (value as? JsonObject)
            ?.get("id")
            ?.let { it as? JsonPrimitive }
            ?.content
            ?.takeIf(String::isNotBlank)
    }
    .toSet()
