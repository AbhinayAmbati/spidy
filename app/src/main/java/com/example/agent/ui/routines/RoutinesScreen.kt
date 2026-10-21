package com.example.agent.ui.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.Routine
import com.example.agent.ui.components.ConfirmDialog
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.RingAvatar
import com.example.agent.ui.components.TabTitle
import com.example.agent.ui.components.TextAction
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
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabTitle(title = "Routines")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpideySpacing.lg),
        ) {
            Text(
                text = "A task that worked, saved so it can be repeated without the model. " +
                    "Each step is checked as it runs, and if a screen has changed the agent " +
                    "picks up from there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
                modifier = Modifier.padding(
                    horizontal = SpideySpacing.screenGutter,
                    vertical = SpideySpacing.xs,
                ),
            )

            if (routines.isEmpty()) {
                EmptyState(
                    title = "No routines yet",
                    body = "Finish a task, then choose Save as routine to replay it later.",
                )
            }

            routines.forEach { routine ->
                RoutineRow(
                    routine = routine,
                    onRun = { onRun(routine) },
                    onDelete = { pendingDelete = routine },
                )
            }
        }
    }

    pendingDelete?.let { routine ->
        ConfirmDialog(
            title = "Delete routine?",
            message = "\"${routine.name}\" will be removed. Tasks you already ran are not affected.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                onDelete(routine)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun RoutineRow(routine: Routine, onRun: () -> Unit, onDelete: () -> Unit) {
    val steps = if (routine.steps.size == 1) "1 step" else "${routine.steps.size} steps"
    val runs = when (routine.runCount) {
        0 -> "never run"
        1 -> "ran once"
        else -> "ran ${routine.runCount} times"
    }
    ListRow(
        title = routine.name,
        subtitle = "$steps · $runs",
        leading = { RingAvatar(text = routine.name) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(text = "Run", onClick = onRun)
                RoutineMenu(onDelete = onDelete)
            }
        },
    )
}

@Composable
private fun RoutineMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "More",
            tint = MaterialTheme.spideyColors.textSecondary,
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.spideyColors.error) },
            onClick = {
                open = false
                onDelete()
            },
        )
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
