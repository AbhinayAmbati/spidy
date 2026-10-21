package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One task in a list, in the style of a chat history entry: a round status glyph, the request,
 * a grey line with the app and time, and a chevron.
 */
@Composable
fun TaskRow(
    prompt: String,
    detail: String,
    state: TaskState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        title = prompt,
        subtitle = detail,
        modifier = modifier,
        onClick = onClick,
        leading = { StateGlyph(state) },
        trailing = { Chevron() },
    )
}

/** A round glyph that shows how a task ended without needing words. */
@Composable
private fun StateGlyph(state: TaskState) {
    val color = taskStateColor(state)
    val icon: ImageVector = when (state) {
        TaskState.COMPLETED -> Icons.Filled.Check
        TaskState.FAILED -> Icons.Filled.Close
        else -> Icons.Filled.MoreHoriz
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = taskStateLabel(state),
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The grey line under a task: the app it used, how many steps it took and when it ran. */
fun taskSubtitle(record: TaskRecord): String {
    val time = Instant.ofEpochMilli(record.createdAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(TimeFormat)
    val app = record.appsUsed.firstOrNull()?.substringAfterLast('.')
    val steps = if (record.stepCount == 1) "1 step" else "${record.stepCount} steps"
    return listOfNotNull(app, steps, time).joinToString(" · ")
}
