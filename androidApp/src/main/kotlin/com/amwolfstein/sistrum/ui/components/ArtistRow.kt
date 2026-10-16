package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
fun ArtistRow(
    name: String,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    portrait: @Composable (() -> Unit)? = null,
) {
    val tokens = LocalSistrumTokens.current
    val bitmap = if (portrait == null) rememberArtworkThumbnail(artworkPath) else null
    val clickModifier = remember(onClick, onLongClick) {
        if (onClick != null || onLongClick != null) Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        ) else Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(
                top = tokens.spacing.small, end = tokens.spacing.small,
                bottom = tokens.spacing.small, start = tokens.spacing.extraLarge,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tokens.colors.opaqueFill)
                .border(1.dp, tokens.colors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                portrait()
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                MaterialSymbol(
                    symbol = MaterialSymbols.Person,
                    contentDescription = null,
                    size = 22.dp,
                    tint = tokens.colors.mutedText,
                )
            }
        }

        Text(
            text = name,
            modifier = Modifier
                .weight(1f)
                .padding(start = tokens.spacing.medium, end = tokens.spacing.small),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(
            onClick = { onClick?.invoke() },
            modifier = Modifier.size(48.dp),
        ) {
            MaterialSymbol(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = stringResource(R.string.artist_row_open),
                size = 20.dp,
                tint = tokens.colors.mutedText,
            )
        }
    }
}
