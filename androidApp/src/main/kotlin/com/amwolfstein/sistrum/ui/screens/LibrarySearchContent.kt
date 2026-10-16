package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.components.AlbumContextMenu
import com.amwolfstein.sistrum.ui.components.DiscCard
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.SistrumTextField
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import com.amwolfstein.sistrum.ui.components.TrackContextMenu
import com.amwolfstein.sistrum.ui.components.TrackRow
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
internal fun LibrarySearchContent(
    uiState: LibrarySearchUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onQueryChange: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onComposerClick: (String) -> Unit = {},
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (LibraryTrack) -> Unit = {},
    onTrackAddToQueue: (LibraryTrack) -> Unit = {},
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit = { _, _ -> },
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val tokens = LocalSistrumTokens.current
    var contextTrack by remember { mutableStateOf<LibraryTrack?>(null) }
    var contextAlbumId by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().background(tokens.colors.page)) {
        val trackDividerWidth = maxWidth * 0.8f
        Column(Modifier.fillMaxSize()) {
            SistrumTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.library_search_placeholder),
                leadingSymbol = MaterialSymbols.Search,
                modifier = Modifier.padding(
                    start = tokens.spacing.extraLarge, end = tokens.spacing.extraLarge,
                    top = contentPadding.calculateTopPadding() + tokens.spacing.extraSmall,
                    bottom = tokens.spacing.medium,
                ),
            )
            LazyColumn(state = listState, modifier = Modifier.testTag("search-results"), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                if (uiState.query.isBlank()) {
                    item { SearchEmptyState(R.string.library_search_empty_title, R.string.library_search_empty_description) }
                } else if (!uiState.isLoaded) {
                    item("loading") {
                        Box(Modifier.fillMaxWidth().padding(tokens.spacing.extraLarge), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.testTag("search-loading"), color = tokens.colors.text)
                        }
                    }
                } else if (uiState.tracks.isEmpty() && uiState.albums.isEmpty() && uiState.artists.isEmpty() && uiState.playlists.isEmpty() && uiState.composers.isEmpty()) {
                    item { SearchEmptyState(R.string.library_search_no_results_title, R.string.library_search_no_results_description) }
                } else {
                    searchSection(R.string.library_tracks, uiState.tracks, { it.id }, rows = 3, topPadding = tokens.spacing.extraLarge) { track ->
                        TrackContextMenu(
                            track = track, expanded = contextTrack?.id == track.id,
                            onDismiss = { contextTrack = null }, playbackQueue = playbackQueue,
                            onPlayNext = onTrackPlayNext, onAddToQueue = onTrackAddToQueue,
                            onFavoriteChange = onTrackFavoriteToggle, onBottomSheetRequested = onTrackContextBottomSheet,
                        ) {
                            Column {
                                TrackRow(track.title, track.artists, artworkPath = track.artworkPath, audioPath = track.audioPath, contentPadding = PaddingValues(vertical = tokens.spacing.small), onClick = { onTrackClick(track.id) }, onMoreClick = { contextTrack = track }, onLongClick = { contextTrack = track })
                                val trackIndex = uiState.tracks.indexOfFirst { it.id == track.id }
                                if (trackHasDivider(trackIndex, uiState.tracks.lastIndex)) {
                                    HorizontalDivider(
                                        color = tokens.colors.divider,
                                        modifier = Modifier.width(trackDividerWidth).align(Alignment.CenterHorizontally),
                                    )
                                }
                            }
                        }
                    }
                    searchSection(R.string.library_albums, uiState.albums, { it.id }, topPadding = tokens.spacing.extraExtraLarge) { album ->
                        val tracks = uiState.allTracks.filter { it.albumId == album.id }
                        AlbumContextMenu(
                            tracks = tracks, expanded = contextAlbumId == album.id, onDismiss = { contextAlbumId = null }, playbackQueue = playbackQueue,
                            onPlayNext = onAlbumPlayNext, onAddToQueue = onAlbumAddToQueue, onAddToFavorites = onAlbumAddToFavorites,
                            onBottomSheetRequested = onTrackContextBottomSheet,
                        ) {
                            DiscCard(
                                title = album.title,
                                subtitle = album.artist,
                                artworkPath = album.artworkPath,
                                audioPath = album.audioPath,
                                fallbackSymbol = MaterialSymbols.Album,
                                onClick = { onAlbumClick(album.id) },
                                onLongClick = { contextAlbumId = album.id },
                            )
                        }
                    }
                    searchSection(R.string.library_artists, uiState.artists, { it.id }, topPadding = tokens.spacing.large) { artist ->
                        DiscCard(artist.name, "", artworkPath = artist.artworkPath, fallbackSymbol = MaterialSymbols.Person, artworkShape = CircleShape, onClick = { onArtistClick(artist.id) })
                    }
                    searchSection(R.string.library_playlists, uiState.playlists, { it.id }, topPadding = tokens.spacing.large) { playlist ->
                        DiscCard(
                            title = playlist.name,
                            subtitle = "",
                            artworkPath = uiState.allTracks.firstOrNull { it.id in playlist.trackIds }?.artworkPath,
                            audioPath = uiState.allTracks.firstOrNull { it.id in playlist.trackIds }?.audioPath,
                            fallbackSymbol = MaterialSymbols.QueueMusic,
                            onClick = { onPlaylistClick(playlist.id) },
                        )
                    }
                    searchSection(R.string.library_composers, uiState.composers, { it.id }, topPadding = tokens.spacing.large) { composer ->
                        DiscCard(composer.name, "", artworkPath = composer.artworkPath, fallbackSymbol = MaterialSymbols.StylusFountainPen, artworkShape = CircleShape, onClick = { onComposerClick(composer.id) })
                    }
                }
            }
        }
    }
}

