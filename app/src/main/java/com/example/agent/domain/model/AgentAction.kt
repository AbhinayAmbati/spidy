package com.example.agent.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The structured action the model is allowed to return.
 *
 * This is the *entire* surface the LLM can influence on the device. It is never
 * executed directly — it must pass through [com.example.agent.core.agent.ActionValidator]
 * and then a registered [com.example.agent.core.ai.AgentTool]. The model can never
 * emit Kotlin, shell, or any other executable payload.
 */
@Serializable
data class AgentAction(
    val action: String,
    val target: ActionTarget? = null,
    val text: String? = null,
    val packageName: String? = null,
    @SerialName("url") val url: String? = null,
    val direction: String? = null,
    val amount: Int? = null,
    val durationMs: Long? = null,
    val reason: String? = null,
)

@Serializable
data class ActionTarget(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    /** Legacy positional index. Prefer [key]. */
    val ref: Int? = null,
    /** The [ElementKey] shown in the latest observation. */
    val key: String? = null,
    val className: String? = null,
)

/** Names of the built-in tools. Kept as constants so typos fail loudly at one place. */
object ToolNames {
    const val OPEN_APP = "open_app"
    const val CLOSE_APP = "close_app"
    const val READ_SCREEN = "read_screen"
    const val FIND_TEXT = "find_text"
    const val CLICK = "click"
    const val LONG_CLICK = "long_click"
    const val TYPE_TEXT = "type_text"
    const val SCROLL = "scroll"
    const val SWIPE = "swipe"
    const val PRESS_BACK = "press_back"
    const val PRESS_HOME = "press_home"
    const val WAIT = "wait"
    const val LAUNCH_URL = "launch_url"
    const val GET_NOTIFICATIONS = "get_notifications"
    const val ASK_USER = "ask_user"
    const val FINISH = "finish"
}
