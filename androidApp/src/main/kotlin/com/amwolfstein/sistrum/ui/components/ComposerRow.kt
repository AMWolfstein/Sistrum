package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

@Composable
fun ComposerRow(
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = LocalSistrumTokens.current
    val clickModifier = remember(onClick, onLongClick) {
        if (onClick != null || onLongClick != null) Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        ) else Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(clickModifier)
            .padding(
                end = tokens.spacing.small,
                start = tokens.spacing.extraLarge,
                top = tokens.spacing.small,
                bottom = tokens.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier
                .weight(1f)
                .padding(end = tokens.spacing.small),
            style = MaterialTheme.typography.titleMedium,
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
                contentDescription = stringResource(R.string.composer_row_open),
                size = 20.dp,
                tint = tokens.colors.mutedText,
            )
        }
    }
}
