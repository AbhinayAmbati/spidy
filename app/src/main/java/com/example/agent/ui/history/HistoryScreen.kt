package com.example.agent.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.ChipRow
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.SearchField
import com.example.agent.ui.components.SectionLabel
import com.example.agent.ui.components.TabTitle
import com.example.agent.ui.components.TaskRow
import com.example.agent.ui.components.taskSubtitle
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val FILTER_ALL = "All"
private const val FILTER_COMPLETED = "Completed"
private const val FILTER_FAILED = "Failed"
private val FILTERS = listOf(FILTER_ALL, FILTER_COMPLETED, FILTER_FAILED)

@Composable
fun HistoryScreen(
    onOpenTask: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    HistoryContent(tasks = tasks, onOpenTask = onOpenTask)
}

@Composable
private fun HistoryContent(
    tasks: List<TaskRecord>,
    onOpenTask: (String) -> Unit,
) {
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var query by remember { mutableStateOf("") }
    val visible = tasks.filter { matchesFilter(it, filter) && it.prompt.contains(query.trim(), ignoreCase = true) }
    val grouped = visible.groupBy { dayLabel(it.createdAtMillis) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabTitle(title = "Tasks")

        if (tasks.isNotEmpty()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Search tasks",
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
            )
            ChipRow(
                labels = FILTERS,
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.padding(vertical = SpideySpacing.sm),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpideySpacing.lg),
        ) {
            if (visible.isEmpty()) {
                EmptyState(
                    title = if (tasks.isEmpty()) "No tasks yet" else "Nothing matches",
                    body = if (tasks.isEmpty()) {
                        "Tasks you run will be listed here, grouped by day."
                    } else {
                        "Try a different search or filter."
                    },
                )
            }

            grouped.forEach { (day, records) ->
                SectionLabel(text = day)
                records.forEach { record ->
                    TaskRow(
                        prompt = record.prompt,
                        detail = taskSubtitle(record),
                        state = record.state,
                        onClick = { onOpenTask(record.id) },
                    )
                }
            }
        }
    }
}

private fun matchesFilter(record: TaskRecord, filter: String): Boolean = when (filter) {
    FILTER_COMPLETED -> record.state == TaskState.COMPLETED
    FILTER_FAILED -> record.state == TaskState.FAILED
    else -> true
}

private fun dayLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.toString()
    }
}

@Preview(name = "Tasks", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HistoryPreview() {
    SpideyTheme {
        HistoryContent(tasks = PreviewData.records, onOpenTask = {})
    }
}

@Preview(name = "Tasks - empty", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HistoryEmptyPreview() {
    SpideyTheme {
        HistoryContent(tasks = emptyList(), onOpenTask = {})
    }
}
