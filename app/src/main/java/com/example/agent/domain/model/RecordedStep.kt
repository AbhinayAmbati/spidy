package com.example.agent.domain.model

import kotlinx.serialization.Serializable

/** One action the agent took, as stored in task history. */
@Serializable
data class RecordedStep(
    val label: String,
    val status: String,
    val detail: String? = null,
)
