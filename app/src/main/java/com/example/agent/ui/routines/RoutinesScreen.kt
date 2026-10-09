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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.Routine
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.LabelValueRow
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val routines by viewModel.routinesList.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SpideySpacing.lg),
    ) {
        Text(
            text = "Routines",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SpideySpacing.sm))
        Text(
            text = "A task that worked, saved so it can be repeated without the model. " +
                "Replays cost nothing and are much faster. Each step is checked as it runs, " +
                "and if a screen has changed the agent picks up with the model from there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        if (routines.isEmpty()) {
            Text(
                text = "Nothing saved yet. Finish a task, then tap Save as routine on the " +
                    "task screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        } else {
            SectionHeader(title = "Saved", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SpideySpacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.md)) {
                routines.forEach { routine ->
                    RoutineCard(
                        routine = routine,
                        onRun = { viewModel.run(routine) },
                        onDelete = { viewModel.delete(routine) },
                    )
                }
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))
        SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RoutineCard(routine: Routine, onRun: () -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = routine.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SpideySpacing.xs))
        LabelValueRow(label = "Steps", value = routine.steps.size.toString())
        Spacer(Modifier.height(SpideySpacing.xs))
        LabelValueRow(
            label = "Runs",
            value = if (routine.runCount == 0) "never" else routine.runCount.toString(),
        )
        Spacer(Modifier.height(SpideySpacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpideySpacing.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PrimaryButton(text = "Run", onClick = onRun, modifier = Modifier.weight(1f))
            SecondaryButton(text = "Delete", onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}
