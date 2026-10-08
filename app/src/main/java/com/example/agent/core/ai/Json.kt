package com.example.agent.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Shared, lenient JSON codec. We ignore unknown keys so provider payloads can evolve. */
internal val AgentJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

/** Parses [json] into an object, falling back to an empty object if it is unusable. */
internal fun parseObjectOrEmpty(json: String?): JsonObject =
    runCatching { AgentJson.parseToJsonElement(json ?: "{}") as? JsonObject }.getOrNull()
        ?: JsonObject(emptyMap())
