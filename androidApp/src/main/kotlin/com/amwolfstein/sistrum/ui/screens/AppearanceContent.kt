package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.amwolfstein.sistrum.ui.components.GlassToggle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.components.ActionList
import com.amwolfstein.sistrum.ui.components.ActionListContainerStyle
import com.amwolfstein.sistrum.ui.components.ActionListDivider
import com.amwolfstein.sistrum.ui.components.ActionListDividerStyle
import com.amwolfstein.sistrum.ui.components.ActionListItem
import com.amwolfstein.sistrum.ui.components.Card
import com.amwolfstein.sistrum.ui.components.Selection
import com.amwolfstein.sistrum.ui.components.SelectionOption

@Composable
internal fun AppearanceContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparencyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Selection(
                labelRes = R.string.appearance_theme_title,
                options = ThemeMode.entries.map { mode ->
                    SelectionOption(value = mode, labelRes = mode.labelRes)
                },
                selectedValue = themeMode,
                onValueSelected = onThemeModeSelected,
                hazeState = hazeState,
            )
            ActionListDivider(style = ActionListDividerStyle.FullWidth)
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.appearance_reduce_transparency,
                        trailingContent = {
                            GlassToggle(
                                checked = reduceTransparency,
                                onCheckedChange = onReduceTransparencyChanged,
                            )
                        },
                        onClick = { onReduceTransparencyChanged(!reduceTransparency) },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
            )
        }
    }
}
