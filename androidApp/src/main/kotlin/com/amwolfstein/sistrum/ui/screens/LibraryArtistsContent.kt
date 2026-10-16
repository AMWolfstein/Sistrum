package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.components.ArtistRow
import com.amwolfstein.sistrum.ui.components.ArtistContextMenu
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.LibraryVirtualList
import com.amwolfstein.sistrum.ui.components.LibraryTextFilter
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import dev.chrisbanes.haze.HazeState
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot

@Composable
internal fun LibraryArtistsContent(
    uiState: LibraryArtistsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onArtistClick: ((LibraryArtist) -> Unit)? = null,
    onFilterQueryChange: (String) -> Unit = {},
    orderedTrackIdsForArtist: (String) -> List<String> = { emptyList() },
    onArtistPlayNext: (List<String>) -> Unit = {},
    onArtistAddToQueue: (List<String>) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    hazeState: HazeState? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    portrait: @Composable ((LibraryArtist) -> Unit)? = null,
) {
    val tokens = LocalSistrumTokens.current
    var contextArtistId by remember { mutableStateOf<String?>(null) }
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.artists,
        isLoaded = uiState.isLoaded,
        key = { artist -> artist.id },
        contentType = "artist_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier.background(tokens.colors.page),
        dividerTestTag = "artist-row-divider",
        filterKey = "artists",
        filterActive = uiState.filterQuery.isNotBlank(),
        filterContent = {
            LibraryTextFilter(
                value = uiState.filterQuery,
                onValueChange = onFilterQueryChange,
                placeholder = stringResource(R.string.filter_placeholder_search),
            )
        },
        alphabeticalIndexKey = if (uiState.sortOption == ArtistSortOption.Name) ({ artist -> artist.sortName.ifBlank { artist.name } }) else null,
        emptyContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.large),
            ) {
                HeroCard(
                    title = stringResource(if (uiState.filterQuery.isBlank()) R.string.artists_empty_title else R.string.artists_no_match_title),
                    description = stringResource(if (uiState.filterQuery.isBlank()) R.string.artists_empty_description else R.string.filter_no_match_description),
                    symbol = MaterialSymbols.People,
                )
            }
        },
    ) { artist ->
        // Track IDs are only needed by the context menu. Avoid rebuilding the
        // artist's full track/album ordering while every row is composed or
        // recomposed during scrolling.
        val trackIds = if (contextArtistId == artist.id) {
            orderedTrackIdsForArtist(artist.id)
        } else {
            emptyList()
        }
        ArtistContextMenu(
            trackIds = trackIds,
            expanded = contextArtistId == artist.id,
            onDismiss = { if (contextArtistId == artist.id) contextArtistId = null },
            onPlayNext = onArtistPlayNext,
            onAddToQueue = onArtistAddToQueue,
            onBottomSheetRequested = onTrackContextBottomSheet,
            addToPlaylistOnly = true,
            hazeState = hazeState,
            playbackQueue = playbackQueue,
        ) {
            ArtistRow(
                name = artist.name,
                artworkPath = artist.artworkPath,
                portrait = portrait?.let { content -> { content(artist) } },
                onClick = onArtistClick?.let { callback -> { callback(artist) } },
                onLongClick = { contextArtistId = artist.id },
            )
        }
    }
}
