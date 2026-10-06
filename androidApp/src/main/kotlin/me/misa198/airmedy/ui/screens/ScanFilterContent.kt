package me.misa198.airmedy.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.launch
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.MediaScanFilter
import me.misa198.airmedy.sync.MediaScanMode
import me.misa198.airmedy.sync.ScanFilterPreferences
import me.misa198.airmedy.sync.SkippedFilesSummaryStore
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListDivider
import me.misa198.airmedy.ui.components.ActionListDividerStyle
import me.misa198.airmedy.ui.components.AirmedyIconButton
import me.misa198.airmedy.ui.components.Card
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.LabeledCard
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.Selection
import me.misa198.airmedy.ui.components.SelectionOption
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/**
 * Whitelist/blacklist folder scan filter, ported from Rhythm's MediaScanSettingsScreen
 * (https://github.com/cromaguy/Rhythm) using this app's own component style. Song-level
 * allow/deny entries were intentionally left out: in Rhythm those are added via context-menu
 * actions scattered across several other screens (song info sheet, library, explorer, search),
 * not from this settings screen itself, and wiring that up is a separate, larger task. The
 * "include hidden whitelisted media" toggle was also left out: Rhythm walks the filesystem
 * directly, but this app's scanner queries MediaStore, which already excludes hidden/.nomedia
 * files before a cursor query ever sees them, so that toggle would have no real effect here.
 */
