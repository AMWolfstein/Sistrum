package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
internal fun PlaylistRow(
    playlistId: String,
    name: String,
    artworkPaths: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val tokens = LocalSistrumTokens.current
    Row(
        modifier = modifier.fillMaxWidth().combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick).padding(start = tokens.spacing.extraLarge, top = tokens.spacing.small, end = tokens.spacing.small, bottom = tokens.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistArtwork(playlistId, artworkPaths)
        Column(modifier = Modifier.weight(1f).padding(horizontal = tokens.spacing.medium)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            MaterialSymbol(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = stringResource(R.string.playlist_row_open),
                size = 20.dp,
                tint = tokens.colors.mutedText,
            )
        }
    }
}

@Composable
internal fun PlaylistArtwork(playlistId: String, artworkPaths: List<String>, modifier: Modifier = Modifier, size: Dp = 110.dp) {
    val tokens = LocalSistrumTokens.current
    Box(
        modifier = modifier
            .size(size)
            .clip(tokens.shapes.small)
            .background(tokens.colors.opaqueFill)
            .border(1.dp, tokens.colors.divider, tokens.shapes.small)
            .then(if (artworkPaths.size >= 4) Modifier.testTag("playlist-artwork-mosaic") else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val targetPx = if (size > 110.dp) 480 else 128
        when {
            artworkPaths.size >= 4 -> {
                Column(Modifier.fillMaxSize()) {
                    PlaylistArtworkPair(artworkPaths.take(2), targetPx)
                    PlaylistArtworkPair(artworkPaths.drop(2).take(2), targetPx)
                }
            }
            artworkPaths.isNotEmpty() -> PlaylistArtworkImage(artworkPaths.first(), Modifier.fillMaxSize(), targetPx)
            else -> MaterialSymbol(
                symbol = if (playlistId == "favorites") MaterialSymbols.Favorite else MaterialSymbols.QueueMusic,
                contentDescription = null,
                size = 30.dp,
                tint = tokens.colors.mutedText,
                filled = playlistId == "favorites",
            )
        }
    }
}

@Composable
private fun ColumnScope.PlaylistArtworkPair(paths: List<String>, targetPx: Int) {
    Row(Modifier.fillMaxWidth().weight(1f)) {
        paths.forEach { path -> PlaylistArtworkImage(path, Modifier.weight(1f).fillMaxSize(), targetPx) }
    }
}

@Composable
private fun PlaylistArtworkImage(path: String, modifier: Modifier, targetPx: Int) {
    val bitmap = rememberArtworkThumbnail(path, targetPx = targetPx)
    if (bitmap != null) Image(bitmap, null, modifier = modifier, contentScale = ContentScale.Crop)
}
