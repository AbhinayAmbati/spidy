package com.example.agent.domain.model

/**
 * A compact, model-friendly view of a single on-screen element.
 *
 * We deliberately do NOT ship raw AccessibilityNodeInfo trees to the LLM: the tree
 * is huge, full of noise, and leaks more of the user's screen than is needed. This
 * type is the *only* thing the model ever sees about the screen.
 */
data class UIElement(
    val ref: Int,                       // stable per-snapshot index the model can refer to
    val id: String?,                    // view id resource name (may be null)
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val bounds: Bounds,
) {
    /** Human label used when building the model prompt and for logging. */
    val label: String
        get() = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: className?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            ?: "element"
}

/** Screen-space rectangle in pixels. Kept dependency-free so it is unit-testable. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/** A structured snapshot of the screen at one point in time. */
data class ScreenSnapshot(
    val packageName: String?,
    val appLabel: String?,
    val windowTitle: String?,
    val elements: List<UIElement>,
    val capturedAtMillis: Long,
) {
    companion object {
        val EMPTY = ScreenSnapshot(null, null, null, emptyList(), 0L)
    }
}
