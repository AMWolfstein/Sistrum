package me.misa198.airmedy.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.misa198.airmedy.R
import me.misa198.airmedy.player.DecodeFailureEntry
import me.misa198.airmedy.player.DecodeFailureLog
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.Card
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private const val DecodeFailureSaveFileName = "sistrum-decode-failures.txt"

/** FR-064a: shows the bounded decode-failure log with share, save-as-file and clear actions. */
@Composable
internal fun DecodeFailureLogContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalAirmedyColors.current
    val log = remember(context) { DecodeFailureLog.forContext(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DecodeFailureEntry>>(emptyList()) }
    var pendingSaveText by remember { mutableStateOf("") }

    LaunchedEffect(log) {
        entries = withContext(Dispatchers.IO) { log.entries() }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = pendingSaveText
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
            }
        }
    }

    val hasEntries = entries.isNotEmpty()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(
            symbol = MaterialSymbols.Folder,
            title = stringResource(R.string.decode_log_title),
            description = stringResource(R.string.decode_log_description),
        )
        Card(contentPadding = PaddingValues(20.dp)) {
            Text(
                text = if (hasEntries) {
                    pluralStringResource(R.plurals.decode_log_count, entries.size, entries.size)
                } else {
                    stringResource(R.string.decode_log_empty)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasEntries) colors.textMain else colors.textMuted,
            )
        }
        AirmedyPillButton(
            label = stringResource(R.string.decode_log_share),
            onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { log.exportText() }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            },
            enabled = hasEntries,
            variant = AirmedyPillButtonVariant.Primary,
        )
        AirmedyPillButton(
            label = stringResource(R.string.decode_log_save),
            onClick = {
                scope.launch {
                    pendingSaveText = withContext(Dispatchers.IO) { log.exportText() }
                    saveLauncher.launch(DecodeFailureSaveFileName)
                }
            },
            enabled = hasEntries,
            variant = AirmedyPillButtonVariant.Secondary,
        )
        AirmedyPillButton(
            label = stringResource(R.string.decode_log_clear),
            onClick = {
                scope.launch {
                    entries = withContext(Dispatchers.IO) {
                        log.clear()
                        log.entries()
                    }
                }
            },
            enabled = hasEntries,
            variant = AirmedyPillButtonVariant.Destructive,
        )
    }
}
