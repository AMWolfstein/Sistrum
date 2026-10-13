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
import com.amwolfstein.sistrum.sync.LibraryGenre
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.metadataObject
import com.amwolfstein.sistrum.ui.libraryAlphabeticalComparator

data class GenreDetailsUiState(
    val genre: LibraryGenre? = null,
    val albums: List<LibraryAlbum> = emptyList(),
    val tracks: List<LibraryTrack> = emptyList(),
    internal val allGenres: List<LibraryGenre> = emptyList(),
    internal val allAlbums: List<LibraryAlbum> = emptyList(),
) {
    /**
     * Tracks per genre id. Kept out of the constructor so it isn't part of equals(): the
     * StateFlow and remember() compare states on the main thread. The ViewModel forces it on
     * Dispatchers.Default (see indexByKeys).
     */
    internal val tracksByGenreId: Map<String, List<LibraryTrack>> by lazy { tracks.indexByKeys { it.genreIds() } }
}

internal class GenreDetailsViewModel(
    syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
) : ViewModel() {
    class Factory(
        private val store: AndroidLibrarySyncStore,
        private val playback: PlaybackController,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GenreDetailsViewModel(store, playback) as T
    }

    val uiState: StateFlow<GenreDetailsUiState> = combine(
        syncStore.genres,
        syncStore.albums,
        syncStore.tracks,
    ) { genres, albums, tracks ->
        GenreDetailsUiState(
            tracks = tracks,
            allGenres = genres,
            allAlbums = albums,
        )
    }
        // Build the lazy index here, on Dispatchers.Default, not on first read in composition.
        .onEach { it.tracksByGenreId }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreDetailsUiState())

    fun play(genreId: String, shuffle: Boolean) {
        val tracks = genreDetailsUiStateFor(uiState.value, genreId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    fun orderedTrackIds(genreId: String): List<String> =
        genreDetailsUiStateFor(uiState.value, genreId).tracks.map(LibraryTrack::id)
}

internal fun genreDetailsUiStateFor(
    state: GenreDetailsUiState,
    genreId: String,
): GenreDetailsUiState {
    val genre = state.allGenres.firstOrNull { it.id == genreId }
    val genreTracks = state.tracksByGenreId[genreId].orEmpty()
    val tracksByAlbumId = genreTracks.groupBy { it.genreAlbumIdentifier() }
    val albums = state.allAlbums
        .filter { it.id in tracksByAlbumId }
        .sortedWith(
            compareBy<LibraryAlbum, String>(libraryAlphabeticalComparator) { it.sortTitle }
                .thenBy(libraryAlphabeticalComparator) { it.sortArtist }
                .thenBy { it.id },
        )
    val tracks = albums.flatMap { album ->
        tracksByAlbumId[album.id].orEmpty().sortedWith(genreAlbumTrackComparator)
    }
    return GenreDetailsUiState(genre = genre, albums = albums, tracks = tracks)
}

private val genreAlbumTrackComparator = compareBy<LibraryTrack> {
    if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE
}.thenBy {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.syncOrder }

private fun LibraryTrack.genreAlbumIdentifier(): String? = albumId.takeIf(String::isNotBlank)
    ?: metadataObject()?.get("album")
        ?.let { it as? JsonObject }
        ?.get("id")
        ?.let { it as? JsonPrimitive }
        ?.content
        ?.takeIf(String::isNotBlank)

private fun LibraryTrack.genreIds(): Set<String> {
    val root = metadataObject() ?: return emptySet()
    return (root["genres"] as? JsonArray).orEmpty().mapNotNull { genre ->
        (genre as? JsonObject)?.get("id")?.let { it as? JsonPrimitive }?.content?.takeIf(String::isNotBlank)
    }.toSet()
}
