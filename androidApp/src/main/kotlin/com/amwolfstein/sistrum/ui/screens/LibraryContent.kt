package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.components.ActionListItem
import com.amwolfstein.sistrum.ui.components.MaterialSymbol
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
internal fun LibraryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    onSearchSelected: (() -> Unit)? = null,
    onArtistsSelected: (() -> Unit)? = null,
    onAlbumsSelected: (() -> Unit)? = null,
    onTracksSelected: (() -> Unit)? = null,
    onGenresSelected: (() -> Unit)? = null,
    onComposersSelected: (() -> Unit)? = null,
    onPlaylistsSelected: (() -> Unit)? = null,
) {
    val tokens = LocalSistrumTokens.current
    val destinations = listOf(
        ActionListItem(
            labelRes = R.string.library_search,
            leadingSymbol = MaterialSymbols.Search,
            onClick = onSearchSelected,
        ),
        ActionListItem(
            labelRes = R.string.library_artists,
            leadingSymbol = MaterialSymbols.People,
            onClick = onArtistsSelected,
        ),
        ActionListItem(
            labelRes = R.string.library_albums,
            leadingSymbol = MaterialSymbols.Album,
            onClick = onAlbumsSelected,
        ),
        ActionListItem(
            labelRes = R.string.library_tracks,
            leadingSymbol = MaterialSymbols.MusicNote,
            onClick = onTracksSelected,
        ),
        ActionListItem(
            labelRes = R.string.library_genres,
            leadingSymbol = MaterialSymbols.Label,
            onClick = onGenresSelected,
        ),
        ActionListItem(
            labelRes = R.string.library_composers,
            leadingSymbol = MaterialSymbols.StylusFountainPen,
            onClick = onComposersSelected,
        ),
        ActionListItem(
            labelRes = R.string.library_playlists,
            leadingSymbol = MaterialSymbols.QueueMusic,
            onClick = onPlaylistsSelected,
        ),
    )
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        items(destinations, key = { it.labelRes }) { destination ->
            val label = stringResource(destination.labelRes)
            val clickModifier = destination.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) }
                ?: Modifier
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tokens.shapes.medium)
                    .background(tokens.colors.opaqueFill)
                    .then(clickModifier)
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .heightIn(min = 72.dp)
                    .padding(tokens.spacing.large),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destination.leadingSymbol?.let { symbol ->
                    MaterialSymbol(symbol, null, size = 24.dp, tint = tokens.colors.accent)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.text,
                    modifier = Modifier.weight(1f),
                )
                MaterialSymbol(MaterialSymbols.ChevronRight, null, size = 20.dp, tint = tokens.colors.mutedText)
            }
        }
    }
}
