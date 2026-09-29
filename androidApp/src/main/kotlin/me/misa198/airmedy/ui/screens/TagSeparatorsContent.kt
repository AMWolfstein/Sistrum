/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 AMWolfstein <https://github.com/AMWolfstein>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted from Rhythm's ArtistSeparatorsSettingsScreen and ArtistDelimitersBottomSheet
 * (https://github.com/cromaguy/Rhythm, app/src/main/java/chromahub/rhythm/app/shared/
 * presentation/screens/settings/ArtistSeparatorsSettingsScreen.kt and .../components/
 * bottomsheets/ArtistDelimitersBottomSheet.kt), using this app's components. Additions: an
 * Arabic preset and Arabic comma card, "," and "،" in quick add, and a rescan action,
 * because this app applies separators when scanning rather than when displaying.
 */
package me.misa198.airmedy.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.ArtistSeparator
import me.misa198.airmedy.sync.TagSeparatorPreferences
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListDividerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.AirmedyBottomSheet
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.AirmedyTextField
import me.misa198.airmedy.ui.components.AirmedyTextFieldSize
import me.misa198.airmedy.ui.components.LabeledCard
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private data class DelimiterPreset(val nameRes: Int, val tokens: List<String>)
private data class BaseDelimiter(val token: String, val nameRes: Int)

// "All" is this fork's default (every delimiter below); Standard is Rhythm's own default.
private val Presets = listOf(
    DelimiterPreset(R.string.tag_separators_preset_all, ArtistSeparator.DEFAULT_TOKENS),
    DelimiterPreset(R.string.tag_separators_preset_standard, listOf(";", "/")),
    DelimiterPreset(R.string.tag_separators_preset_minimal, listOf(";")),
    DelimiterPreset(R.string.tag_separators_preset_featured, listOf(";", "/", "feat.", "ft.", "featuring")),
    DelimiterPreset(R.string.tag_separators_preset_extended, listOf(";", "/", ",", "+", "&")),
    DelimiterPreset(R.string.tag_separators_preset_cjk, listOf("、", "／", "・", "•")),
    DelimiterPreset(R.string.tag_separators_preset_arabic, listOf("،", ";", "/")),
)

private val BaseDelimiters = listOf(
    BaseDelimiter("/", R.string.tag_separators_slash),
    BaseDelimiter(";", R.string.tag_separators_semicolon),
    BaseDelimiter(",", R.string.tag_separators_comma),
    BaseDelimiter("+", R.string.tag_separators_plus),
    BaseDelimiter("&", R.string.tag_separators_ampersand),
    BaseDelimiter("،", R.string.tag_separators_arabic_comma),
)

private val QuickSuggestions = listOf("feat.", "ft.", "featuring", "//", " x ", "with", "vs.", ",", "،", "、", "／", "・", "•")

private val ArtistPreviewExamples = listOf(
    "Kendrick Lamar; SZA",
    "AC\\/DC",
    "Tyler, The Creator",
    "Simon & Garfunkel",
    "Daft Punk feat. Pharrell Williams",
    "Artist 1 / Artist 2",
    "عمرو دياب، تامر حسني",
)

// Genres get their own list: "/" and "&" occur inside real genre names ("Hip-Hop/Rap").
private val GenrePresets = listOf(
    DelimiterPreset(R.string.tag_separators_preset_genre_default, ArtistSeparator.DEFAULT_GENRE_TOKENS),
    DelimiterPreset(R.string.tag_separators_preset_minimal, listOf(";")),
)

private val GenreBaseDelimiters = listOf(
    BaseDelimiter(";", R.string.tag_separators_semicolon),
    BaseDelimiter("،", R.string.tag_separators_arabic_comma),
    BaseDelimiter(",", R.string.tag_separators_comma),
    BaseDelimiter("/", R.string.tag_separators_slash),
    BaseDelimiter("&", R.string.tag_separators_ampersand),
)

private val GenrePreviewExamples = listOf(
    "Soundtrack; Hip-Hop/Rap",
    "Hip-Hop/Rap; Hardcore Rap; Rap",
    "R&B/Soul",
    "Singer/Songwriter",
    "Pop، Rock",
)

/** What the delimiter sheet edits: the artist/composer list or the genre list. */
private class DelimiterSheetConfig(
    val titleRes: Int,
    val presets: List<DelimiterPreset>,
    val baseDelimiters: List<BaseDelimiter>,
    val defaults: List<String>,
    val examples: List<String>,
) {
    val baseTokens = baseDelimiters.map { it.token }
}