@Composable
internal fun ScanFilterContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val colors = LocalAirmedyColors.current
    val preferences = remember { ScanFilterPreferences(context) }
    val filter by preferences.filter.collectAsStateWithLifecycle(initialValue = MediaScanFilter())
    val tracks by AndroidSyncRuntime.syncStore().tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val skippedSummary by remember { SkippedFilesSummaryStore(context) }
        .summary
        .collectAsStateWithLifecycle(initialValue = null)

    val activeFolders = when (filter.mode) {
        MediaScanMode.Blacklist -> filter.blacklistedFolders
        MediaScanMode.Whitelist -> filter.whitelistedFolders
    }

    fun setActiveFolders(folders: Set<String>) {
        scope.launch {
            when (filter.mode) {
                MediaScanMode.Blacklist -> preferences.setBlacklistedFolders(folders)
                MediaScanMode.Whitelist -> preferences.setWhitelistedFolders(folders)
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> documentTreePath(uri)?.let { path -> setActiveFolders(activeFolders + path) } }
        }
    }

    val suggestedFolders = remember(tracks, activeFolders) {
        tracks.mapNotNull { track -> track.audioPath?.let { path -> runCatching { File(path).parent }.getOrNull() } }
            .distinct()
            .filter { it !in activeFolders }
            .sorted()
    }

    // `modifier` (settingsPageModifier from AppDestinationContent) already carries
    // fillMaxSize().verticalScroll(...) for the whole settings page; adding another
    // verticalScroll() here nested a scrollable Column inside an already-scrollable
    // one, which Compose measures with an infinite height constraint and crashes on.
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.scan_filter_description), color = colors.textMuted)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Selection(
                    labelRes = R.string.scan_filter_mode_title,
                    options = listOf(
                        SelectionOption(MediaScanMode.Blacklist, R.string.scan_filter_mode_blacklist),
                        SelectionOption(MediaScanMode.Whitelist, R.string.scan_filter_mode_whitelist),
                    ),
                    selectedValue = filter.mode,
                    onValueSelected = { mode -> scope.launch { preferences.setMode(mode) } },
                )
            }
            Text(
                text = stringResource(
                    if (filter.mode == MediaScanMode.Blacklist) R.string.scan_filter_mode_blacklist_desc else R.string.scan_filter_mode_whitelist_desc,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }

        LabeledCard(label = stringResource(R.string.scan_filter_folders_title)) {
            ActionList(
                items = buildList {
                    add(ActionListItem(R.string.scan_filter_add_folder, leadingSymbol = MaterialSymbols.Add, onClick = { folderPickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) }))
                    if (activeFolders.isNotEmpty()) {
                        add(ActionListItem(R.string.scan_filter_clear_folders, leadingSymbol = MaterialSymbols.Delete, onClick = { setActiveFolders(emptySet()) }))
                    }
                },
                containerStyle = ActionListContainerStyle.Plain,
            )
        }

        if (activeFolders.isNotEmpty()) {
            LabeledCard(
                label = stringResource(
                    if (filter.mode == MediaScanMode.Blacklist) R.string.scan_filter_blocked_folders else R.string.scan_filter_whitelisted_folders,
                ),
            ) {
                Column {
                    val sortedFolders = activeFolders.sorted()
                    sortedFolders.forEachIndexed { index, folder ->
                        FolderRow(folder = folder, onRemove = { setActiveFolders(activeFolders - folder) })
                        if (index < sortedFolders.lastIndex) ActionListDivider(style = ActionListDividerStyle.FullWidth)
                    }
                }
            }
        }

        if (suggestedFolders.isNotEmpty()) {
            LabeledCard(label = stringResource(R.string.scan_filter_suggested_folders_title)) {
                Column {
                    suggestedFolders.forEachIndexed { index, folder ->
                        SuggestedFolderRow(folder = folder, onAdd = { setActiveFolders(activeFolders + folder) })
                        if (index < suggestedFolders.lastIndex) ActionListDivider(style = ActionListDividerStyle.FullWidth)
                    }
                }
            }
        }

        LabeledCard(label = stringResource(R.string.scan_skipped_title)) {
            if (skippedSummaryLines(skippedSummary).isEmpty()) {
                Text(
                    text = stringResource(R.string.scan_skipped_none),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            } else {
                SkippedFilesSummaryText(
                    summary = skippedSummary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        LabeledCard(label = stringResource(R.string.scan_filter_tips_title)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScanFilterTip(MaterialSymbols.Delete, stringResource(R.string.scan_filter_tip_blacklist))
                ScanFilterTip(MaterialSymbols.Check, stringResource(R.string.scan_filter_tip_whitelist))
                ScanFilterTip(MaterialSymbols.Folder, stringResource(R.string.scan_filter_tip_folder))
            }
        }
    }
}

@Composable
private fun ScanFilterTip(symbol: String, text: String) {
    val colors = LocalAirmedyColors.current
    Row(verticalAlignment = Alignment.Top) {
        MaterialSymbol(symbol = symbol, contentDescription = null, size = 18.dp, tint = colors.textMuted)
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun FolderRow(folder: String, onRemove: () -> Unit) {
    val colors = LocalAirmedyColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol = MaterialSymbols.Folder, contentDescription = null, size = 20.dp, tint = colors.textMuted)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = File(folder).name.ifEmpty { folder },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AirmedyIconButton(
            symbol = MaterialSymbols.Close,
            label = stringResource(R.string.scan_filter_remove_folder),
            onClick = onRemove,
            size = 36.dp,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun SuggestedFolderRow(folder: String, onAdd: () -> Unit) {
    val colors = LocalAirmedyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onAdd,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol = MaterialSymbols.Folder, contentDescription = null, size = 20.dp, tint = colors.textMuted)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = File(folder).name.ifEmpty { folder },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AirmedyIconButton(
            symbol = MaterialSymbols.Add,
            label = stringResource(R.string.scan_filter_add_folder),
            onClick = onAdd,
            size = 36.dp,
            iconSize = 18.dp,
        )
    }
}

/**
 * Best-effort absolute path from a SAF tree-document URI, ported from Rhythm's
 * MediaScanSettingsScreen folder picker so it matches the same folder-path format
 * (`MediaStore.Audio.Media.DATA`) MediaStoreLibraryScanner already filters on.
 */
private fun documentTreePath(uri: android.net.Uri): String? = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val split = docId.split(":")
    if (split.size < 2) return null
    val storageType = split[0]
    val relativePath = split[1]
    when {
        storageType == "primary" -> "/storage/emulated/0/$relativePath"
        storageType.contains("-") -> "/storage/$storageType/$relativePath"
        else -> "/storage/emulated/0/$relativePath"
    }
}.getOrNull()
