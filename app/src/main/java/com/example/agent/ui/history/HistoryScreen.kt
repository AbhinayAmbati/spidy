package com.example.agent.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.TabTitle
import com.example.agent.ui.components.TaskCard
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
    val visible = tasks.filter { matchesFilter(it, filter) }
    val grouped = visible.groupBy { dayLabel(it.createdAtMillis) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabTitle(title = "Tasks")

        if (tasks.isNotEmpty()) {
            ChipRow(labels = FILTERS, selected = filter, onSelect = { filter = it })
            Spacer(Modifier.height(SpideySpacing.md))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
        ) {
            if (visible.isEmpty()) {
                EmptyState(
                    title = if (tasks.isEmpty()) "Nothing here yet" else "No $filter tasks",
                    body = "Completed and failed tasks will be listed here, grouped by day.",
                )
            }

            grouped.forEach { (day, records) ->
                SectionHeader(title = day, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(SpideySpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                    records.forEach { record ->
                        TaskCard(
                            prompt = record.prompt,
                            appName = record.appsUsed.firstOrNull(),
                            state = record.state,
                            onClick = { onOpenTask(record.id) },
                        )
                    }
                }
                Spacer(Modifier.height(SpideySpacing.sectionGap))
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

@Preview(name = "History", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HistoryPreview() {
    SpideyTheme {
        HistoryContent(tasks = PreviewData.records, onOpenTask = {})
    }
}

@Preview(name = "History - empty", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HistoryEmptyPreview() {
    SpideyTheme {
        HistoryContent(tasks = emptyList(), onOpenTask = {})
    }
}
