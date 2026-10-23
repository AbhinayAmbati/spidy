package com.example.agent

import com.example.agent.core.screen.ScreenSerializer
import com.example.agent.domain.model.Bounds
import com.example.agent.domain.model.ScreenSnapshot
import com.example.agent.domain.model.UIElement
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenSerializerTest {

    private fun el(
        top: Int,
        text: String? = null,
        cls: String = "android.widget.TextView",
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
        enabled: Boolean = true,
        id: String? = null,
        left: Int = 0,
        width: Int = 200,
        height: Int = 40,
    ) = UIElement(
        ref = 0, id = id, text = text, contentDescription = null, className = cls,
        clickable = clickable, longClickable = false, editable = editable, enabled = enabled,
        scrollable = scrollable, bounds = Bounds(left, top, left + width, top + height),
    )

    private fun snap(vararg e: UIElement, pkg: String = "com.instagram.android") =
        ScreenSnapshot(pkg, null, null, e.toList(), 0L)

    @Test
    fun `keeps interactive and labelled elements and drops empty containers`() {
        val out = ScreenSerializer.serialize(
            snap(
                el(0, "Search", cls = "android.widget.Button", clickable = true),
                el(100, null, cls = "android.widget.FrameLayout"),
                el(200, null, cls = "android.widget.LinearLayout", clickable = true),
                el(300, "Hello", cls = "android.widget.TextView"),
            ),
        )
        assertThat(out.text).contains("button \"Search\" c")
        assertThat(out.text).contains("text \"Hello\"")
        assertThat(out.idToKey).hasSize(2)
    }

    @Test
    fun `ids are one based in visual order and map to keys`() {
        val lower = el(500, "Send", cls = "android.widget.Button", clickable = true)
        val upper = el(10, "Back", cls = "android.widget.ImageButton", clickable = true)
        val out = ScreenSerializer.serialize(snap(lower, upper))
        val lines = out.text.lines()
        assertThat(lines[1]).startsWith("1 ")
        assertThat(lines[1]).contains("Back")
        assertThat(lines[2]).startsWith("2 ")
        assertThat(out.keyOf(1)).isEqualTo(upper.key)
        assertThat(out.keyOf(2)).isEqualTo(lower.key)
        assertThat(out.keyOf(3)).isNull()
    }

    @Test
    fun `same screen serializes to identical text`() {
        val s = snap(
            el(0, "A", clickable = true), el(100, "B", clickable = true), el(200, "C"),
        )
        assertThat(ScreenSerializer.serialize(s).text).isEqualTo(ScreenSerializer.serialize(s).text)
    }

    @Test
    fun `label inside clickable parent is merged into the parent`() {
        val parent = el(
            0, null, cls = "android.widget.LinearLayout", clickable = true,
            id = "app:id/row", width = 400, height = 100,
        )
        val child = el(10, "Wi-Fi", width = 100, height = 20, left = 10)
        val out = ScreenSerializer.serialize(snap(parent, child))
        assertThat(out.idToKey).hasSize(1)
        assertThat(out.text).contains("\"Wi-Fi\"")
    }

    @Test
    fun `duplicates at the same spot are merged`() {
        val a = el(0, "Like", cls = "android.widget.Button", clickable = true)
        val b = el(4, "Like", cls = "android.widget.Button", clickable = true)
        assertThat(ScreenSerializer.serialize(snap(a, b)).idToKey).hasSize(1)
    }

    @Test
    fun `flags and roles are reported`() {
        val out = ScreenSerializer.serialize(
            snap(
                el(0, null, cls = "android.widget.EditText", editable = true, clickable = true, id = "a:id/query"),
                el(100, null, cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true, height = 500),
                el(700, "Mute", cls = "android.widget.Switch", clickable = true, enabled = false),
            ),
        )
        assertThat(out.text).contains("scroll:yes")
        assertThat(out.text).contains("edit \"query\" ce")
        assertThat(out.text).contains("list s")
        assertThat(out.text).contains("switch \"Mute\" cx")
    }

    @Test
    fun `long labels are truncated`() {
        val out = ScreenSerializer.serialize(snap(el(0, "x".repeat(200), clickable = true)))
        assertThat(out.text).doesNotContain("x".repeat(30))
    }

    @Test
    fun `big screens stay within the token budget and keep controls over plain text`() {
        val many = (0 until 200).map { i ->
            if (i % 2 == 0) {
                el(i * 50, "Item number $i", cls = "android.widget.TextView")
            } else {
                el(i * 50, "Open $i", cls = "android.widget.Button", clickable = true)
            }
        }
        val field = el(20000, null, cls = "android.widget.EditText", editable = true, id = "a:id/search")
        val out = ScreenSerializer.serialize(snap(*(many + field).toTypedArray()))
        assertThat(out.tokenEstimate).isAtMost(ScreenSerializer.DEFAULT_TOKEN_BUDGET)
        assertThat(out.dropped).isGreaterThan(0)
        assertThat(out.text).contains("edit \"search\"")
        assertThat(out.text).contains("more")
        assertThat(out.text).doesNotContain("Item number 100")
    }

    @Test
    fun `empty screen gives header only`() {
        val out = ScreenSerializer.serialize(ScreenSnapshot.EMPTY)
        assertThat(out.idToKey).isEmpty()
        assertThat(out.text).isEqualTo("app:unknown scroll:no")
    }
}
