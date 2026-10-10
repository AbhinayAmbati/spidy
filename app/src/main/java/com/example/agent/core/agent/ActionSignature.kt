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
    val REPEATABLE_TOOLS = setOf(
        ToolNames.SCROLL,
        ToolNames.SWIPE,
        ToolNames.WAIT,
        ToolNames.READ_SCREEN,
        ToolNames.PRESS_BACK,
        ToolNames.PRESS_HOME,
        ToolNames.GET_NOTIFICATIONS,
    )

    /**
     * Tools that should never need to run twice with identical arguments in one task.
     *
     * The observation-equality check below cannot catch these on its own, because volatile UI
     * - the keyboard sliding in, a caret blinking - makes the screen differ between two runs
     * that accomplished the same nothing. Real screenshots show "Opening com.android.chrome"
     * five times and "Typing Kubernetes" six times for a task that only needed each once.
     *
     * Deliberately excludes click and long_click: tapping the same "Next" button five times
     * through a paginated flow is legitimate, and refusing it would break real tasks.
     */
    val NEVER_REPEATED_TOOLS = setOf(
        ToolNames.OPEN_APP,
        ToolNames.CLOSE_APP,
        ToolNames.LAUNCH_URL,
        ToolNames.TYPE_TEXT,
        ToolNames.ASK_USER,
        ToolNames.FINISH,
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