private fun <T> LazyListScope.searchSection(
    title: Int, values: List<T>, key: (T) -> String, rows: Int = 1, topPadding: androidx.compose.ui.unit.Dp = 16.dp, row: @Composable (T) -> Unit,
) {
    if (values.isEmpty()) return
    item("section_$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = LocalSistrumTokens.current.colors.mutedText,
            modifier = Modifier.padding(start = LocalSistrumTokens.current.spacing.extraLarge, top = topPadding, bottom = LocalSistrumTokens.current.spacing.medium),
        )
    }
    item("section_content_$title") {
        val tokens = LocalSistrumTokens.current
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A wrap-content column keeps all three tracks reachable at large font scales.
            val trackWidth = minOf(SearchTrackColumnMaxWidth, maxWidth - tokens.spacing.extraLarge * 2)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = tokens.spacing.extraLarge),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
            ) {
                if (rows == 3) {
                    rowItems(values.chunked(3), key = { searchItemKey(title, key(it.first())) }) { column ->
                        Column(Modifier.width(trackWidth)) { column.forEach { row(it) } }
                    }
                } else {
                    rowItems(values, key = { searchItemKey(title, key(it)) }) { item ->
                        Box(Modifier.width(160.dp)) { row(item) }
                    }
                }
            }
        }
    }
}

internal fun searchItemKey(section: Int, id: String): String = "search_$section:$id"

private val SearchTrackColumnMaxWidth = 320.dp

internal fun trackHasDivider(index: Int, lastIndex: Int): Boolean = index >= 0 && index < lastIndex && index % 3 != 2

internal const val SearchEmptyStateTag = "search-empty-state"

@Composable private fun SearchEmptyState(title: Int, description: Int) = Box(
    Modifier.padding(LocalSistrumTokens.current.spacing.extraLarge).testTag(SearchEmptyStateTag),
) {
    HeroCard(
        title = stringResource(title),
        description = stringResource(description),
        symbol = MaterialSymbols.Search,
    )
}
