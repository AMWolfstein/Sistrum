package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.BuildConfig
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.components.ActionList
import com.amwolfstein.sistrum.ui.components.ActionListContainerStyle
import com.amwolfstein.sistrum.ui.components.ActionListDividerStyle
import com.amwolfstein.sistrum.ui.components.ActionListItem
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.LabeledCard
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

private const val SistrumGithubUrl = "https://github.com/misa198/airmedy"
private const val SistrumLicenseUrl = "https://github.com/misa198/airmedy/blob/master/LICENSE"
private const val SistrumGithubSponsorsUrl = "https://github.com/sponsors/misa198"
private const val SistrumKofiUrl = "https://ko-fi.com/misa198"
private const val SistrumBuyMeACoffeeUrl = "https://buymeacoffee.com/misa1982"
private const val SistrumPatreonUrl = "https://www.patreon.com/c/misa198"

@Composable
internal fun AboutContent(
    developerUnlocked: Boolean,
    onVersionTapped: () -> Unit,
    onDeveloperSelected: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSistrumColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroCard(
            title = stringResource(R.string.app_name),
            description = stringResource(R.string.about_description),
        ) {
            Image(
                painter = painterResource(R.drawable.sistrum_about_app_icon),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
        ActionList(
            items = buildList {
                add(
                    ActionListItem(
                        labelRes = R.string.about_version,
                        trailingContent = {
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textMuted,
                            )
                        },
                        onClick = onVersionTapped,
                    ),
                )
                if (developerUnlocked) {
                    add(
                        ActionListItem(
                            labelRes = R.string.developer_title,
                            onClick = onDeveloperSelected,
                        ),
                    )
                }
                add(
                    ActionListItem(
                        labelRes = R.string.about_github,
                        onClick = { onOpenExternalUrl(SistrumGithubUrl) },
                    ),
                )
                add(
                    ActionListItem(
                        labelRes = R.string.about_license,
                        onClick = { onOpenExternalUrl(SistrumLicenseUrl) },
                    ),
                )
            },
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
        LabeledCard(
            label = stringResource(R.string.about_sponsor),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.about_sponsor_github,
                        onClick = { onOpenExternalUrl(SistrumGithubSponsorsUrl) },
                    ),
                    ActionListItem(
                        labelRes = R.string.about_sponsor_kofi,
                        onClick = { onOpenExternalUrl(SistrumKofiUrl) },
                    ),
                    ActionListItem(
                        labelRes = R.string.about_sponsor_bmac,
                        onClick = { onOpenExternalUrl(SistrumBuyMeACoffeeUrl) },
                    ),
                    ActionListItem(
                        labelRes = R.string.about_sponsor_patreon,
                        onClick = { onOpenExternalUrl(SistrumPatreonUrl) },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
                dividerStyle = ActionListDividerStyle.FullWidth,
            )
        }
    }
}
