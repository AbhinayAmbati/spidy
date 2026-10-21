package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.spideyColors

/** A small tinted label for an outcome or status: "Completed", "Ready", "Not downloaded". */
@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The status colour for a task: green when done, red when failed, blue otherwise. */
@Composable
fun taskStateColor(state: TaskState): Color = when (state) {
    TaskState.COMPLETED -> MaterialTheme.spideyColors.success
    TaskState.FAILED -> MaterialTheme.spideyColors.error
    TaskState.CANCELLED -> MaterialTheme.spideyColors.textSecondary
    TaskState.WAITING_FOR_CONFIRMATION -> MaterialTheme.spideyColors.warning
    else -> MaterialTheme.spideyColors.blue
}

/** The user-facing name of a task state. */
fun taskStateLabel(state: TaskState): String = when (state) {
    TaskState.COMPLETED -> "Completed"
    TaskState.FAILED -> "Failed"
    TaskState.CANCELLED -> "Stopped"
    else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
}
