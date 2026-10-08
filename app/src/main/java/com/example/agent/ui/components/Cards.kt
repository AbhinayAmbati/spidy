package com.example.agent.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

@Composable
fun TaskCard(
    prompt: String,
    appName: String?,
    statusLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(SpideySpacing.cardPadding),
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(SpideySpacing.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (appName != null) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.spideyColors.brownAccent,
            )
        }
    }
}

@Composable
fun ProviderCard(
    name: String,
    @DrawableRes iconRes: Int,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = SpideySpacing.md, vertical = SpideySpacing.sm,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (selected) MaterialTheme.spideyColors.brownAccent
                else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(SpideySpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.spideyColors.textSecondary,
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.spideyColors.brownAccent,
                ),
            )
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
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            StatusDot(
                color = if (granted) MaterialTheme.spideyColors.success else MaterialTheme.spideyColors.warning,
                label = if (granted) "Granted" else "Not granted",
            )
            Spacer(Modifier.width(SpideySpacing.xs))
            Text(
                text = if (granted) "Enabled" else "Not enabled",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.spideyColors.success else MaterialTheme.spideyColors.textSecondary,
            )
        }
        Spacer(Modifier.height(SpideySpacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )
        if (!granted) {
            Spacer(Modifier.height(SpideySpacing.md))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
