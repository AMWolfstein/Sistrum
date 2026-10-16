package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import com.amwolfstein.sistrum.ui.components.DetailHeroArtworkShape
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import androidx.compose.ui.platform.testTag
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
import com.amwolfstein.sistrum.ui.components.ArtistContextMenu
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val ArtistAlbumDividerTag = "artist-detail-album-divider"

@Composable
internal fun ArtistDetailsContent(
    uiState: ArtistDetailsUiState,
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
    onAddArtistImage: () -> Unit = {},
    onRemoveArtistImage: () -> Unit = {},
    onAlbumClick: (LibraryAlbum) -> Unit = {},
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    // Null slots retain the local-only screen; a persisted manual image always wins.
    portrait: (@Composable () -> Unit)? = null,
    headerBackground: (@Composable () -> Unit)? = null,
    about: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalSistrumTokens.current
    val colors = tokens.colors
    val artist = uiState.artist
    if (artist == null) {
        HeroCard(
            symbol = MaterialSymbols.Person,
            title = stringResource(R.string.artist_details_empty_title),
            description = stringResource(R.string.artist_details_empty_description),
            modifier = modifier.fillMaxSize().padding(contentPadding),
        )
        return
    }
    var menuExpanded by remember(artist.id) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.page),
        state = listState,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item("hero") {
            ArtworkHeroBackdrop(artist.artworkPath, Modifier.fillMaxWidth(), onHeroColorChanged) {
                Box(Modifier.fillMaxWidth()) {
                    if (headerBackground != null) {
                        Box(Modifier.matchParentSize()) { headerBackground() }
                        Box(Modifier.matchParentSize().background(colors.page.copy(alpha = 0.28f)))
                    }
                    ArtistContextMenu(
                        trackIds = uiState.tracks.map { it.id },
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onBottomSheetRequested = onTrackContextBottomSheet,
                        onAddArtistImage = onAddArtistImage,
                        onRemoveArtistImage = onRemoveArtistImage.takeIf { artist.hasCustomArtwork },
                        addToPlaylistOnly = true,
                        hazeState = hazeState,
                        playbackQueue = playbackQueue,
                    ) {
                        DetailHero(
                            title = artist.name,
                            subtitle = stringResource(
                                R.string.artist_details_summary,
                                pluralStringResource(R.plurals.artist_details_album_count, uiState.albums.size, uiState.albums.size),
                                pluralStringResource(R.plurals.artist_details_track_count, uiState.tracks.size, uiState.tracks.size),
                            ),
                            metadata = null,
                            playLabel = stringResource(R.string.player_play),
                            shuffleLabel = stringResource(R.string.player_shuffle),
                            moreLabel = stringResource(R.string.artist_row_more_options),
                            modifier = Modifier.fillMaxWidth().padding(
                                start = tokens.spacing.extraLarge,
                                top = contentPadding.calculateTopPadding(),
                                end = tokens.spacing.extraLarge,
                                bottom = tokens.spacing.large,
                            ),
                            artworkPath = artist.artworkPath,
                            artworkContent = portrait.takeUnless { artist.hasCustomArtwork },
                            artworkShape = DetailHeroArtworkShape.Circle,
                            artworkSize = 144.dp,
                            fallbackSymbol = MaterialSymbols.Person,
                            onPlayClick = onPlay,
                            onShuffleClick = onShuffle,
                            onMoreClick = { menuExpanded = true },
                        )
                    }
                }
            }
        }
        about?.let { aboutContent ->
            item("about") {
                Box(Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.extraLarge, vertical = tokens.spacing.medium)) {
                    aboutContent()
                }
            }
        }
        itemsIndexed(uiState.albums, key = { _, album -> album.id }, contentType = { _, _ -> "artist_album_row" }) { index, album ->
            if (index == 0) Box(Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.extraLarge).height(1.dp).background(colors.divider).testTag(ArtistAlbumDividerTag))
            AlbumRow(
                title = album.title,
                artist = album.artist.ifBlank { stringResource(R.string.album_unknown_artist) },
                artworkPath = album.artworkPath,
                audioPath = album.audioPath,
                onClick = { onAlbumClick(album) },
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.extraLarge).height(1.dp).background(colors.divider).testTag(ArtistAlbumDividerTag))
        }
    }
}
