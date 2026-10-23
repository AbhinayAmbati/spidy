package com.example.agent.core.screen

import com.example.agent.domain.model.Bounds
import com.example.agent.domain.model.ScreenSnapshot
import com.example.agent.domain.model.UIElement
import com.example.agent.domain.model.inVisualOrder

/** The compact screen text for a small model, and the way back from its numbers to elements. */
data class SerializedScreen(
    val text: String,
    /** Model-facing id (1-based) to the stable [UIElement.key] the executor targets by. */
    val idToKey: Map<Int, String>,
    val tokenEstimate: Int,
    /** Elements left out because of the token budget (not because they were noise). */
    val dropped: Int,
) {
    fun keyOf(id: Int): String? = idToKey[id]
}

/**
 * Turns a [ScreenSnapshot] into a short numbered list a small model can pick from.
 *
 * Kept: clickable, long-clickable, editable, scrollable and labelled elements. Dropped: empty
 * containers, and labels that only repeat their clickable parent. Ids are 1..N in visual order
 * (top to bottom, then left to right), so the same screen always gives the same ids; they shift
 * only when elements appear or disappear, which is why the executor resolves them through
 * [SerializedScreen.keyOf] against the snapshot they came from.
 */
object ScreenSerializer {

    const val DEFAULT_TOKEN_BUDGET = 350
    private const val MAX_LABEL_CHARS = 24
    private const val CHARS_PER_TOKEN = 3.5
    private const val DUPLICATE_PX = 16
    private const val MORE_RESERVE = 5

    private val LAYOUT_CLASSES = setOf(
        "View", "ViewGroup", "FrameLayout", "LinearLayout", "RelativeLayout",
        "ConstraintLayout", "CoordinatorLayout", "ComposeView",
    )

    fun serialize(
        snapshot: ScreenSnapshot,
        tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
    ): SerializedScreen {
        val kept = merge(snapshot.elements.filter { !it.bounds.isEmpty && isWorthKeeping(it) })
        val header = header(snapshot, kept)

        // Fill the budget by importance, then put the survivors back in screen order.
        var used = estimateTokens(header)
        val chosen = ArrayList<Entry>()
        kept.mapIndexed { i, e -> Entry(e, i) }
            .sortedWith(compareBy({ priority(it.element) }, { it.order }))
            .forEach { entry ->
                val cost = estimateTokens(line(0, entry.element)) + 1
                if (used + cost <= tokenBudget - MORE_RESERVE) {
                    used += cost
                    chosen.add(entry)
                }
            }
        val dropped = kept.size - chosen.size
        val ordered = chosen.map { it.element }.inVisualOrder()

        val sb = StringBuilder(header)
        val ids = LinkedHashMap<Int, String>()
        ordered.forEachIndexed { i, e ->
            val id = i + 1
            ids[id] = e.key
            sb.append('\n').append(line(id, e))
        }
        if (dropped > 0) sb.append("\n+").append(dropped).append(" more")
        val text = sb.toString()
        return SerializedScreen(text, ids, estimateTokens(text), dropped)
    }

    fun estimateTokens(text: String): Int = Math.ceil(text.length / CHARS_PER_TOKEN).toInt()

    private class Entry(val element: UIElement, val order: Int)

    private fun header(snapshot: ScreenSnapshot, kept: List<UIElement>): String {
        val app = (snapshot.packageName ?: "unknown").substringAfterLast('.')
        val scroll = if (kept.any { it.scrollable }) "yes" else "no"
        return "app:$app scroll:$scroll"
    }

    private fun interactive(e: UIElement) = e.clickable || e.longClickable || e.editable || e.scrollable

    private fun labelOf(e: UIElement): String? =
        (e.text?.takeIf { it.isNotBlank() } ?: e.contentDescription?.takeIf { it.isNotBlank() })
            ?.replace('\n', ' ')?.trim()

    private fun idLeaf(e: UIElement): String? =
        e.id?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    private fun shortClass(e: UIElement): String = e.className?.substringAfterLast('.').orEmpty()

    private fun isWorthKeeping(e: UIElement): Boolean {
        if (labelOf(e) != null) return true
        if (!interactive(e)) return false
        // An unlabelled interactive layout with no id is an empty container, not a control.
        val bareContainer = shortClass(e) in LAYOUT_CLASSES && idLeaf(e) == null &&
            !e.editable && !e.scrollable
        return !bareContainer
    }

    /**
     * Folds a label-only element into the clickable parent that contains it (the parent takes
     * the label), and removes same-role, same-label elements at the same spot.
     */
    private fun merge(elements: List<UIElement>): List<UIElement> {
        val parents = elements.filter { it.clickable }
        val result = ArrayList<UIElement>()
        val relabelled = HashMap<UIElement, String>()

        for (e in elements) {
            if (!interactive(e) && labelOf(e) != null) {
                val parent = parents.firstOrNull { it !== e && contains(it.bounds, e.bounds) }
                if (parent != null) {
                    if (labelOf(parent) == null && !relabelled.containsKey(parent)) {
                        relabelled[parent] = labelOf(e)!!
                    }
                    continue
                }
            }
            result.add(e)
        }

        val deduped = ArrayList<UIElement>()
        for (e in result.map { p -> relabelled[p]?.let { p.copy(text = it) } ?: p }) {
            val label = labelOf(e)
            val twin = label != null && deduped.any {
                role(it) == role(e) && labelOf(it) == label &&
                    Math.abs(it.bounds.centerX - e.bounds.centerX) <= DUPLICATE_PX &&
                    Math.abs(it.bounds.centerY - e.bounds.centerY) <= DUPLICATE_PX
            }
            if (!twin) deduped.add(e)
        }
        return deduped
    }

    private fun contains(outer: Bounds, inner: Bounds): Boolean =
        outer.left <= inner.left && outer.top <= inner.top &&
            outer.right >= inner.right && outer.bottom >= inner.bottom

    /** Lower number = kept first when the budget runs out. */
    private fun priority(e: UIElement): Int = when {
        e.editable -> 0
        e.clickable && labelOf(e) != null -> 1
        e.scrollable -> 2
        interactive(e) -> 3
        else -> 4
    }

    fun role(e: UIElement): String {
        val c = shortClass(e)
        return when {
            e.editable || c.contains("EditText") -> "edit"
            c.contains("Switch") || c.contains("CheckBox") || c.contains("Toggle") -> "switch"
            c.contains("Tab") -> "tab"
            c.contains("Image") -> "image"
            c.contains("RecyclerView") || c.contains("ListView") || c.contains("ScrollView") ||
                c.contains("Pager") -> "list"
            c.contains("Button") -> "button"
            c.contains("TextView") && !interactive(e) -> "text"
            e.clickable -> "button"
            e.scrollable -> "list"
            else -> "other"
        }
    }

    private fun line(id: Int, e: UIElement): String {
        val flags = buildString {
            if (e.clickable) append('c')
            if (e.longClickable) append('l')
            if (e.editable) append('e')
            if (e.scrollable) append('s')
            if (!e.enabled) append('x')
        }
        val label = labelOf(e)?.take(MAX_LABEL_CHARS) ?: idLeaf(e)?.take(MAX_LABEL_CHARS)
        val sb = StringBuilder()
        sb.append(id).append(' ').append(role(e))
        if (label != null) sb.append(" \"").append(label.replace('"', '\'')).append('"')
        if (flags.isNotEmpty()) sb.append(' ').append(flags)
        return sb.toString()
    }
}
