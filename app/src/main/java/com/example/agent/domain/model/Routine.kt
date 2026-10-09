package com.example.agent.domain.model

import kotlinx.serialization.Serializable

/**
 * One recorded action of a routine.
 *
 * The action is stored as the JSON the model produced, so a replay is byte-for-byte the
 * action that worked rather than a reconstruction of it.
 */
@Serializable
data class RoutineStep(
    val actionJson: String,
    /** How the step reads in the UI. */
    val label: String,
)

/**
 * A task that worked, recorded so it can be replayed with no model involved.
 *
 * Replay costs nothing and is far more reliable than re-deriving the same steps — but a
 * routine is only valid while the screens it touches look the same, so every step is
 * verified as it runs and the agent falls back to the model the moment one does not match.
 */
@Serializable
data class Routine(
    val id: String,
    val name: String,
    val prompt: String,
    val createdAtMillis: Long,
    val steps: List<RoutineStep>,
    val runCount: Int = 0,
    val lastRunAtMillis: Long? = null,
)
