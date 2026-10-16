package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.ui.components.AlbumContextMenu
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import com.amwolfstein.sistrum.ui.components.TrackContextArtist
import com.amwolfstein.sistrum.ui.components.TrackContextMenu
import com.amwolfstein.sistrum.ui.components.TrackContextMenuActions
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import androidx.compose.ui.text.style.TextAlign
import com.amwolfstein.sistrum.ui.components.MornyeDetailScaffold
import com.amwolfstein.sistrum.ui.components.MornyeActionRow
import com.amwolfstein.sistrum.ui.components.MornyeSongRow
import com.amwolfstein.sistrum.ui.components.MornyeDetailFooter
import com.amwolfstein.sistrum.ui.components.trackInfoValues
import com.amwolfstein.sistrum.ui.components.trackAudioQuality

@Composable
internal fun AlbumDetailsContent(
    uiState: AlbumDetailsUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
    onHeroColorChanged: (Color) -> Unit = {},
    onBack: () -> Unit = {},
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (String) -> Unit = {},
    onTrackAddToQueue: (String) -> Unit = {},
    onTrackFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    onTrackArtistClick: (TrackContextArtist) -> Unit = {},
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
) {
    val album = uiState.album
    if (album == null) {
        MornyeDetailScaffold(
            title = stringResource(R.string.album_details_empty_title),
            onBack = onBack,
            modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item("unavailable") {
                HeroCard(symbol = MaterialSymbols.Album, title = stringResource(R.string.album_details_empty_title),
                    description = stringResource(R.string.album_details_empty_description), modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }
    var contextTrack by remember(album.id) { mutableStateOf<String?>(null) }
    var albumMenuExpanded by remember(album.id) { mutableStateOf(false) }
    val totalDurationSeconds = remember(uiState.tracks) { albumTotalDurationSeconds(uiState.tracks) }
    val genres = remember(uiState.tracks) {
        uiState.tracks.mapNotNull { track ->
            trackInfoValues(track).firstOrNull { it.labelRes == R.string.track_info_genre }?.value
        }.distinct().joinToString(" / ")
    }
    val quality = uiState.tracks.firstOrNull()?.let { qualityLabel(trackAudioQuality(it)) }
    val metadata = listOfNotNull(genres.takeIf(String::isNotBlank), album.year.takeIf { it > 0 }?.toString(), quality).joinToString(" · ")
    val multiDisc = uiState.tracks.map { it.discNumber.takeIf { n -> n > 0 } ?: 1 }.distinct().size > 1
    MornyeDetailScaffold(
        title = album.title,
        onBack = onBack,
        artworkPath = album.artworkPath,
        audioPath = album.audioPath,
        onHeroColorChanged = onHeroColorChanged,
        modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        header = {
            Text(album.artist.ifBlank { stringResource(R.string.album_unknown_artist) },
                Modifier.fillMaxWidth().testTag("album-mornye-artist"), color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium)
            Text(metadata, Modifier.fillMaxWidth().testTag("album-mornye-metadata"), color = Color.White,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            MornyeActionRow(
                onShuffle = onShuffle,
                onPlay = onPlay,
                onMore = { albumMenuExpanded = true },
                moreLabel = stringResource(R.string.album_row_more_options),
                menu = { moreButton ->
                    AlbumContextMenu(
                        tracks = uiState.tracks,
                        expanded = albumMenuExpanded,
                        onDismiss = { albumMenuExpanded = false },
                        hazeState = hazeState,
                        playbackQueue = playbackQueue,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onPlayNext = onAlbumPlayNext,
                        onAddToQueue = onAlbumAddToQueue,
                        onAddToFavorites = onAlbumAddToFavorites,
                        onBottomSheetRequested = onTrackContextBottomSheet,
                        anchor = moreButton,
                    )
                },
            )
        },
    ) {
        uiState.tracks.forEachIndexed { index, track ->
            val disc = track.discNumber.takeIf { it > 0 } ?: 1
            if (multiDisc && (index == 0 || disc != (uiState.tracks[index - 1].discNumber.takeIf { it > 0 } ?: 1))) {
                item("disc-$index") {
                    Text(stringResource(R.string.album_details_disc, disc), color = Color.White,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
            }
            item(track.id) {
                TrackContextMenu(
                    track = track,
                    expanded = contextTrack == track.id,
                    onDismiss = { if (contextTrack == track.id) contextTrack = null },
                    actions = TrackContextMenuActions(goToAlbum = false),
                    playbackQueue = playbackQueue,
                    onPlayNext = { onTrackPlayNext(it.id) },
                    onAddToQueue = { onTrackAddToQueue(it.id) },
                    onFavoriteChange = { item, favorite -> onTrackFavoriteToggle(item.id, favorite) },
                    onGoToArtist = onTrackArtistClick,
                    onBottomSheetRequested = onTrackContextBottomSheet,
                ) {
                    MornyeSongRow(
                        track = track,
                        number = track.trackNumber.takeIf { it > 0 } ?: index + 1,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onTrackClick(track.id) },
                        onMore = { contextTrack = track.id },
                        onLongClick = { contextTrack = track.id },
                    )
                }
            }
        }
        item("footer") {
            MornyeDetailFooter(uiState.tracks.size, (totalDurationSeconds / 60).toInt())
        }
        album.copyright.takeIf(String::isNotBlank)?.let { copyright ->
            item("copyright") {
                Text(copyright, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp))
            }
        }
    }
}
