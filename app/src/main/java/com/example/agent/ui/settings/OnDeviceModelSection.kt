package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.agent.core.ai.local.ModelStatus
import com.example.agent.ui.components.IconBadge
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.ProgressBar
import com.example.agent.ui.components.RowDivider
import com.example.agent.ui.components.SectionLabel
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.components.TextAction
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/**
 * The on-device models Spidey runs on: Needle 3 for tool calling and Whistle for speech to
 * text, each with its download state, a progress line while it downloads and a Download or
 * Retry action when it is missing or failed.
 */
@Composable
fun OnDeviceModelSection(
    needleStatus: ModelStatus,
    whistleStatus: ModelStatus,
    onDownloadNeedle: () -> Unit,
    onDownloadWhistle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(text = "On-device model")
        ModelRow(
            name = "Needle 3",
            role = "Tool calling",
            status = needleStatus,
            onDownload = onDownloadNeedle,
            leading = { IconBadge(icon = Icons.Outlined.Build) },
        )
        RowDivider(inset = true)
        ModelRow(
            name = "Whistle",
            role = "Speech to text",
            status = whistleStatus,
            onDownload = onDownloadWhistle,
            leading = { IconBadge(icon = Icons.Outlined.Mic) },
        )
    }
}

@Composable
private fun ModelRow(
    name: String,
    role: String,
    status: ModelStatus,
    onDownload: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val colors = MaterialTheme.spideyColors
    val subtitle = if (status is ModelStatus.Failed) status.message else role
    Column {
        ListRow(
            title = name,
            subtitle = subtitle,
            subtitleColor = if (status is ModelStatus.Failed) colors.error else colors.textSecondary,
            leading = leading,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status is ModelStatus.NotDownloaded || status is ModelStatus.Failed) {
                        TextAction(
                            text = if (status is ModelStatus.Failed) "Retry" else "Download",
                            onClick = onDownload,
                        )
                    } else {
                        StatusPill(label = statusLabel(status), color = statusColor(status))
                    }
                }
            },
        )
        if (status is ModelStatus.Downloading) {
            ProgressBar(
                fraction = status.percent / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpideySpacing.screenGutter),
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
private fun statusColor(status: ModelStatus): Color = when (status) {
    ModelStatus.Ready -> MaterialTheme.spideyColors.success
    ModelStatus.NotDownloaded -> MaterialTheme.spideyColors.warning
    is ModelStatus.Downloading -> MaterialTheme.spideyColors.blue
    is ModelStatus.Failed -> MaterialTheme.spideyColors.error
}

@Preview(name = "On-device model", showBackground = true, widthDp = 360)
@Composable
private fun OnDeviceModelSectionPreview() {
    SpideyTheme {
        OnDeviceModelSection(
            needleStatus = ModelStatus.Downloading(downloadedBytes = 12_000_000, totalBytes = 35_335_380),
            whistleStatus = ModelStatus.NotDownloaded,
            onDownloadNeedle = {},
            onDownloadWhistle = {},
        )
    }
}
