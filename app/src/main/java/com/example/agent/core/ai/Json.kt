package com.example.agent.core.ai

import kotlinx.serialization.json.Json

/** Shared, lenient JSON codec. We ignore unknown keys so provider payloads can evolve. */
internal val AgentJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}