private val ArtistSheet = DelimiterSheetConfig(
    R.string.tag_separators_configure, Presets, BaseDelimiters, ArtistSeparator.DEFAULT_TOKENS, ArtistPreviewExamples,
)
private val GenreSheet = DelimiterSheetConfig(
    R.string.tag_separators_configure_genres, GenrePresets, GenreBaseDelimiters, ArtistSeparator.DEFAULT_GENRE_TOKENS, GenrePreviewExamples,
)

private enum class SheetTarget { Artists, Genres }

@Composable
internal fun TagSeparatorsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val colors = LocalAirmedyColors.current
    val preferences = remember { TagSeparatorPreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = null)
    val appliedSignature by preferences.appliedSignature.collectAsStateWithLifecycle(initialValue = null)
    val customTokens by preferences.customDelimiters.collectAsStateWithLifecycle(initialValue = emptyList())
    val genreCustomTokens by preferences.genreCustomDelimiters.collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks by AndroidSyncRuntime.syncStore().tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var scanState by remember { mutableStateOf(LibraryScanUiState()) }
    val current = settings ?: return

    // A library scanned with other settings (or before this setting existed) keeps its old split.
    val needsRescan = tracks.isNotEmpty() && appliedSignature != current.signature

    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = stringResource(R.string.tag_separators_description), color = colors.textMuted)

        LabeledCard(label = stringResource(R.string.tag_separators_parsing)) {
            ActionList(
                items = buildList {
                    add(
                        ActionListItem(
                            R.string.tag_separators_enable,
                            trailingContent = {
                                Switch(checked = current.enabled, onCheckedChange = { scope.launch { preferences.setEnabled(it) } })
                            },
                            onClick = { scope.launch { preferences.setEnabled(!current.enabled) } },
                        ),
                    )
                    if (current.enabled) {
                        add(delimiterRow(R.string.tag_separators_delimiters, current.delimiters) { sheet = SheetTarget.Artists })
                        add(delimiterRow(R.string.tag_separators_genre_delimiters, current.genreDelimiters) { sheet = SheetTarget.Genres })
                    }
                },
                containerStyle = ActionListContainerStyle.Plain,
                dividerStyle = ActionListDividerStyle.FullWidth,
            )
        }

        if (needsRescan || scanState.isScanning || scanState.completed) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when {
                        scanState.isScanning -> stringResource(R.string.scan_running)
                        scanState.completed && !needsRescan -> scanCompleteSummary(scanState.tracks, scanState.albums, scanState.artists)
                        else -> stringResource(R.string.tag_separators_rescan_notice)
                    },
                    color = colors.textMuted,
                )
                if (needsRescan && !scanState.isScanning) {
                    AirmedyPillButton(
                        label = stringResource(R.string.tag_separators_rescan),
                        variant = AirmedyPillButtonVariant.Primary,
                        onClick = {
                            if (context.checkSelfPermission(readMediaPermission()) == PackageManager.PERMISSION_GRANTED) {
                                launchScan(scope, context) { scanState = it }
                            } else {
                                scanState = LibraryScanUiState(permissionDenied = true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (scanState.permissionDenied) {
                    Text(text = stringResource(R.string.scan_permission_denied), color = colors.textMuted)
                }
            }
        }

        LabeledCard(label = stringResource(R.string.tag_separators_about_title)) {
            Text(
                text = stringResource(R.string.tag_separators_about),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }

    when (sheet) {
        SheetTarget.Artists -> TagSeparatorsSheet(
            config = ArtistSheet,
            active = current.delimiters,
            savedCustom = customTokens,
            onSave = { tokens, custom ->
                scope.launch {
                    preferences.setCustomDelimiters(custom)
                    preferences.setDelimiters(tokens)
                }
            },
            onDismiss = { sheet = null },
        )
        SheetTarget.Genres -> TagSeparatorsSheet(
            config = GenreSheet,
            active = current.genreDelimiters,
            savedCustom = genreCustomTokens,
            onSave = { tokens, custom ->
                scope.launch {
                    preferences.setGenreCustomDelimiters(custom)
                    preferences.setGenreDelimiters(tokens)
                }
            },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

private enum class SheetPage { Main, AddCustom }

/** A settings row showing a delimiter list; capped so a long list can't squeeze the label. */
private fun delimiterRow(labelRes: Int, tokens: List<String>, onClick: () -> Unit) = ActionListItem(
    labelRes,
    trailingContent = {
        Text(
            text = tokens.joinToString("  "),
            modifier = Modifier.widthIn(max = 150.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAirmedyColors.current.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    onClick = onClick,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSeparatorsSheet(
    config: DelimiterSheetConfig,
    active: List<String>,
    savedCustom: List<String>,
    onSave: (tokens: List<String>, custom: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    var page by remember { mutableStateOf(SheetPage.Main) }
    var activeTokens by remember { mutableStateOf(active) }
    var customTokens by remember { mutableStateOf((savedCustom + active.filterNot { it in config.baseTokens }).distinct()) }
    var input by remember { mutableStateOf("") }

    fun toggle(token: String) {
        activeTokens = if (token in activeTokens) activeTokens - token else activeTokens + token
    }
    fun save() {
        if (activeTokens.isNotEmpty()) onSave(activeTokens, customTokens)
        onDismiss()
    }
    fun addCustom(raw: String) {
        val token = raw.trim()
        if (token.isEmpty()) return
        customTokens = (customTokens + token).distinct()
        activeTokens = (activeTokens + token).distinct()
        input = ""
        page = SheetPage.Main
    }

    AirmedyBottomSheet(
        title = {
            Text(
                stringResource(if (page == SheetPage.Main) config.titleRes else R.string.tag_separators_add_custom),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        // As in Rhythm, dismissing keeps the edited delimiters (unless none are left).
        onDismiss = { save() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                SheetPage.Main -> {
                    SheetLabel(R.string.tag_separators_presets)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.presets.forEach { preset ->
                            DelimiterChip(
                                label = stringResource(preset.nameRes),
                                selected = activeTokens.toSet() == preset.tokens.toSet(),
                                onClick = {
                                    activeTokens = preset.tokens
                                    customTokens = (customTokens + preset.tokens.filterNot { it in config.baseTokens }).distinct()
                                },
                            )
                        }
                    }
                    SheetLabel(R.string.tag_separators_active)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.baseDelimiters.forEach { base ->
                            DelimiterChip(
                                label = "${base.token}  ${stringResource(base.nameRes)}",
                                selected = base.token in activeTokens,
                                onClick = { toggle(base.token) },
                            )
                        }
                        customTokens.forEach { token ->
                            DelimiterChip(label = token, selected = token in activeTokens, onClick = { toggle(token) })
                        }
                        DelimiterChip(label = "+  " + stringResource(R.string.tag_separators_add_custom), selected = false, onClick = { page = SheetPage.AddCustom })
                    }
                    if (activeTokens.isEmpty()) {
                        Text(stringResource(R.string.tag_separators_empty_warning), color = colors.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    SheetLabel(R.string.tag_separators_preview)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        config.examples.forEach { example ->
                            Text(
                                text = "$example  →  " + ArtistSeparator.splitArtistNames(example, activeTokens, true).joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AirmedyPillButton(
                            label = stringResource(R.string.tag_separators_reset),
                            variant = AirmedyPillButtonVariant.Secondary,
                            onClick = { activeTokens = config.defaults },
                            modifier = Modifier.weight(1f),
                        )
                        AirmedyPillButton(
                            label = stringResource(R.string.save),
                            variant = AirmedyPillButtonVariant.Primary,
                            enabled = activeTokens.isNotEmpty(),
                            onClick = { save() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SheetPage.AddCustom -> {
                    AirmedyTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = stringResource(R.string.tag_separators_custom_hint),
                        modifier = Modifier.fillMaxWidth(),
                        size = AirmedyTextFieldSize.Medium,
                        onDone = { addCustom(input) },
                    )
                    SheetLabel(R.string.tag_separators_quick_add)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickSuggestions.forEach { suggestion ->
                            DelimiterChip(label = suggestion.trim(), selected = suggestion.trim() in activeTokens, onClick = { addCustom(suggestion) })
                        }
                    }
                    if (activeTokens.isNotEmpty()) {
                        SheetLabel(R.string.tag_separators_active)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeTokens.forEach { token ->
                                DelimiterChip(label = "$token  ✕", selected = true, onClick = { toggle(token) })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AirmedyPillButton(
                            label = stringResource(R.string.tag_separators_back),
                            variant = AirmedyPillButtonVariant.Secondary,
                            onClick = { page = SheetPage.Main },
                            modifier = Modifier.weight(1f),
                        )
                        AirmedyPillButton(
                            label = stringResource(R.string.tag_separators_add),
                            variant = AirmedyPillButtonVariant.Primary,
                            enabled = input.isNotBlank(),
                            onClick = { addCustom(input) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(labelRes: Int) {
    Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge, color = LocalAirmedyColors.current.textMuted)
}

@Composable
private fun DelimiterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = label,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.primary else colors.glassElevated)
            .border(1.dp, if (selected) colors.primary else colors.borderGlass, shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) colors.onPrimary else colors.textMain,
    )
}
