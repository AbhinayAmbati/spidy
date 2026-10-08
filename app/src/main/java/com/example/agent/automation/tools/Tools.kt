package com.example.agent.automation.tools

import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.automation.accessibility.AccessibilityController.ActionResult
import com.example.agent.automation.accessibility.TargetMatcher
import com.example.agent.automation.observer.ScreenObserver
import com.example.agent.core.ai.ValidationResult
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.example.agent.domain.model.ToolResult
import kotlinx.coroutines.delay

private fun ActionTarget?.toMatcher(): TargetMatcher = TargetMatcher(
    text = this?.text,
    contentDescription = this?.contentDescription,
    viewId = this?.viewId,
    ref = this?.ref,
    className = this?.className,
)

// --------------------------------------------------------------------------- apps

class OpenAppTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.OPEN_APP
    override val description = "Open an installed app by package name or app name."
    override val parametersSchema = ToolSchemas.OPEN_APP

    override fun validate(action: AgentAction): ValidationResult =
        if (action.packageName.isNullOrBlank()) ValidationResult.Invalid("packageName is required")
        else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult {
        val query = action.packageName!!.trim()
        val pkg = automation.resolvePackage(query)
            ?: return ToolResult.Failure("No installed app matches \"$query\".")
        return when (val outcome = automation.openApp(pkg)) {
            is AndroidAutomationManager.LaunchOutcome.Launched -> {
                automation.readScreen() // warm up the observer
                ToolResult.Success("Opened ${automation.appLabel(pkg) ?: pkg} ($pkg).", mapOf("package" to pkg))
            }
            AndroidAutomationManager.LaunchOutcome.NotInstalled ->
                ToolResult.Failure("App $pkg is not installed.")
            is AndroidAutomationManager.LaunchOutcome.Failed ->
                ToolResult.Failure("Could not open $pkg: ${outcome.reason ?: "unknown error"}")
        }
    }
}

class CloseAppTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.CLOSE_APP
    override val description = "Leave the current app. Android does not allow force-stopping other apps."
    override val parametersSchema = ToolSchemas.CLOSE_APP

    override suspend fun execute(action: AgentAction): ToolResult {
        val msg = automation.closeApp(action.packageName.orEmpty())
        return ToolResult.Success(msg)
    }
}

// --------------------------------------------------------------------------- observation

class ReadScreenTool(
    private val automation: AndroidAutomationManager,
    private val observer: ScreenObserver,
) : BaseTool() {
    override val name = ToolNames.READ_SCREEN
    override val description = "Read the current screen and list its interactive elements."
    override val parametersSchema = ToolSchemas.READ_SCREEN

    override suspend fun execute(action: AgentAction): ToolResult {
        val observation = observer.observe()
        val app = automation.appLabel(observation.snapshot.packageName)
        return ToolResult.Success(observation.rendered, mapOf("app" to (app ?: observation.snapshot.packageName)))
    }
}

class FindTextTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.FIND_TEXT
    override val description = "Check whether given text is present on the current screen."
    override val parametersSchema = ToolSchemas.FIND_TEXT

    override fun validate(action: AgentAction): ValidationResult =
        if (action.text.isNullOrBlank()) ValidationResult.Invalid("text is required") else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult {
        val found = automation.findText(action.text!!)
        return ToolResult.Success(
            if (found) "Found \"${action.text}\" on screen." else "\"${action.text}\" is not on screen.",
            mapOf("found" to found),
        )
    }
}

// --------------------------------------------------------------------------- interaction

class ClickTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.CLICK
    override val description = "Tap an on-screen element."
    override val parametersSchema = ToolSchemas.CLICK

    override fun validate(action: AgentAction): ValidationResult =
        if (action.target.toMatcher().isEmpty) ValidationResult.Invalid("A target is required to click")
        else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult {
        val matcher = action.target.toMatcher()
        val result = automation.click(matcher)
        return when (result) {
            ActionResult.Ok -> ToolResult.Success("Tapped ${matcher.describe()}.")
            ActionResult.NotFound -> ToolResult.Failure("Could not find ${matcher.describe()} on screen.")
            ActionResult.Unsupported -> ToolResult.Failure("That element could not be tapped.")
        }
    }
}

class LongClickTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.LONG_CLICK
    override val description = "Long-press an on-screen element."
    override val parametersSchema = ToolSchemas.LONG_CLICK

    override fun validate(action: AgentAction): ValidationResult =
        if (action.target.toMatcher().isEmpty) ValidationResult.Invalid("A target is required")
        else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult {
        val matcher = action.target.toMatcher()
        return when (automation.longClick(matcher)) {
            ActionResult.Ok -> ToolResult.Success("Long-pressed ${matcher.describe()}.")
            ActionResult.NotFound -> ToolResult.Failure("Could not find ${matcher.describe()}.")
            ActionResult.Unsupported -> ToolResult.Failure("Long-press is not supported there.")
        }
    }
}

class TypeTextTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.TYPE_TEXT
    override val description = "Type text into the focused field, or into a targeted field."
    override val parametersSchema = ToolSchemas.TYPE_TEXT

    override fun validate(action: AgentAction): ValidationResult =
        if (action.text.isNullOrEmpty()) ValidationResult.Invalid("text is required") else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult {
        val matcher = action.target.toMatcher()
        return when (automation.typeText(matcher, action.text!!)) {
            ActionResult.Ok -> ToolResult.Success("Typed the text.")
            ActionResult.NotFound -> ToolResult.Failure("No editable field was found.")
            ActionResult.Unsupported -> ToolResult.Failure("The field could not be filled.")
        }
    }
}

class ScrollTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.SCROLL
    override val description = "Scroll the current screen forward or backward."
    override val parametersSchema = ToolSchemas.SCROLL

    override suspend fun execute(action: AgentAction): ToolResult =
        when (automation.scroll(action.direction)) {
            ActionResult.Ok -> ToolResult.Success("Scrolled ${action.direction ?: "forward"}.")
            ActionResult.NotFound -> ToolResult.Failure("Nothing scrollable was found.")
            ActionResult.Unsupported -> ToolResult.Failure("This screen cannot be scrolled.")
        }
}

class SwipeTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.SWIPE
    override val description = "Swipe in a direction (up, down, left, right)."
    override val parametersSchema = ToolSchemas.SWIPE

    override suspend fun execute(action: AgentAction): ToolResult {
        val snapshot = automation.readScreen()
        val w = snapshot.elements.maxOfOrNull { it.bounds.right } ?: 1080
        val h = snapshot.elements.maxOfOrNull { it.bounds.bottom } ?: 1920
        val cx = w / 2
        val cy = h / 2
        val d = action.durationMs ?: 300L
        val (fx, fy, tx, ty) = when (action.direction?.lowercase()) {
            "down" -> listOf(cx, cy - h / 4, cx, cy + h / 4)
            "left" -> listOf(w - w / 5, cy, w / 5, cy)
            "right" -> listOf(w / 5, cy, w - w / 5, cy)
            else -> listOf(cx, cy + h / 4, cx, cy - h / 4) // up
        }
        return when (automation.swipe(fx, fy, tx, ty, d)) {
            ActionResult.Ok -> ToolResult.Success("Swiped ${action.direction ?: "up"}.")
            ActionResult.NotFound -> ToolResult.Failure("Could not swipe: screen size unknown.")
            ActionResult.Unsupported -> ToolResult.Failure("Swipe gesture was rejected.")
        }
    }
}

class PressBackTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.PRESS_BACK
    override val description = "Go back one screen."
    override val parametersSchema = ToolSchemas.PRESS_BACK

    override suspend fun execute(action: AgentAction): ToolResult =
        when (automation.pressBack()) {
            ActionResult.Ok -> ToolResult.Success("Went back.")
            else -> ToolResult.Failure("Back is not available here.")
        }
}

class PressHomeTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.PRESS_HOME
    override val description = "Return to the home screen."
    override val parametersSchema = ToolSchemas.PRESS_HOME

    override suspend fun execute(action: AgentAction): ToolResult =
        when (automation.pressHome()) {
            ActionResult.Ok -> ToolResult.Success("Returned home.")
            else -> ToolResult.Failure("Home is not available here.")
        }
}

class WaitTool : BaseTool() {
    override val name = ToolNames.WAIT
    override val description = "Wait for the screen to update. Maximum 5 seconds."
    override val parametersSchema = ToolSchemas.WAIT

    override suspend fun execute(action: AgentAction): ToolResult {
        val ms = (action.durationMs ?: 1000L).coerceIn(100L, 5_000L)
        delay(ms)
        return ToolResult.Success("Waited ${ms}ms.")
    }
}

class LaunchUrlTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.LAUNCH_URL
    override val description = "Open a URL in the default browser."
    override val parametersSchema = ToolSchemas.LAUNCH_URL

    override fun validate(action: AgentAction): ValidationResult =
        if (action.url.isNullOrBlank()) ValidationResult.Invalid("url is required") else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult =
        when (val outcome = automation.launchUrl(action.url!!)) {
            AndroidAutomationManager.LaunchOutcome.Launched -> ToolResult.Success("Opened ${action.url}.")
            AndroidAutomationManager.LaunchOutcome.NotInstalled -> ToolResult.Failure("No browser is available.")
            is AndroidAutomationManager.LaunchOutcome.Failed ->
                ToolResult.Failure("Could not open the URL: ${outcome.reason ?: "unknown error"}")
        }
}

class GetNotificationsTool(private val automation: AndroidAutomationManager) : BaseTool() {
    override val name = ToolNames.GET_NOTIFICATIONS
    override val description = "List recent notifications (requires notification access)."
    override val parametersSchema = ToolSchemas.GET_NOTIFICATIONS

    override suspend fun execute(action: AgentAction): ToolResult {
        if (!automation.notificationsAvailable()) {
            return ToolResult.Failure(
                "Notification access is not granted. The user can enable it in system settings.",
            )
        }
        val items = automation.recentNotifications().take(10)
        if (items.isEmpty()) return ToolResult.Success("No recent notifications.")
        val rendered = items.joinToString("\n") {
            val app = automation.appLabel(it.packageName) ?: it.packageName
            "- [$app] ${it.title ?: ""} ${it.text ?: ""}".trim()
        }
        return ToolResult.Success(rendered)
    }
}

// --------------------------------------------------------------------------- control

class AskUserTool : BaseTool() {
    override val name = ToolNames.ASK_USER
    override val description = "Pause and ask the user a question when a decision is required."
    override val parametersSchema = ToolSchemas.ASK_USER

    override fun validate(action: AgentAction): ValidationResult =
        if (action.text.isNullOrBlank()) ValidationResult.Invalid("text is required") else ValidationResult.Valid

    override suspend fun execute(action: AgentAction): ToolResult =
        ToolResult.NeedsConfirmation(prompt = action.text!!, action = action)
}

class FinishTool : BaseTool() {
    override val name = ToolNames.FINISH
    override val description = "Declare the task complete, with a short summary."
    override val parametersSchema = ToolSchemas.FINISH

    override suspend fun execute(action: AgentAction): ToolResult =
        ToolResult.Success(action.reason?.takeIf { it.isNotBlank() } ?: "Task complete.")
}

private fun TargetMatcher.describe(): String = when {
    text != null -> "\"$text\""
    contentDescription != null -> "the \"$contentDescription\" control"
    ref != null -> "element #$ref"
    viewId != null -> "element $viewId"
    className != null -> "the $className element"
    else -> "the element"
}
