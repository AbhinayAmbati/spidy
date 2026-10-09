package com.example.agent.domain.model

import kotlinx.serialization.Serializable

/** One action the agent took, as stored in task history. */
@Serializable
data class RecordedStep(
    val label: String,
    val status: String,
    val detail: String? = null,
    // Flat rather than nested so the stored JSON stays simple and backwards compatible.
    val modelMillis: Long = 0,
    val settleMillis: Long = 0,
    val observeMillis: Long = 0,
    val actionMillis: Long = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val accountedMillis: Long get() = modelMillis + settleMillis + observeMillis + actionMillis
}
