package com.example.agent.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/** One task in a list: the request in bold, the app it used, and a coloured outcome pill. */
@Composable
fun TaskCard(
    prompt: String,
    appName: String?,
    state: TaskState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableSpideyCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(SpideySpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (appName != null) {
                Text(
                    text = appName.substringAfterLast('.'),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusPill(label = taskStateLabel(state), color = taskStateColor(state))
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown when [granted] is true but something still needs the user's attention. */
    warning: String? = null,
) {
    SpideyCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SpideySpacing.xs))
            StatusPill(
                label = if (granted) "Enabled" else "Not enabled",
                color = if (granted) MaterialTheme.spideyColors.success else MaterialTheme.spideyColors.warning,
            )
        }
        Spacer(Modifier.height(SpideySpacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )
        if (warning != null) {
            Spacer(Modifier.height(SpideySpacing.sm))
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.error,
            )
        }
        // The button is hidden only when the permission is genuinely in place. If it is
        // granted but still not working, the action stays available.
        if (!granted || warning != null) {
            Spacer(Modifier.height(SpideySpacing.md))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
