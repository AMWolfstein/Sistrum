package com.amwolfstein.sistrum.ui.screens

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
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.diagnostics.Diagnostics
import com.amwolfstein.sistrum.player.DecodeFailureLog
import com.amwolfstein.sistrum.ui.components.SistrumPillButton
import com.amwolfstein.sistrum.ui.components.SistrumPillButtonVariant
import com.amwolfstein.sistrum.ui.components.Card
import com.amwolfstein.sistrum.ui.components.HeroCard
import com.amwolfstein.sistrum.ui.components.MaterialSymbols
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

private const val DecodeFailureSaveFileName = "sistrum-diagnostics.txt"

/** Diagnostics log (FR-064a decode failures + app events): count, share, save as file and clear. */
@Composable
internal fun DecodeFailureLogContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalSistrumColors.current
    val log = remember(context) { DecodeFailureLog.forContext(context) }
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf(0) }
    var pendingSaveText by remember { mutableStateOf("") }

    LaunchedEffect(log) {
        count = withContext(Dispatchers.IO) { log.entries().size + Diagnostics.log()?.entries().orEmpty().size }
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

    val hasEntries = count > 0

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(
            symbol = MaterialSymbols.Folder,
            title = stringResource(R.string.decode_log_title),
            description = stringResource(R.string.decode_log_description),
        )
        Card(contentPadding = PaddingValues(20.dp)) {
            Text(
                text = if (hasEntries) {
                    pluralStringResource(R.plurals.decode_log_count, count, count)
                } else {
                    stringResource(R.string.decode_log_empty)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasEntries) colors.textMain else colors.textMuted,
            )
        }
        SistrumPillButton(
            label = stringResource(R.string.decode_log_share),
            onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { Diagnostics.exportText(context) }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            },
            enabled = true,
            variant = SistrumPillButtonVariant.Primary,
        )
        SistrumPillButton(
            label = stringResource(R.string.decode_log_save),
            onClick = {
                scope.launch {
                    pendingSaveText = withContext(Dispatchers.IO) { Diagnostics.exportText(context) }
                    saveLauncher.launch(DecodeFailureSaveFileName)
                }
            },
            enabled = true,
            variant = SistrumPillButtonVariant.Secondary,
        )
        SistrumPillButton(
            label = stringResource(R.string.decode_log_clear),
            onClick = {
                scope.launch {
                    count = withContext(Dispatchers.IO) {
                        log.clear()
                        Diagnostics.log()?.clear()
                        0
                    }
                }
            },
            enabled = hasEntries,
            variant = SistrumPillButtonVariant.Destructive,
        )
    }
}
