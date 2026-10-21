package com.example.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.agent.ui.theme.SheetShape
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * Sensitive actions pause here. The user is always the last word.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    title: String,
    prompt: String,
    detail: String?,
    allowLabel: String,
    cancelLabel: String,
    onAllow: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = SheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpideySpacing.lg)
                .padding(bottom = SpideySpacing.lg)
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Spacer(Modifier.height(SpideySpacing.sm))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
            Spacer(Modifier.height(SpideySpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpideySpacing.sm),
            ) {
                SecondaryButton(text = cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
                PrimaryButton(text = allowLabel, onClick = onAllow, modifier = Modifier.weight(1f))
            }
        }
    }
}
