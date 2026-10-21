package com.example.agent.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.agent.ui.theme.spideyColors

/** A short confirmation before something is deleted. Dismissing it changes nothing. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        },
        confirmButton = {
            TextAction(text = confirmLabel, onClick = onConfirm, color = MaterialTheme.spideyColors.error)
        },
        dismissButton = {
            TextAction(text = "Cancel", onClick = onDismiss, color = MaterialTheme.colorScheme.onBackground)
        },
    )
}
