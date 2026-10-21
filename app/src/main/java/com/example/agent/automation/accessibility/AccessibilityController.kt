package com.example.agent.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.agent.core.control.AgentHalt
import com.example.agent.domain.model.Bounds
import com.example.agent.domain.model.ScreenSnapshot
import com.example.agent.domain.model.UIElement
import com.example.agent.domain.model.inVisualOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The ONLY component that speaks AccessibilityService. Every automation feature
 * (tools, observer) goes through this class so the raw service never leaks out.
 *
 * All calls marshal to the main thread, because node inspection and gesture
 * dispatch must happen there.
 */
class AccessibilityController {

    private val service: AccessibilityService?
        get() = AccessibilityBridge.serviceOrNull()

    fun isAvailable(): Boolean = service != null

    /**
     * The package of the app currently on screen, or null if unknown.
     * Deliberately cheap: no tree traversal, just the active window.
     */
    suspend fun currentPackage(): String? = withContext(Dispatchers.Main) {
        service?.rootInActiveWindow?.packageName?.toString()
    }

    // ---------------------------------------------------------------- observation

    /** Builds a compact, model-friendly snapshot of the current screen. */
    suspend fun snapshot(maxElements: Int = 120, maxDepth: Int = 24): ScreenSnapshot =
        withContext(Dispatchers.Main) {
            val svc = service ?: return@withContext ScreenSnapshot.EMPTY
            val root = svc.rootInActiveWindow ?: return@withContext ScreenSnapshot.EMPTY

            val collected = ArrayList<AccessibilityNodeInfo>()
            traverse(root, 0, maxDepth, maxElements, collected)

            val byKey = HashMap<String, AccessibilityNodeInfo>()
            val built = ArrayList<UIElement>(collected.size)
            collected.forEach { node ->
                // The one place bounds are read. Zero-size nodes are dropped here rather
                // than paying for bounds on every node in the tree just to filter them.
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (rect.width() <= 0 || rect.height() <= 0) return@forEach
                val element = UIElement(
                    ref = built.size,
                    id = node.viewIdResourceName,
                    text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    contentDescription = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    className = node.className?.toString(),
                    clickable = node.isClickable,
                    longClickable = node.isLongClickable,
                    editable = node.isEditable,
                    enabled = node.isEnabled,
                    scrollable = node.isScrollable,
                    bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                )
                byKey[element.key] = node
                built.add(element)
            }

            // Visual order, so the listing reads like the screen the user is looking at
            // instead of an arbitrary tree walk. Keys are content-derived, so this does not
            // disturb targeting.
            val elements = built.inVisualOrder()
            lastNodes = elements.mapNotNull { byKey[it.key] }
            lastNodesByKey = byKey

            ScreenSnapshot(
                packageName = svc.rootInActiveWindow?.packageName?.toString(),
                appLabel = null,
                windowTitle = null,
                elements = elements,
                capturedAtMillis = System.currentTimeMillis(),
            )
        }

