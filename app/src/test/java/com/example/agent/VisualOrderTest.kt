package com.example.agent

import com.example.agent.domain.model.Bounds
import com.example.agent.domain.model.UIElement
import com.example.agent.domain.model.inVisualOrder
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The listing used to follow the accessibility tree walk, which has no relation to what is on
 * screen — so the model could not tell what was above or below, and scrolled blindly.
 */
class VisualOrderTest {

    private fun el(ref: Int, top: Int, left: Int, label: String) = UIElement(
        ref = ref,
        id = null,
        text = label,
        contentDescription = null,
        className = "android.widget.TextView",
        clickable = true,
        longClickable = false,
        editable = false,
        enabled = true,
        scrollable = false,
        bounds = Bounds(left, top, left + 100, top + 40),
    )

    @Test
    fun `elements are ordered top to bottom`() {
        val scrambled = listOf(
            el(0, top = 600, left = 0, label = "bottom"),
            el(1, top = 100, left = 0, label = "top"),
            el(2, top = 350, left = 0, label = "middle"),
        )

        assertThat(scrambled.inVisualOrder().map { it.text })
            .containsExactly("top", "middle", "bottom").inOrder()
    }

    @Test
    fun `elements on the same row are ordered left to right`() {
        val row = listOf(
            el(0, top = 200, left = 500, label = "right"),
            el(1, top = 200, left = 50, label = "left"),
        )

        assertThat(row.inVisualOrder().map { it.text }).containsExactly("left", "right").inOrder()
    }

    @Test
    fun `ordering does not depend on the input order`() {
        val a = listOf(el(0, 300, 0, "c"), el(1, 100, 0, "a"), el(2, 200, 0, "b"))
        val b = listOf(el(1, 100, 0, "a"), el(2, 200, 0, "b"), el(0, 300, 0, "c"))

        assertThat(a.inVisualOrder().map { it.text }).isEqualTo(b.inVisualOrder().map { it.text })
    }

    @Test
    fun `an already ordered list is unchanged`() {
        val ordered = listOf(el(0, 0, 0, "a"), el(1, 100, 0, "b"), el(2, 200, 0, "c"))

        assertThat(ordered.inVisualOrder().map { it.text }).isEqualTo(ordered.map { it.text })
    }
}
