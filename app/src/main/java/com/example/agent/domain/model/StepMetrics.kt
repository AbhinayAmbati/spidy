package com.example.agent.domain.model

/**
 * Where one step's time actually went.
 *
 * Every number here was previously a guess. Without them an optimisation is unverifiable -
 * which is exactly how three regressions in a row slipped through.
 */
data class StepMetrics(
    val modelMillis: Long = 0,
    val settleMillis: Long = 0,
    val observeMillis: Long = 0,
    val actionMillis: Long = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val accountedMillis: Long get() = modelMillis + settleMillis + observeMillis + actionMillis
}
