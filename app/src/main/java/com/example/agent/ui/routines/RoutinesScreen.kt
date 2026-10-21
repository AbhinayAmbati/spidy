package com.example.agent.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.Routine
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.LabelValueRow
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.TabTitle
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val routines by viewModel.routinesList.collectAsStateWithLifecycle()
    RoutinesContent(
        routines = routines,
        onRun = viewModel::run,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun RoutinesContent(
    routines: List<Routine>,
    onRun: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TabTitle(title = "Routines")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
        ) {
            Text(
                text = "A task that worked, saved so it can be repeated without the model. " +
                    "Replays are much faster. Each step is checked as it runs, and if a screen " +
                    "has changed the agent picks up from there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )

            Spacer(Modifier.height(SpideySpacing.sectionGap))

            if (routines.isEmpty()) {
                EmptyState(
                    title = "Nothing saved yet",
                    body = "Finish a task, then tap Save as routine on the task screen.",
                    icon = Icons.Filled.Replay,
                )
            } else {
                SectionHeader(title = "Saved", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(SpideySpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                    routines.forEach { routine ->
                        RoutineCard(
                            routine = routine,
                            onRun = { onRun(routine) },
                            onDelete = { onDelete(routine) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: Routine, onRun: () -> Unit, onDelete: () -> Unit) {
    SpideyCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = routine.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(SpideySpacing.xs))
        LabelValueRow(label = "Steps", value = routine.steps.size.toString())
        Spacer(Modifier.height(SpideySpacing.xxs))
        LabelValueRow(
            label = "Runs",
            value = if (routine.runCount == 0) "never" else routine.runCount.toString(),
        )
        Spacer(Modifier.height(SpideySpacing.md))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpideySpacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PrimaryButton(text = "Run", onClick = onRun, modifier = Modifier.weight(1f))
            SecondaryButton(text = "Delete", onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Routines", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun RoutinesPreview() {
    SpideyTheme {
        RoutinesContent(routines = PreviewData.routines, onRun = {}, onDelete = {})
    }
}

@Preview(name = "Routines - empty", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun RoutinesEmptyPreview() {
    SpideyTheme {
        RoutinesContent(routines = emptyList(), onRun = {}, onDelete = {})
    }
}
