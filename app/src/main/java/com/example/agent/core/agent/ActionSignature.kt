package com.example.agent.core.agent

import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames

/**
 * Identifies "the same action", for loop detection.
 *
 * Two calls with identical arguments are the same action. Repeating one that changed
 * nothing is the most common way the agent fails — opening an app it is already in, or
 * retyping text that is already on screen — so the runtime uses this to refuse a repeat
 * and, if the model keeps insisting, to stop rather than spin.
 *
 * Tools whose whole purpose is repetition return null, so they are never loop-guarded.
 */
object ActionSignature {

    /** Tools that are expected to be called repeatedly, in the same form, on purpose. */
    private val REPEATABLE_TOOLS = setOf(
        ToolNames.SCROLL,
        ToolNames.SWIPE,
        ToolNames.WAIT,
        ToolNames.READ_SCREEN,
        ToolNames.PRESS_BACK,
        ToolNames.PRESS_HOME,
        ToolNames.GET_NOTIFICATIONS,
    )

    /** @return a stable key for the action, or null if it is legitimately repeatable. */
    fun of(action: AgentAction): String? {
        if (action.action in REPEATABLE_TOOLS) return null

        val target = action.target?.let {
            listOfNotNull(
                it.text, it.contentDescription, it.viewId, it.key, it.ref?.toString(), it.className,
            ).joinToString("~")
        }

        return listOf(
            action.action,
            action.packageName,
            action.text,
            action.url,
            action.direction,
            target,
        ).joinToString("|")
    }
}
