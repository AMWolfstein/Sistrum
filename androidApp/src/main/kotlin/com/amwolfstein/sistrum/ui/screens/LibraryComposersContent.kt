package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.background
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
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
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.components.ComposerRow
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.LibraryVirtualList
import com.amwolfstein.sistrum.ui.components.LibraryTextFilter
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import com.amwolfstein.sistrum.ui.components.ComposerContextMenu
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import com.amwolfstein.sistrum.sync.LibraryComposer
import dev.chrisbanes.haze.HazeState
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot

@Composable
internal fun LibraryComposersContent(
    uiState: LibraryComposersUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onComposerClick: (LibraryComposer) -> Unit = {},
    onFilterQueryChange: (String) -> Unit = {},
    orderedTrackIdsForComposer: (String) -> List<String> = { emptyList() },
    onComposerPlayNext: (List<String>) -> Unit = {},
    onComposerAddToQueue: (List<String>) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    hazeState: HazeState? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
) {
    val tokens = LocalSistrumTokens.current
    var contextComposerId by remember { mutableStateOf<String?>(null) }
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.composers,
        isLoaded = uiState.isLoaded,
        key = { composer -> composer.id },
        contentType = "composer_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier.background(tokens.colors.page),
        dividerTestTag = "composer-row-divider",
        filterKey = "composers",
        filterActive = uiState.filterQuery.isNotBlank(),
        filterContent = {
            LibraryTextFilter(
                value = uiState.filterQuery,
                onValueChange = onFilterQueryChange,
                placeholder = stringResource(R.string.filter_placeholder_search),
            )
        },
        alphabeticalIndexKey = if (uiState.sortOption == ComposerSortOption.Name) ({ composer -> composer.sortName.ifBlank { composer.name } }) else null,
        emptyContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.large),
            ) {
                HeroCard(
                    title = stringResource(if (uiState.filterQuery.isBlank()) R.string.composers_empty_title else R.string.composers_no_match_title),
                    description = stringResource(if (uiState.filterQuery.isBlank()) R.string.composers_empty_description else R.string.filter_no_match_description),
                    symbol = MaterialSymbols.StylusFountainPen,
                )
            }
        },
    ) { composer ->
        val trackIds = if (contextComposerId == composer.id) {
            orderedTrackIdsForComposer(composer.id)
        } else {
            emptyList()
        }
        ComposerContextMenu(
            trackIds = trackIds,
            expanded = contextComposerId == composer.id,
            onDismiss = { if (contextComposerId == composer.id) contextComposerId = null },
            onPlayNext = onComposerPlayNext,
            onAddToQueue = onComposerAddToQueue,
            onBottomSheetRequested = onTrackContextBottomSheet,
            addToPlaylistOnly = true,
            hazeState = hazeState,
            playbackQueue = playbackQueue,
        ) {
            ComposerRow(
                name = composer.name,
                onClick = { onComposerClick(composer) },
                onLongClick = { contextComposerId = composer.id },
            )
        }
    }
}
