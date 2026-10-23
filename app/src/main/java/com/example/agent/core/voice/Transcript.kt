package com.example.agent.core.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pulls the spoken text out of the engine's JSON answer. The schema is not documented, so this is lenient. */
object WhistleResult {

    private val textKeys = listOf("text", "transcript", "transcription", "response", "result")

    /** The text, or null when the answer is not JSON or holds none. A bare string is taken as the text. */
    fun parse(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val element = try {
            Json.parseToJsonElement(trimmed)
        } catch (_: Exception) {
            return trimmed.takeUnless { it.startsWith("{") || it.startsWith("[") }
        }
        return find(element)?.takeIf { it.isNotBlank() }
    }

    private fun find(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> if (element.isString) element.content else null
        is JsonObject -> {
            textKeys.firstNotNullOfOrNull { key -> element[key]?.let(::find) }
                ?: element["segments"]?.let(::find)
        }
        is JsonArray -> element.mapNotNull(::find).joinToString(" ").ifBlank { null }
        else -> null
    }
}

/** Tidies a transcript and drops what Whistle says when nothing was said. */
object TranscriptCleaner {

    private val noise = setOf(
        "", "you", "thank you", "thanks", "thanks for watching", "bye", "okay", "ok",
        "blank audio", "silence", "music", "applause", "noise",
    )

    fun clean(text: String?): String? {
        if (text == null) return null
        var out = text.replace(Regex("\\s+"), " ").trim()
        // Whole-string annotations such as [BLANK_AUDIO] or (music).
        if (Regex("^[\\[(*].*[\\])*]$").matches(out)) return null
        out = out.trim()
        val probe = out.lowercase().replace(Regex("[^a-z ]"), "").replace('_', ' ').trim()
        if (probe in noise) return null
        return out
    }
}
