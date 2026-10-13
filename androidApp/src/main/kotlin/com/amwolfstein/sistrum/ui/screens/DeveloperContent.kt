package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.engine.EngineKind
import com.amwolfstein.sistrum.ui.components.Card
import com.amwolfstein.sistrum.ui.components.Selection
import com.amwolfstein.sistrum.ui.components.SelectionOption
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

@Composable
internal fun DeveloperContent(
    engine: EngineKind,
    onEngineSelected: (EngineKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSistrumColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Selection(
                labelRes = R.string.developer_engine,
                options = listOf(
                    SelectionOption(EngineKind.Native, R.string.developer_engine_native),
                    SelectionOption(EngineKind.Media3, R.string.developer_engine_media3),
                ),
                selectedValue = engine,
                onValueSelected = onEngineSelected,
            )
        }
        Text(
            text = stringResource(R.string.developer_engine_note),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