    private fun traverse(
        node: AccessibilityNodeInfo?,
        depth: Int,
        maxDepth: Int,
        maxElements: Int,
        out: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null || depth > maxDepth || out.size >= maxElements) return
        if (isInteresting(node)) out.add(node)
        for (i in 0 until node.childCount) {
            if (out.size >= maxElements) break
            traverse(node.getChild(i), depth + 1, maxDepth, maxElements, out)
        }
    }

    /**
     * Cheap test only. This runs for *every* node in the tree, so it must not touch bounds:
     * the old version called getBoundsInScreen here and then again for each kept element,
     * paying for the whole tree twice over. Bounds are now read once, for kept elements only.
     */
    private fun isInteresting(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val interactive = node.isClickable || node.isEditable || node.isScrollable || node.isLongClickable
        return hasText || interactive
    }

    /** Nodes captured by the most recent [snapshot], indexed by [UIElement.ref]. */
    @Volatile
    private var lastNodes: List<AccessibilityNodeInfo> = emptyList()

    /** The same nodes, indexed by their stable [UIElement.key]. */
    @Volatile
    private var lastNodesByKey: Map<String, AccessibilityNodeInfo> = emptyMap()

    // ---------------------------------------------------------------- interaction

    suspend fun click(target: TargetMatcher): ActionResult = withContext(Dispatchers.Main) {
        if (AgentHalt.isStopped) return@withContext ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        // A node captured by an earlier snapshot can go stale once the window changes;
        // touching one throws IllegalStateException. That must not take down the task.
        runCatching {
            val node = resolve(target) ?: return@withContext ActionResult.NotFound
            val clickable = clickableSelfOrAncestor(node) ?: return@withContext ActionResult.NotFound
            val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (ok) ActionResult.Ok else tapCenter(clickable)
        }.getOrDefault(ActionResult.NotFound)
    }

    suspend fun longClick(target: TargetMatcher): ActionResult = withContext(Dispatchers.Main) {
        if (AgentHalt.isStopped) return@withContext ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        runCatching {
            val node = resolve(target) ?: return@withContext ActionResult.NotFound
            val clickable = clickableSelfOrAncestor(node) ?: return@withContext ActionResult.NotFound
            val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (ok) ActionResult.Ok else ActionResult.Unsupported
        }.getOrDefault(ActionResult.NotFound)
    }

    suspend fun setText(target: TargetMatcher, text: String): ActionResult = withContext(Dispatchers.Main) {
        if (AgentHalt.isStopped) return@withContext ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        val node = resolve(target) ?: focusedEditable() ?: return@withContext ActionResult.NotFound
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }

        // A node from an earlier snapshot can go stale once the window changes, and touching it
        // throws IllegalStateException. That must not take down the task, as it does not in click.
        try {
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return@withContext ActionResult.Ok
            }

            // SET_TEXT is refused outright by a field that does not hold focus - the search boxes
            // in several apps do exactly this, which is why a real run failed to type on its first
            // attempt and succeeded on the second. Focus it, then fill it.
            focusAndMaybeClick(node)
            delay(120)
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                ActionResult.Ok
            } else {
                ActionResult.Unsupported
            }
        } catch (stale: IllegalStateException) {
            ActionResult.NotFound
        }
    }

    /** Puts focus on a field, clicking it first when the platform needs that to focus it. */
    private fun focusAndMaybeClick(node: AccessibilityNodeInfo) {
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        if (!node.isFocused) {
            clickableSelfOrAncestor(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    suspend fun scroll(direction: String?): ActionResult = withContext(Dispatchers.Main) {
        if (AgentHalt.isStopped) return@withContext ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        val root = service?.rootInActiveWindow ?: return@withContext ActionResult.NotFound

        // The first scrollable in traversal order is often a small nested scroller, which
        // is why scrolling "backward" kept failing. Use the largest one on screen.
        val scrollable = largestScrollable(root) ?: return@withContext ActionResult.NotFound

        val forward = direction?.lowercase() !in BACKWARD_DIRECTIONS
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        if (scrollable.performAction(action)) return@withContext ActionResult.Ok

        // Some surfaces ignore the scroll action but accept the equivalent gesture.
        val rect = Rect().also { scrollable.getBoundsInScreen(it) }
        if (rect.width() <= 0 || rect.height() <= 0) {
            ActionResult.Unsupported
        } else {
            val cx = rect.exactCenterX().toInt()
            val near = rect.top + rect.height() * 0.3f
            val far = rect.top + rect.height() * 0.7f
            if (forward) {
                gesture(cx, far.toInt(), cx, near.toInt(), GESTURE_SCROLL_MS)
            } else {
                gesture(cx, near.toInt(), cx, far.toInt(), GESTURE_SCROLL_MS)
            }
        }
    }

    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): ActionResult {
        if (AgentHalt.isStopped) return ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        return gesture(fromX, fromY, toX, toY, durationMs)
    }

    suspend fun tap(x: Int, y: Int): ActionResult {
        if (AgentHalt.isStopped) return ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        return gesture(x, y, x, y, 60)
    }

    suspend fun pressBack(): ActionResult = withContext(Dispatchers.Main) {
        if (AgentHalt.isStopped) return@withContext ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        val svc = service ?: return@withContext ActionResult.NotFound
        if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) ActionResult.Ok
        else ActionResult.Unsupported
    }

    suspend fun pressHome(): ActionResult = withContext(Dispatchers.Main) {
        if (AgentHalt.isStopped) return@withContext ActionResult.Stopped
        AccessibilityBridge.suppressTouchesFor(UserTouchGate.ACTION_WINDOW_MS)
        val svc = service ?: return@withContext ActionResult.NotFound
        if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) ActionResult.Ok
        else ActionResult.Unsupported
    }

    /**
     * Waits until the screen stops changing.
     *
     * @return true if the screen changed at all while waiting. When it did not, the caller
     *   can skip reading the screen entirely: it is byte-for-byte what the model already has,
     *   so both the traversal and the listing would be wasted.
     *
     * This used to poll the view tree: two traversals of up to 80 nodes every 120 ms, for as
     * long as four seconds, on every single step - thousands of node visits on the main
     * thread per step, and up to 40 seconds of it across a ten-step task. It now watches the
     * platform's own window-change events, which the service already receives, so the wait
     * costs one volatile read per tick and nothing else.
     *
     * A change that happened before this call does not count, so the quiet window always
     * starts from the action we are waiting on.
     */
    suspend fun awaitSettle(
        quietMs: Long = SettleGate.DEFAULT_QUIET_MS,
        timeoutMs: Long = SettleGate.DEFAULT_TIMEOUT_MS,
    ): Boolean {
        if (service == null) return false
        val started = SystemClock.uptimeMillis()
        while (true) {
            val now = SystemClock.uptimeMillis()
            if (SettleGate.isSettled(now, AccessibilityBridge.lastScreenChangeMillis(), started, quietMs)) {
                // A change at or after we started waiting means the action did something.
                return SettleGate.changedDuring(AccessibilityBridge.lastScreenChangeMillis(), started)
            }
            if (SettleGate.isTimedOut(now, started, timeoutMs)) return true
            delay(SettleGate.TICK_MS)
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun gesture(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): ActionResult {
        if (AgentHalt.isStopped) return ActionResult.Stopped
        // The longest window wins, so a nested tap cannot shorten a running swipe.
        AccessibilityBridge.suppressTouchesFor(durationMs)
        val svc = service ?: return ActionResult.NotFound
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return ActionResult.Unsupported
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val path = Path().apply {
                    moveTo(fromX.toFloat(), fromY.toFloat())
                    lineTo(toX.toFloat(), toY.toFloat())
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(30))
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val dispatched = svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult.Ok)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult.Unsupported)
                    }
                }, null)
                if (!dispatched && cont.isActive) cont.resume(ActionResult.Unsupported)
            }
        }
    }

    private fun tapCenter(node: AccessibilityNodeInfo): ActionResult {
        val rect = Rect().also { node.getBoundsInScreen(it) }
        if (rect.width() <= 0 || rect.height() <= 0) return ActionResult.NotFound
        // Fall back to a touch at the node centre when ACTION_CLICK is unavailable.
        val svc = service ?: return ActionResult.NotFound
        val path = Path().apply { moveTo(rect.exactCenterX(), rect.exactCenterY()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return if (svc.dispatchGesture(gesture, null, null)) ActionResult.Ok else ActionResult.Unsupported
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            hops++
        }
        return node.takeIf { it.isEnabled }
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = service?.rootInActiveWindow ?: return null
        return findFirst(root) { it.isEditable && it.isFocused }
            ?: findFirst(root) { it.isEditable }
    }

    /** The biggest scrollable region on screen - the one a user would actually drag. */
    private fun largestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var count = 0
        while (stack.isNotEmpty() && count < 400) {
            val n = stack.removeLast()
            count++
            if (n.isScrollable) {
                val rect = Rect().also { n.getBoundsInScreen(it) }
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    bestArea = area
                    best = n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return best
    }

    private fun findFirst(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var count = 0
        while (stack.isNotEmpty() && count < 400) {
            val n = stack.removeLast()
            count++
            if (predicate(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return null
    }

    /** Resolves a [TargetMatcher] to a concrete node, preferring the last snapshot. */
    private fun resolve(target: TargetMatcher): AccessibilityNodeInfo? {
        // A key is stable across re-reads, so it is tried before the positional index.
        target.key?.let { key -> lastNodesByKey[key]?.let { return it } }
        target.ref?.let { ref -> lastNodes.getOrNull(ref)?.let { return it } }

        val root = service?.rootInActiveWindow ?: return null

        target.viewId?.let { id ->
            runCatching { root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull() }
                .getOrNull()?.let { return it }

            // The observation shows the short id ("url_bar") while the platform wants the
            // fully-qualified one ("com.android.chrome:id/url_bar"). The model echoes back
            // what it was shown, so accept either form.
            val short = id.substringAfterLast('/')
            if (short.isNotBlank()) {
                lastNodes.firstOrNull { it.viewIdResourceName?.substringAfterLast('/') == short }
                    ?.let { return it }
                findFirst(root) { it.viewIdResourceName?.substringAfterLast('/') == short }
                    ?.let { return it }
            }
        }

        val needles = listOfNotNull(target.text, target.contentDescription)
        if (needles.isNotEmpty()) {
            for (needle in needles) {
                root.findAccessibilityNodeInfosByText(needle)?.firstOrNull { node ->
                    val t = node.text?.toString().orEmpty()
                    val d = node.contentDescription?.toString().orEmpty()
                    t.contains(needle, ignoreCase = true) || d.contains(needle, ignoreCase = true)
                }?.let { return it }
            }
        }

        target.className?.let { cls ->
            return findFirst(root) { it.className?.toString()?.endsWith(cls) == true }
        }
        return null
    }

    /**
     * Screen size in pixels. Read straight from the display metrics - the swipe tool used to
     * build a whole snapshot just to find the widest and lowest element.
     */
    fun screenSize(): Pair<Int, Int> {
        val metrics = service?.resources?.displayMetrics
            ?: return DEFAULT_WIDTH to DEFAULT_HEIGHT
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * Whether [query] appears on screen. Uses the platform's own text search rather than
     * building a whole snapshot to answer a yes/no question.
     */
    suspend fun hasText(query: String): Boolean = withContext(Dispatchers.Main) {
        val root = service?.rootInActiveWindow ?: return@withContext false
        runCatching { root.findAccessibilityNodeInfosByText(query) }
            .getOrNull()
            ?.isNotEmpty() == true
    }

    /** Whether anything on the current screen can be scrolled. */
    suspend fun hasScrollable(): Boolean = withContext(Dispatchers.Main) {
        val root = service?.rootInActiveWindow ?: return@withContext false
        findFirst(root) { it.isScrollable } != null
    }

    private companion object {
        val BACKWARD_DIRECTIONS = setOf("back", "backward", "up", "left")
        const val GESTURE_SCROLL_MS = 260L
        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 1920
    }

    sealed interface ActionResult {
        data object Ok : ActionResult
        data object NotFound : ActionResult
        data object Unsupported : ActionResult

        /** The user pressed Stop. Nothing was performed. */
        data object Stopped : ActionResult
    }
}

/** Everything needed to locate a node on screen. */
data class TargetMatcher(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    /** Legacy positional index. Prefer [key]. */
    val ref: Int? = null,
    /** Stable [com.example.agent.domain.model.ElementKey] from the latest observation. */
    val key: String? = null,
    val className: String? = null,
) {
    val isEmpty: Boolean
        get() = text == null && contentDescription == null && viewId == null &&
            ref == null && key == null && className == null
}
