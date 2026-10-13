package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.ui.components.AlbumRow
import com.amwolfstein.sistrum.ui.components.ArtworkHeroBackdrop
import com.amwolfstein.sistrum.ui.components.DetailHero
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import androidx.compose.ui.platform.testTag
import com.amwolfstein.sistrum.ui.components.InsetListDivider
import com.amwolfstein.sistrum.ui.components.ComposerContextMenu
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val ComposerAlbumDividerTag = "composer-detail-album-divider"

@Composable
internal fun ComposerDetailsContent(
    uiState: ComposerDetailsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
    onHeroColorChanged: (Color) -> Unit = {},
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlayNext: (List<String>) -> Unit = {},
    onAddToQueue: (List<String>) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    onAlbumClick: (LibraryAlbum) -> Unit = {},
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
) {
    val composer = uiState.composer
    if (composer == null) {
        HeroCard(
            symbol = MaterialSymbols.StylusFountainPen,
            title = stringResource(R.string.composer_details_empty_title),
            description = stringResource(R.string.composer_details_empty_description),
            modifier = modifier.fillMaxSize().padding(contentPadding),
        )
        return
    }
    var menuExpanded by remember(composer.id) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item("hero") {
            ArtworkHeroBackdrop(null, Modifier.fillMaxWidth(), onHeroColorChanged) {
                ComposerContextMenu(
                    trackIds = uiState.tracks.map { it.id },
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onBottomSheetRequested = onTrackContextBottomSheet,
                    hazeState = hazeState,
                    playbackQueue = playbackQueue,
                ) {
                    DetailHero(
                    title = composer.name,
                    subtitle = stringResource(
                        R.string.composer_details_summary,
                        pluralStringResource(R.plurals.composer_details_album_count, uiState.albums.size, uiState.albums.size),
                        pluralStringResource(R.plurals.composer_details_track_count, uiState.tracks.size, uiState.tracks.size),
                    ),
                    metadata = null,
                    playLabel = stringResource(R.string.player_play),
                    shuffleLabel = stringResource(R.string.player_shuffle),
                    moreLabel = stringResource(R.string.composer_row_more_options),
                    modifier = Modifier.fillMaxWidth().padding(
                        start = 24.dp,
                        // A top inset under 8 dp would make this negative, which crashes.
                        top = (contentPadding.calculateTopPadding() - 8.dp).coerceAtLeast(0.dp),
                        end = 24.dp,
                        bottom = 20.dp,
                    ),
                    showArtwork = false,
                    onPlayClick = onPlay,
                    onShuffleClick = onShuffle,
                    onMoreClick = { menuExpanded = true },
                )
                }
            }
        }
        itemsIndexed(uiState.albums, key = { _, album -> album.id }, contentType = { _, _ -> "composer_album_row" }) { index, album ->
            if (index == 0) InsetListDivider(Modifier.testTag(ComposerAlbumDividerTag))
            AlbumRow(
                title = album.title,
                artist = album.artist.ifBlank { stringResource(R.string.album_unknown_artist) },
                artworkPath = album.artworkPath,
                audioPath = album.audioPath,
                onClick = { onAlbumClick(album) },
            )
            InsetListDivider(Modifier.testTag(ComposerAlbumDividerTag))
        }
    }
}
