package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.agent.ui.components.IconBadge
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.RowDivider
import com.example.agent.ui.components.SectionLabel
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors
import androidx.compose.material3.MaterialTheme

/** Where a downloadable on-device model stands. */
enum class ModelStatus { NotDownloaded, Ready }

/**
 * The on-device models Spidey runs on: Needle 3 for tool calling and Whistle for speech to
 * text. Static until the download flow lands; it takes the status as a parameter so wiring it
 * up later only changes the caller.
 */
@Composable
fun OnDeviceModelSection(
    needleStatus: ModelStatus,
    whistleStatus: ModelStatus,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(text = "On-device model")
        ModelRow(
            name = "Needle 3",
            role = "Tool calling",
            status = needleStatus,
            leading = { IconBadge(icon = Icons.Outlined.Build) },
        )
        RowDivider(inset = true)
        ModelRow(
            name = "Whistle",
            role = "Speech to text",
            status = whistleStatus,
            leading = { IconBadge(icon = Icons.Outlined.Mic) },
        )
    }
}

@Composable
private fun ModelRow(
    name: String,
    role: String,
    status: ModelStatus,
    leading: @Composable () -> Unit,
) {
    val colors = MaterialTheme.spideyColors
    ListRow(
        title = name,
        subtitle = role,
        leading = leading,
        trailing = {
            StatusPill(
                label = if (status == ModelStatus.Ready) "Ready" else "Not downloaded",
                color = if (status == ModelStatus.Ready) colors.success else colors.warning,
            )
        },
    )
}

@Preview(name = "On-device model", showBackground = true, widthDp = 360)
@Composable
private fun OnDeviceModelSectionPreview() {
    SpideyTheme {
        OnDeviceModelSection(needleStatus = ModelStatus.NotDownloaded, whistleStatus = ModelStatus.Ready)
    }
}
