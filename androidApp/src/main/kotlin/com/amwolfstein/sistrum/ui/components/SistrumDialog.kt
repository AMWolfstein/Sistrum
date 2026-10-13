package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

enum class SistrumDialogActionLayout {
    Horizontal,
    Vertical,
}

/** A compact alert or confirmation dialog with the app's rounded treatment. */
@Composable
fun SistrumDialog(
    title: String,
    description: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmVariant: SistrumPillButtonVariant = SistrumPillButtonVariant.Primary,
    actionLayout: SistrumDialogActionLayout = SistrumDialogActionLayout.Horizontal,
) {
    val colors = LocalSistrumColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(colors.card),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textMain,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
            when (actionLayout) {
                SistrumDialogActionLayout.Horizontal -> Row(
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SistrumPillButton(
                        label = dismissLabel,
                        onClick = onDismiss,
                        variant = SistrumPillButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    confirmLabel?.let { SistrumPillButton(label = it, onClick = onConfirm, variant = confirmVariant, modifier = Modifier.weight(1f)) }
                }
                SistrumDialogActionLayout.Vertical -> Column(
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SistrumPillButton(label = dismissLabel, onClick = onDismiss, variant = SistrumPillButtonVariant.Secondary)
                    confirmLabel?.let { SistrumPillButton(label = it, onClick = onConfirm, variant = confirmVariant) }
                }
            }
        }
    }
}
