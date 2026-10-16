package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.isExplicit
import com.amwolfstein.sistrum.sync.metadataObject
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
internal fun HomeContent(
    isLoaded: Boolean,
    recentTracks: List<LibraryTrack>,
    keepListeningTracks: List<LibraryTrack>,
    mostPlayedTracks: List<LibraryTrack>,
    forgottenTracks: List<LibraryTrack>,
    onTrackClick: (List<LibraryTrack>, String) -> Unit,
    playbackQueue: PlaybackQueueSnapshot,
    onTrackPlayNext: (LibraryTrack) -> Unit,
    onTrackAddToQueue: (LibraryTrack) -> Unit,
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit,
    onTrackAlbumClick: (LibraryTrack) -> Unit,
    onTrackArtistClick: (TrackContextArtist) -> Unit,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSistrumTokens.current
    val albums = remember(recentTracks) { recentHomeAlbums(recentTracks) }
    var showRecentList by remember { mutableStateOf(false) }
    val recentRowState = rememberLazyListState()
    val collapsed by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Box(modifier) {
        val layoutDirection = LocalLayoutDirection.current
        val titleHorizontalPadding = contentPadding.calculateStartPadding(layoutDirection)
        LazyColumn(
            modifier = Modifier.testTag("home-list"),
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.extraExtraLarge),
        ) {
            item(key = "title") {
                Text(stringResource(R.string.destination_home),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.colors.text,
                    modifier = Modifier.padding(horizontal = titleHorizontalPadding).testTag("home-large-title"))
            }
            if (!isLoaded && recentTracks.isEmpty() && keepListeningTracks.isEmpty() && mostPlayedTracks.isEmpty() && forgottenTracks.isEmpty()) {
                return@LazyColumn
            }
            if (recentTracks.isEmpty() && keepListeningTracks.isEmpty() && mostPlayedTracks.isEmpty() && forgottenTracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        HeroCard(
                            symbol = MaterialSymbols.MusicNote,
                            title = stringResource(R.string.library_empty_title),
                            description = stringResource(R.string.library_empty_description),
                        )
                    }
                }
            } else {
                if (albums.isNotEmpty()) item(key = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = titleHorizontalPadding),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.library_recently_added),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = tokens.colors.text, modifier = Modifier.weight(1f))
                            SistrumIconButton(MaterialSymbols.ViewList,
                                label = stringResource(R.string.home_recent_full_list),
                                onClick = { showRecentList = true },
                                modifier = Modifier.testTag("home-recent-full-list"))
                        }
                        LazyRow(state = recentRowState,
                            flingBehavior = rememberSnapFlingBehavior(recentRowState, snapPosition = SnapPosition.Start),
                            contentPadding = PaddingValues(horizontal = titleHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
                            modifier = Modifier.fillMaxWidth().testTag("home-recent-carousel")) {
                            items(albums, key = { it.first().albumId.ifBlank { it.first().id } }) { album ->
                                val track = album.first()
                                var expanded by remember { mutableStateOf(false) }
                                TrackContextMenu(track = track, expanded = expanded, onDismiss = { expanded = false },
                                    modifier = Modifier.width(280.dp), playbackQueue = playbackQueue,
                                    onPlayNext = onTrackPlayNext, onAddToQueue = onTrackAddToQueue,
                                    onFavoriteChange = onTrackFavoriteToggle, onGoToAlbum = onTrackAlbumClick,
                                    onGoToArtist = onTrackArtistClick, onBottomSheetRequested = onTrackContextBottomSheet) {
                                    HomeAlbumHero(album, Modifier,
                                        onClick = { onTrackClick(recentTracks, track.id) },
                                        onLongClick = { expanded = true })
                                }
                            }
                        }
                    }
                }
                if (keepListeningTracks.isNotEmpty()) item {
                    HomeTrackSection(
                        titleRes = R.string.home_keep_listening,
                        tracks = keepListeningTracks,
                        onTrackClick = onTrackClick,
                        playbackQueue = playbackQueue,
                        onTrackPlayNext = onTrackPlayNext,
                        onTrackAddToQueue = onTrackAddToQueue,
                        onTrackFavoriteToggle = onTrackFavoriteToggle,
                        onTrackAlbumClick = onTrackAlbumClick,
                        onTrackArtistClick = onTrackArtistClick,
                        onTrackContextBottomSheet = onTrackContextBottomSheet,
                        titleHorizontalPadding = titleHorizontalPadding,
                    )
                }
                if (mostPlayedTracks.isNotEmpty()) item {
                    HomeTrackSection(
                        titleRes = R.string.home_most_played,
                        tracks = mostPlayedTracks,
                        onTrackClick = onTrackClick,
                        playbackQueue = playbackQueue,
                        onTrackPlayNext = onTrackPlayNext,
                        onTrackAddToQueue = onTrackAddToQueue,
                        onTrackFavoriteToggle = onTrackFavoriteToggle,
                        onTrackAlbumClick = onTrackAlbumClick,
                        onTrackArtistClick = onTrackArtistClick,
                        onTrackContextBottomSheet = onTrackContextBottomSheet,
                        titleHorizontalPadding = titleHorizontalPadding,
                    )
                }
                if (forgottenTracks.isNotEmpty()) item {
                    HomeTrackSection(
                        titleRes = R.string.home_forgotten,
                        tracks = forgottenTracks,
                        onTrackClick = onTrackClick,
                        playbackQueue = playbackQueue,
                        onTrackPlayNext = onTrackPlayNext,
                        onTrackAddToQueue = onTrackAddToQueue,
                        onTrackFavoriteToggle = onTrackFavoriteToggle,
                        onTrackAlbumClick = onTrackAlbumClick,
                        onTrackArtistClick = onTrackArtistClick,
                        onTrackContextBottomSheet = onTrackContextBottomSheet,
                        titleHorizontalPadding = titleHorizontalPadding,
                    )
                }
            }
        }
        if (showRecentList) {
            AlertDialog(
                onDismissRequest = { showRecentList = false },
                title = { Text(stringResource(R.string.library_recently_added)) },
                confirmButton = {
                    TextButton(onClick = { showRecentList = false }) { Text(stringResource(R.string.close)) }
                },
                text = {
                    LazyColumn(Modifier.testTag("home-recent-list")) {
                        items(recentTracks, key = { it.id }) { track ->
                            var expanded by remember { mutableStateOf(false) }
                            TrackContextMenu(track = track, expanded = expanded, onDismiss = { expanded = false },
                                playbackQueue = playbackQueue, onPlayNext = onTrackPlayNext,
                                onAddToQueue = onTrackAddToQueue, onFavoriteChange = onTrackFavoriteToggle,
                                onGoToAlbum = onTrackAlbumClick, onGoToArtist = onTrackArtistClick,
                                onBottomSheetRequested = onTrackContextBottomSheet) {
                                TrackRow(title = track.title, artist = track.artists,
                                    artworkPath = track.artworkPath, audioPath = track.audioPath,
                                    onClick = { onTrackClick(recentTracks, track.id); showRecentList = false },
                                    onMoreClick = { expanded = true }, onLongClick = { expanded = true })
                            }
                        }
                    }
                },
            )
        }

        if (collapsed) {
            Box(Modifier.fillMaxWidth().background(tokens.colors.opaqueFill)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(vertical = tokens.spacing.large).testTag("home-collapsed-title"), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.destination_home), style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.text)
            }
        }
    }
}

