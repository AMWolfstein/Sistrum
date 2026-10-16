package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.components.*
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
internal fun ArtistDetailsContent(
    uiState: ArtistDetailsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
    onHeroColorChanged: (Color) -> Unit = {},
    onBack: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTrackPlayNext: (String) -> Unit = {},
    onTrackAddToQueue: (String) -> Unit = {},
    onTrackFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    onTrackAlbumClick: (LibraryTrack) -> Unit = {},
    onTrackArtistClick: (TrackContextArtist) -> Unit = {},
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
    val artist = uiState.artist
    if (artist == null) {
        MornyeDetailScaffold(title = stringResource(R.string.artist_details_empty_title), onBack = onBack,
            modifier = modifier.padding(contentPadding)) {
            item("unavailable") {
                HeroCard(symbol = MaterialSymbols.Person, title = stringResource(R.string.artist_details_empty_title),
                    description = stringResource(R.string.artist_details_empty_description))
            }
        }
        return
    }
    val sections = remember(uiState) { artistReleaseSections(uiState) }
    var menuExpanded by remember(artist.id) { mutableStateOf(false) }
    var contextTrack by remember(artist.id) { mutableStateOf<String?>(null) }
    val portraitContent = portrait.takeUnless { artist.hasCustomArtwork }
    val backgroundContent: (@Composable BoxScope.() -> Unit)? = if (headerBackground != null || portraitContent != null) {
        {
            headerBackground?.invoke()
            if (portraitContent != null) portraitContent()
            else ArtistReleaseArtwork(artist.artworkPath, null, Modifier.matchParentSize())
        }
    } else null
    MornyeDetailScaffold(
        title = artist.name,
        titleStyle = MaterialTheme.typography.displayLarge,
        onBack = onBack,
        modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        listState = listState,
        artworkPath = artist.artworkPath,
        artworkContent = backgroundContent,
        onHeroColorChanged = onHeroColorChanged,
        header = {
            Text(stringResource(R.string.artist_details_summary,
                pluralStringResource(R.plurals.artist_details_album_count, uiState.albums.size, uiState.albums.size),
                pluralStringResource(R.plurals.artist_details_track_count, uiState.tracks.size, uiState.tracks.size)),
                color = Color.White, style = MaterialTheme.typography.bodySmall)
            MornyeActionRow(onPlay = onPlay, onShuffle = onShuffle, playShape = MornyePlayShape.Circle,
                moreLabel = stringResource(R.string.artist_row_more_options), onMore = { menuExpanded = true },
                menu = { moreButton ->
                    ArtistContextMenu(trackIds = uiState.tracks.map { it.id }, expanded = menuExpanded,
                        onDismiss = { menuExpanded = false }, onPlayNext = onPlayNext, onAddToQueue = onAddToQueue,
                        onBottomSheetRequested = onTrackContextBottomSheet, onAddArtistImage = onAddArtistImage,
                        onRemoveArtistImage = onRemoveArtistImage.takeIf { artist.hasCustomArtwork },
                        addToPlaylistOnly = true, hazeState = hazeState, playbackQueue = playbackQueue,
                        anchor = moreButton)
                })
        },
    ) {
        sections.latest?.let { album ->
            item("latest") {
                ArtistSectionHeading(stringResource(R.string.artist_latest_release))
                MornyeGlassSurface(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clickable(role = Role.Button) { onAlbumClick(album) }.testTag("artist-latest-release")) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtistReleaseArtwork(album.artworkPath, album.audioPath, Modifier.size(72.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(album.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            ArtistReleaseMetadata(album, sections.songCounts.getValue(album.id))
                        }
                    }
                }
            }
        }
        if (sections.popular.isNotEmpty()) {
            item("popular") {
                ArtistSectionHeading(stringResource(R.string.artist_popular))
                key(artist.id) {
                    val pager = rememberPagerState { (sections.popular.size + 4) / 5 }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().testTag("artist-popular-pager"),
                        verticalAlignment = Alignment.Top) { page ->
                        Column {
                            sections.popular.drop(page * 5).take(5).forEach { track ->
                                TrackContextMenu(track = track, expanded = contextTrack == track.id,
                                    onDismiss = { contextTrack = null }, hazeState = hazeState, playbackQueue = playbackQueue,
                                    onPlayNext = { onTrackPlayNext(it.id) }, onAddToQueue = { onTrackAddToQueue(it.id) },
                                    onFavoriteChange = { item, favorite -> onTrackFavoriteToggle(item.id, favorite) },
                                    onGoToAlbum = onTrackAlbumClick, onGoToArtist = onTrackArtistClick,
                                    onBottomSheetRequested = onTrackContextBottomSheet) {
                                    MornyeSongRow(track, onClick = { onTrackClick(track.id) },
                                        onMore = { contextTrack = track.id }, onLongClick = { contextTrack = track.id })
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp).testTag("artist-popular-dots"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        repeat(pager.pageCount) { page ->
                            Box(Modifier.size(8.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = if (page == pager.currentPage) 1f else 0.35f))
                                .semantics { selected = page == pager.currentPage }.testTag("artist-popular-dot-$page"))
                        }
                    }
                }
            }
        }
        listOf(
            Triple("albums", R.plurals.artist_release_albums, sections.albums),
            Triple("singles", R.plurals.artist_release_singles, sections.singles),
            Triple("compilations", R.plurals.artist_release_compilations, sections.compilations),
        ).forEach { (key, title, albums) ->
            if (albums.isNotEmpty()) item(key) {
                ArtistSectionHeading(formatDisplay(pluralStringResource(title, albums.size), albums.size))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("artist-shelf-$key")) {
                    items(albums, key = { it.id }) { album ->
                        Column(Modifier.width(140.dp).clickable(role = Role.Button) { onAlbumClick(album) }) {
                            ArtistReleaseArtwork(album.artworkPath, album.audioPath, Modifier.size(140.dp))
                            Text(album.title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            ArtistReleaseMetadata(album, sections.songCounts.getValue(album.id))
                        }
                    }
                }
            }
        }
        about?.let { content -> item("about") { Box(Modifier.padding(16.dp)) { content() } } }
        item("footer") {
            MornyeDetailFooter(uiState.tracks.size, (albumTotalDurationSeconds(uiState.tracks) / 60).toInt())
        }
    }
}

@Composable
private fun ArtistSectionHeading(title: String) {
    Text(title, Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ArtistReleaseMetadata(album: LibraryAlbum, count: Int) {
    val songs = formatDisplay(pluralStringResource(R.plurals.mornye_songs, count), count)
    Text(listOfNotNull(album.year.takeIf { it > 0 }?.toString(), songs).joinToString(" · "),
        color = Color.White, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ArtistReleaseArtwork(path: String?, audioPath: String?, modifier: Modifier) {
    val bitmap = rememberArtworkThumbnail(path, audioPath, targetPx = 360)
    Box(modifier.clip(LocalSistrumTokens.current.shapes.small)
        .background(LocalSistrumTokens.current.colors.opaqueFill), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else MaterialSymbol(MaterialSymbols.Album, null, tint = Color.White)
    }
}
