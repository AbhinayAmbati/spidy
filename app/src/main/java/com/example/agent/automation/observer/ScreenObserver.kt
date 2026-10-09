package com.example.agent.automation.observer

import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.domain.model.ScreenSnapshot

/**
 * Turns the live screen into a compact, model-friendly text block.
 *
 * We never dump the raw accessibility tree: only labelled, interactive elements
 * are included, and only a bounded number of them, so the prompt stays small and
 * the user's private screen content is not over-shared.
 */
class ScreenObserver(private val automation: AndroidAutomationManager) {

    suspend fun observe(): Observation {
        val snapshot = automation.readScreen()
        return Observation(snapshot, render(snapshot))
    }

    private fun render(snapshot: ScreenSnapshot): String {
        if (snapshot.elements.isEmpty()) return "Screen is empty or not readable."

        val app = snapshot.appLabel ?: snapshot.packageName ?: "unknown app"
        val sb = StringBuilder()
        sb.append("Current app: ").append(app).append('\n')
        sb.append("Interactive elements:\n")
        snapshot.elements.forEach { el ->
            val flags = buildList {
                if (el.clickable) add("clickable")
                if (el.editable) add("editable")
                if (el.scrollable) add("scrollable")
                if (!el.enabled) add("disabled")
            }.joinToString(",")
            sb.append("  [").append(el.key).append("] ")
                .append("\"").append(el.label.take(80)).append("\"")
            // The widget type matters: an EditText is typeable, a TextView is not.
            el.className?.substringAfterLast('.')?.takeIf { it.isNotBlank() }?.let {
                sb.append(" <").append(it).append(">")
            }
            if (flags.isNotEmpty()) sb.append(" (").append(flags).append(")")
            el.id?.let { sb.append(" id=").append(it.substringAfterLast('/')) }
            sb.append('\n')
        }
        return sb.toString()
    }

    data class Observation(val snapshot: ScreenSnapshot, val rendered: String)
}