/** Input is the ViewModel's newest-first recent queue; group without changing its order. */
internal fun recentHomeAlbums(tracks: List<LibraryTrack>): List<List<LibraryTrack>> =
    tracks.groupBy { it.albumId.ifBlank { it.id } }.values.toList()

@Composable
private fun HomeAlbumHero(tracks: List<LibraryTrack>, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val track = tracks.first()
    val tokens = LocalSistrumTokens.current
    val bitmap = rememberArtworkThumbnail(track.artworkPath, track.audioPath, targetPx = 500)
    val year = remember(track.metadataJson) { (track.metadataObject()?.get("year") as? JsonPrimitive)?.contentOrNull }
    val count = pluralStringResource(R.plurals.album_details_track_count, tracks.size, tracks.size)
    Box(modifier.requiredSize(280.dp, 300.dp).clip(tokens.shapes.large).background(tokens.colors.opaqueFill)
        .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
        .testTag("home-hero-${track.id}")) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else MaterialSymbol(MaterialSymbols.Album, null, modifier = Modifier.align(Alignment.Center),
            size = 64.dp, tint = tokens.colors.mutedText)
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(0f to Color.Transparent,
                0.2f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.95f)))
            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.extraSmall)) {
            Text(track.album.ifBlank { track.title }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home-recent-title-${track.id}"))
            Text(track.artists, style = MaterialTheme.typography.bodyMedium, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(count, year?.takeIf { it.isNotBlank() && it != "0" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeTrackSection(
    titleRes: Int,
    tracks: List<LibraryTrack>,
    onTrackClick: (List<LibraryTrack>, String) -> Unit,
    playbackQueue: PlaybackQueueSnapshot,
    onTrackPlayNext: (LibraryTrack) -> Unit,
    onTrackAddToQueue: (LibraryTrack) -> Unit,
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit,
    onTrackAlbumClick: (LibraryTrack) -> Unit,
    onTrackArtistClick: (TrackContextArtist) -> Unit,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit,
    titleHorizontalPadding: Dp,
) {
    if (tracks.isEmpty()) return

    val tokens = LocalSistrumTokens.current
    var contextTrack by remember { mutableStateOf<LibraryTrack?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = tokens.colors.text,
            modifier = Modifier.padding(horizontal = titleHorizontalPadding, vertical = 4.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = maxOf(
                (maxWidth - titleHorizontalPadding * 2 - tokens.spacing.large * 2) / 3.2f,
                88.dp * LocalDensity.current.fontScale,
            )
            LazyHorizontalGrid(
                rows = GridCells.Fixed(homeTrackSectionRows(1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(homeTrackSectionHeight(1, discCardHeight(cardWidth)))
                    .testTag("home-shelf-$titleRes"),
                contentPadding = PaddingValues(horizontal = titleHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large),
            ) {
                items(tracks.size, key = { index -> tracks[index].id }) { index ->
                    val track = tracks[index]
                    TrackContextMenu(
                        track = track,
                        expanded = contextTrack?.id == track.id,
                        onDismiss = { if (contextTrack?.id == track.id) contextTrack = null },
                        modifier = Modifier.width(cardWidth),
                        playbackQueue = playbackQueue,
                        onPlayNext = onTrackPlayNext,
                        onAddToQueue = onTrackAddToQueue,
                        onFavoriteChange = onTrackFavoriteToggle,
                        onGoToAlbum = onTrackAlbumClick,
                        onGoToArtist = onTrackArtistClick,
                        onBottomSheetRequested = onTrackContextBottomSheet,
                    ) {
                        Box {
                            DiscCard(
                                title = track.title,
                                subtitle = track.artists,
                                artworkPath = track.artworkPath,
                                audioPath = track.audioPath,
                                modifier = Modifier.fillMaxWidth().testTag("home-track-${track.id}"),
                                onClick = { onTrackClick(tracks, track.id) },
                                onLongClick = { contextTrack = track },
                            )
                            Column(Modifier.align(Alignment.TopStart).background(tokens.colors.opaqueFill)) {
                                if (track.isExplicit()) Text(stringResource(R.string.track_explicit),
                                    style = MaterialTheme.typography.labelSmall, color = tokens.colors.mutedText)
                                TrackQualityBadge(trackAudioQuality(track))
                            }
                        }
                    }
                }
            }
        }
    }
}
private val HomeCardSpacing = 20.dp

internal fun homeTrackSectionRows(trackCount: Int) = minOf(trackCount, 2)

/**
 * The grid is as tall as its rows of cards. [cardHeight] comes from the card's text metrics at
 * the current font scale; a fixed height clipped the artist line of larger or Arabic text.
 */
internal fun homeTrackSectionHeight(trackCount: Int, cardHeight: Dp): Dp {
    val rows = homeTrackSectionRows(trackCount)
    return cardHeight * rows + HomeCardSpacing * (rows - 1).coerceAtLeast(0)
}
