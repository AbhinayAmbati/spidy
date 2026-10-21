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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/** Where a downloadable on-device model stands. */
enum class ModelStatus { NotDownloaded, Ready }

/**
 * The on-device models Spidey runs on: Needle 3 for tool calling and Whistle for speech to
 * text. This is a static placeholder until the download flow lands; it takes the status as a
 * parameter so wiring it up later only changes the caller.
 */
@Composable
fun OnDeviceModelCard(
    needleStatus: ModelStatus,
    whistleStatus: ModelStatus,
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
            )
            ModelRow(
                icon = Icons.Filled.Mic,
                name = "Whistle",
                role = "Speech to text",
                status = whistleStatus,
            )
        }
    }
}

@Composable
private fun ModelRow(icon: ImageVector, name: String, role: String, status: ModelStatus) {
    val colors = MaterialTheme.spideyColors
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
        StatusPill(
            label = if (status == ModelStatus.Ready) "Ready" else "Not downloaded",
            color = if (status == ModelStatus.Ready) colors.success else colors.warning,
        )
    }
}

@Preview(name = "On-device model", showBackground = true, widthDp = 360)
@Composable
private fun OnDeviceModelCardPreview() {
    SpideyTheme {
        OnDeviceModelCard(
            needleStatus = ModelStatus.NotDownloaded,
            whistleStatus = ModelStatus.Ready,
            modifier = Modifier.width(360.dp),
        )
    }
}
