package com.example.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/** How far along one logged action is. */
enum class LogState { Pending, Running, Done, Failed }

/** One line of the action log, independent of whether it came from a live task or history. */
data class LogEntry(
    val label: String,
    val state: LogState,
    val detail: String? = null,
)

/**
 * The agent's actions as a collapsible timeline, the way Claude folds away its tool use.
 * The header summarises progress; tapping it rotates the chevron and expands or folds the list.
 */
@Composable
fun ActionLog(
    entries: List<LogEntry>,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val motion = LocalMotionEnabled.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )
    val done = entries.count { it.state == LogState.Done }
    val colors = MaterialTheme.spideyColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.muted),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressClickable(onClick = { expanded = !expanded }, pressedScale = 0.99f)
                .padding(horizontal = SpideySpacing.md, vertical = SpideySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$done of ${entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(SpideySpacing.xs))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide actions" else "Show actions",
                tint = colors.textSecondary,
                modifier = Modifier.rotate(if (motion) rotation else if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(start = SpideySpacing.md, end = SpideySpacing.md, bottom = SpideySpacing.sm)) {
                entries.forEachIndexed { index, entry ->
                    LogRow(entry = entry, isLast = index == entries.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, isLast: Boolean) {
    val colors = MaterialTheme.spideyColors
    val nodeColor by animateColorAsState(
        targetValue = when (entry.state) {
            LogState.Done -> colors.success
            LogState.Failed -> colors.error
            LogState.Running -> colors.blue
            LogState.Pending -> colors.border
        },
        label = "node",
    )
    Row {
        // The node and the line that joins it to the next one.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(nodeColor),
                contentAlignment = Alignment.Center,
            ) {
                when (entry.state) {
                    LogState.Done -> Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(12.dp))
                    LogState.Failed -> Icon(Icons.Filled.Close, contentDescription = "Failed", tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(12.dp))
                    LogState.Running -> Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
                    LogState.Pending -> Unit
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(colors.border),
                )
            }
        }
        Spacer(Modifier.width(SpideySpacing.sm))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.state == LogState.Pending) {
                    colors.textSecondary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
            if (entry.state == LogState.Failed && !entry.detail.isNullOrBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
