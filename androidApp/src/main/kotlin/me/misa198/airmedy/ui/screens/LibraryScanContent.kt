package me.misa198.airmedy.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.misa198.airmedy.R
import me.misa198.airmedy.player.decoders.SkipReason
import me.misa198.airmedy.player.decoders.parseSkipReason
import me.misa198.airmedy.sync.LibraryScanOutcome
import me.misa198.airmedy.sync.LibraryScanRunner
import me.misa198.airmedy.sync.SkippedFilesSummary
import me.misa198.airmedy.sync.SkippedFilesSummaryStore
import me.misa198.airmedy.sync.readMediaPermission as syncReadMediaPermission
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

internal data class LibraryScanUiState(
    val isScanning: Boolean = false,
    val tracks: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val completed: Boolean = false,
    val permissionDenied: Boolean = false,
)

@Composable
internal fun LibraryScanContent(
    modifier: Modifier = Modifier,
    onScanFilterSelected: () -> Unit = {},
    onTagSeparatorsSelected: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(LibraryScanUiState()) }
    val colors = LocalAirmedyColors.current
    val context = LocalContext.current.applicationContext
    val skippedSummary by remember { SkippedFilesSummaryStore(context) }
        .summary
        .collectAsState(initial = null)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchScan(scope, context) { uiState = it }
        } else {
            uiState = LibraryScanUiState(permissionDenied = true)
        }
    }
    val startScan: () -> Unit = {
        val permission = readMediaPermission()
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            launchScan(scope, context) { uiState = it }
        } else {
            uiState = LibraryScanUiState()
            permissionLauncher.launch(permission)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.scan_description),
            color = colors.textMuted,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ActionList(
            items = listOf(
                ActionListItem(R.string.scan_filter_title, onClick = onScanFilterSelected),
                ActionListItem(R.string.tag_separators_title, onClick = onTagSeparatorsSelected),
            ),
            containerStyle = ActionListContainerStyle.Card,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when {
            uiState.isScanning -> Text(
                text = stringResource(R.string.scan_running),
                color = colors.textMuted,
            )
            uiState.permissionDenied -> {
                Text(
                    text = stringResource(R.string.scan_permission_denied),
                    color = colors.textMuted,
                )
                Spacer(modifier = Modifier.height(16.dp))
                ScanButton(
                    label = stringResource(R.string.scan_start),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = startScan,
                )
            }
            uiState.completed -> {
                Text(
                    text = scanCompleteSummary(uiState.tracks, uiState.albums, uiState.artists),
                    color = colors.textMuted,
                )
                SkippedFilesSummaryText(
                    summary = skippedSummary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                ScanButton(
                    label = stringResource(R.string.scan_again),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = startScan,
                )
            }
            else -> {
                SkippedFilesSummaryText(
                    summary = skippedSummary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                ScanButton(
                    label = stringResource(R.string.scan_start),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = startScan,
                )
            }
        }
    }
}

@Composable
private fun ScanButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AirmedyPillButton(
        label = label,
        variant = AirmedyPillButtonVariant.Primary,
        onClick = onClick,
        modifier = modifier,
    )
}

/** "729 tracks, 235 albums, 71 artists", with each count in its plural form. */
@Composable
internal fun scanCompleteSummary(tracks: Int, albums: Int, artists: Int): String = stringResource(
    R.string.scan_complete,
    pluralStringResource(R.plurals.scan_complete_tracks, tracks, tracks),
    pluralStringResource(R.plurals.scan_complete_albums, albums, albums),
    pluralStringResource(R.plurals.scan_complete_artists, artists, artists),
)

/**
 * FR-066: groups a scan's skipped entries by reason text (so dsf and dff merge under
 * "unsupported format (DSD)") and sums their counts. Sorted by count descending, then
 * reason. Empty for a null or empty summary.
 */
internal fun skippedSummaryLines(summary: SkippedFilesSummary?): List<Pair<Int, String>> {
    if (summary == null) return emptyList()
    val counts = linkedMapOf<String, Int>()
    summary.entries.forEach { entry -> counts[entry.reason] = (counts[entry.reason] ?: 0) + entry.count }
    return counts.map { (reason, count) -> count to reason }
        .sortedWith(compareByDescending<Pair<Int, String>> { it.first }.thenBy { it.second })
}

/**
 * FR-066: the per-scan skipped-file summary. Renders nothing when there are no lines.
 */
@Composable
internal fun SkippedFilesSummaryText(summary: SkippedFilesSummary?, modifier: Modifier = Modifier) {
    val lines = skippedSummaryLines(summary)
    if (lines.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { (count, reason) ->
            Text(
                text = pluralStringResource(R.plurals.scan_skipped_files, count, count, skipReasonText(reason)),
                color = LocalAirmedyColors.current.textMuted,
            )
        }
    }
}

/** A skip reason's user-facing text: known forms are translated, refusals are shown as is. */
@Composable
private fun skipReasonText(reason: String): String = when (val parsed = parseSkipReason(reason)) {
    is SkipReason.UnsupportedFormat -> stringResource(R.string.scan_skip_unsupported_format, parsed.label)
    is SkipReason.NoDecoder -> stringResource(R.string.scan_skip_no_decoder, parsed.label)
    is SkipReason.Other -> parsed.text
}

internal fun launchScan(scope: CoroutineScope, context: Context, onResult: (LibraryScanUiState) -> Unit) {
    scope.launch {
        onResult(LibraryScanUiState(isScanning = true))
        onResult(
            when (val outcome = LibraryScanRunner.scan(context)) {
                is LibraryScanOutcome.Completed -> LibraryScanUiState(
                    tracks = outcome.tracks,
                    albums = outcome.albums,
                    artists = outcome.artists,
                    completed = true,
                )
                LibraryScanOutcome.NothingFound, LibraryScanOutcome.Failed -> LibraryScanUiState()
            },
        )
    }
}

internal fun readMediaPermission(): String = syncReadMediaPermission()