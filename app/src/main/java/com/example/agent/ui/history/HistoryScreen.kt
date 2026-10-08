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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.TaskRecord
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.TaskCard
import com.example.agent.ui.theme.SpideySpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    onOpenTask: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    val grouped = tasks.groupBy { dayLabel(it.createdAtMillis) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = "Tasks",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SpideySpacing.xl))

        if (tasks.isEmpty()) {
            EmptyState(
                title = "Nothing here yet",
                body = "Completed and failed tasks will be listed here, grouped by day.",
            )
            return@Column
        }

        grouped.forEach { (day, records) ->
            SectionHeader(title = day, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SpideySpacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.md)) {
                records.forEach { record -> HistoryRow(record, onOpenTask) }
            }
            Spacer(Modifier.height(SpideySpacing.sectionGap))
        }
    }
}

@Composable
private fun HistoryRow(record: TaskRecord, onOpenTask: (String) -> Unit) {
    TaskCard(
        prompt = record.prompt,
        appName = record.appsUsed.firstOrNull(),
        statusLabel = statusLabel(record),
        onClick = { onOpenTask(record.id) },
    )
}

private fun statusLabel(record: TaskRecord): String = when (record.state) {
    com.example.agent.domain.model.TaskState.COMPLETED -> "Completed"
    com.example.agent.domain.model.TaskState.FAILED -> "Failed"
    com.example.agent.domain.model.TaskState.CANCELLED -> "Stopped"
    else -> record.state.name.lowercase().replaceFirstChar { it.uppercase() }
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
