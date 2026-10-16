package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
fun DiscCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    audioPath: String? = null,
    fallbackSymbol: String = MaterialSymbols.MusicNote,
    artworkShape: Shape = LocalSistrumTokens.current.shapes.small,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = LocalSistrumTokens.current
    val bitmap = rememberArtworkThumbnail(artworkPath, audioPath, targetPx = 250)
    val clickModifier = remember(onClick, onLongClick) {
        if (onClick != null || onLongClick != null) {
            Modifier.combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
                role = Role.Button,
                interactionSource = MutableInteractionSource(),
                indication = null,
            )
        } else {
            Modifier
        }
    }

    Column(
        modifier = modifier
            .clip(tokens.shapes.small)
            .then(clickModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artworkShape)
                .background(tokens.colors.opaqueFill)
                .border(1.dp, tokens.colors.divider, artworkShape),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                MaterialSymbol(
                    symbol = fallbackSymbol,
                    contentDescription = null,
                    size = 36.dp,
                    tint = tokens.colors.mutedText,
                )
            }
        }

        Text(
            text = title,
            style = discCardTextStyle().copy(fontWeight = FontWeight.SemiBold),
            color = tokens.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DiscCardTitleGap, start = 2.dp, end = 2.dp),
        )

        Text(
            text = subtitle,
            style = discCardTextStyle(),
            color = tokens.colors.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DiscCardSubtitleGap, start = 2.dp, end = 2.dp, bottom = DiscCardBottomAllowance),
        )
    }
}

private val DiscCardTitleGap = 8.dp
private val DiscCardSubtitleGap = 2.dp

/** Room below the subtitle for glyphs that reach past the line box, such as Arabic descenders. */
private val DiscCardBottomAllowance = 4.dp

/**
 * One fixed line box per text line. Fallback fonts for scripts such as Arabic have taller
 * ascent and descent than Latin, so without it a card's height depended on the script of its
 * title and artist, and the fixed-height grids that hold the cards clipped the last line.
 */
@Composable
private fun discCardTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
)

/** The height of a [DiscCard] of [width]: square artwork, one title line and one subtitle line. */
@Composable
internal fun discCardHeight(width: Dp): Dp {
    // Text's line box includes font metrics beyond the requested lineHeight (Trim.None).
    // Use the measured single-line height so larger fonts cannot squeeze the subtitle.
    val lineHeightPx = rememberTextMeasurer().measure(" ", style = discCardTextStyle(), maxLines = 1).size.height
    val lineHeight = with(LocalDensity.current) { lineHeightPx.toDp() }
    return width + DiscCardTitleGap + lineHeight + DiscCardSubtitleGap + lineHeight + DiscCardBottomAllowance
}
