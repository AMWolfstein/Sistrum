package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

/** A one-pixel themed divider inset to align with detail-page row content. */
@Composable
fun InsetListDivider(modifier: Modifier = Modifier) {
    val colors = LocalSistrumColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(1.dp)
            .background(colors.borderGlass),
    )
}
