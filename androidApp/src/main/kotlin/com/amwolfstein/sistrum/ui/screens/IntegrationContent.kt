package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.amwolfstein.sistrum.ui.components.GlassToggle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.lastfm.LastFmStatus
import com.amwolfstein.sistrum.lyrics.LyricsSettings
import com.amwolfstein.sistrum.ui.components.ActionList
import com.amwolfstein.sistrum.ui.components.ActionListContainerStyle
import com.amwolfstein.sistrum.ui.components.ActionListDividerStyle
import com.amwolfstein.sistrum.ui.components.ActionListItem
import com.amwolfstein.sistrum.ui.components.LabeledCard
import com.amwolfstein.sistrum.ui.components.SistrumPillButton
import com.amwolfstein.sistrum.ui.components.SistrumPillButtonVariant
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.MaterialSymbol
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import com.amwolfstein.sistrum.ui.components.rememberArtworkThumbnail
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

@Composable
internal fun IntegrationContent(
    onLastFmSelected: () -> Unit,
    onLyricsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActionList(
            items = listOf(
                ActionListItem(R.string.lastfm_title, onClick = onLastFmSelected),
                ActionListItem(R.string.lyrics_title, onClick = onLyricsSelected),
            ),
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}

@Composable
internal fun LastFmContent(
    status: LastFmStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSistrumColors.current
    val avatar = rememberArtworkThumbnail(status.avatarPath)
    Column(modifier = modifier) {
        HeroCard(
            title = stringResource(R.string.lastfm_title),
            description = if (status.connected) {
                stringResource(R.string.lastfm_connected_as, status.username)
            } else {
                stringResource(R.string.lastfm_description)
            },
            bottomContent = {
                SistrumPillButton(
                    label = stringResource(
                        when {
                            status.working -> R.string.lastfm_connecting
                            status.connected -> R.string.lastfm_disconnect
                            else -> R.string.lastfm_connect
                        },
                    ),
                    onClick = if (status.connected) onDisconnect else onConnect,
                    enabled = status.configured && !status.working,
                    variant = if (status.connected) SistrumPillButtonVariant.Secondary else SistrumPillButtonVariant.Primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                )
            },
        ) {
            if (status.connected && avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).testTag("lastfm-avatar"),
                )
            } else {
                MaterialSymbol(
                    symbol = MaterialSymbols.GraphicEq,
                    contentDescription = null,
                    size = 40.dp,
                    tint = colors.textMuted,
                    modifier = Modifier.testTag("lastfm-icon"),
                )
            }
        }
        if (!status.configured || status.failed) {
            Text(
                text = stringResource(if (status.configured) R.string.lastfm_error else R.string.lastfm_not_configured),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
internal fun LyricsContent(
    settings: LyricsSettings,
    onLrclibChanged: (Boolean) -> Unit,
    onKugouChanged: (Boolean) -> Unit,
    onEmbeddedChanged: (Boolean) -> Unit = {},
    onSidecarChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LabeledCard(label = stringResource(R.string.lyrics_data_sources), modifier = modifier) {
        ActionList(
            items = listOf(
                ActionListItem(R.string.lyrics_embedded, trailingContent = { GlassToggle(checked = settings.embedded, onCheckedChange = onEmbeddedChanged) }, onClick = { onEmbeddedChanged(!settings.embedded) }),
                ActionListItem(R.string.lyrics_sidecar, trailingContent = { GlassToggle(checked = settings.sidecar, onCheckedChange = onSidecarChanged) }, onClick = { onSidecarChanged(!settings.sidecar) }),
                ActionListItem(R.string.lyrics_lrclib, trailingContent = { GlassToggle(checked = settings.lrclib, onCheckedChange = onLrclibChanged) }, onClick = { onLrclibChanged(!settings.lrclib) }),
                ActionListItem(R.string.lyrics_kugou, trailingContent = { GlassToggle(checked = settings.kugou, onCheckedChange = onKugouChanged) }, onClick = { onKugouChanged(!settings.kugou) }),
            ),
            containerStyle = ActionListContainerStyle.Plain,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}
