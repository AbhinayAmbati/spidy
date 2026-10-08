package com.example.agent.domain.model

import kotlinx.serialization.Serializable

/**
 * A single long-term memory fact. Today these are app shortcuts
 * ("whatsapp" -> "com.whatsapp") learned from tasks the user actually ran.
 * The user can view and delete every one of them.
 */
@Serializable
data class MemoryEntry(
    val key: String,
    val value: String,
    val createdAtMillis: Long = 0L,
)
