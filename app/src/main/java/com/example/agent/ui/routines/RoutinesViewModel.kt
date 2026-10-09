package com.example.agent.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.data.repository.RoutineRepository
import com.example.agent.domain.model.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routines: RoutineRepository,
    private val runtime: AgentRuntime,
) : ViewModel() {

    val routinesList: StateFlow<List<Routine>> = routines.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Replaying needs no model, so it does not matter whether a provider is configured - which
     * is the point of routines: the work is already known, so nothing has to be thought about.
     */
    fun run(routine: Routine) {
        viewModelScope.launch {
            routines.markRun(routine.id, System.currentTimeMillis())
            runtime.startRoutine(routine)
        }
    }

    fun delete(routine: Routine) {
        viewModelScope.launch { routines.delete(routine.id) }
    }
}
