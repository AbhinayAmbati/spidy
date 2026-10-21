package com.example.agent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.agent.core.ai.local.ModelStatus
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/**
 * The on-device models Spidey runs on: Needle 3 for tool calling and Whistle for speech to
 * text, with each one's download state.
 */
@Composable
fun OnDeviceModelCard(
    needleStatus: ModelStatus,
    whistleStatus: ModelStatus,
    onDownloadNeedle: () -> Unit,
    onDownloadWhistle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpideyCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "On-device model",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Downloaded once on first launch, then runs on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )
        Spacer(Modifier.height(SpideySpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
            ModelRow(
                icon = Icons.Filled.Bolt,
                name = "Needle 3",
                role = "Tool calling",
                status = needleStatus,
                onDownload = onDownloadNeedle,
            )
            ModelRow(
                icon = Icons.Filled.Mic,
                name = "Whistle",
                role = "Speech to text",
                status = whistleStatus,
                onDownload = onDownloadWhistle,
            )
        }
    }
}

@Composable
private fun ModelRow(
    icon: ImageVector,
    name: String,
    role: String,
    status: ModelStatus,
    onDownload: () -> Unit,
) {
    val colors = MaterialTheme.spideyColors
    Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.blueTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = colors.blue)
            }
            Spacer(Modifier.width(SpideySpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = role,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            StatusPill(label = statusLabel(status), color = statusColor(status))
        }

        when (status) {
            is ModelStatus.Downloading -> LinearProgressIndicator(
                progress = { status.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = colors.blue,
                trackColor = colors.blueTint,
            )
            is ModelStatus.Failed -> Text(
                text = status.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
            else -> Unit
        }

        if (status is ModelStatus.NotDownloaded || status is ModelStatus.Failed) {
            SecondaryButton(
                text = if (status is ModelStatus.Failed) "Retry" else "Download",
                onClick = onDownload,
            )
        }
    }
}

private fun statusLabel(status: ModelStatus): String = when (status) {
    ModelStatus.Ready -> "Ready"
    ModelStatus.NotDownloaded -> "Not downloaded"
    is ModelStatus.Downloading -> "${status.percent}%"
    is ModelStatus.Failed -> "Failed"
}

@Composable
private fun statusColor(status: ModelStatus) = when (status) {
    ModelStatus.Ready -> MaterialTheme.spideyColors.success
    ModelStatus.NotDownloaded -> MaterialTheme.spideyColors.warning
    is ModelStatus.Downloading -> MaterialTheme.spideyColors.blue
    is ModelStatus.Failed -> MaterialTheme.spideyColors.error
}

@Preview(name = "On-device model", showBackground = true, widthDp = 360)
@Composable
private fun OnDeviceModelCardPreview() {
    SpideyTheme {
        OnDeviceModelCard(
            needleStatus = ModelStatus.Downloading(downloadedBytes = 12_000_000, totalBytes = 35_335_380),
            whistleStatus = ModelStatus.NotDownloaded,
            onDownloadNeedle = {},
            onDownloadWhistle = {},
            modifier = Modifier.width(360.dp),
        )
    }
}
