package com.example.agent

import com.example.agent.domain.model.Bounds
import com.example.agent.domain.model.ElementKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Keys replaced positional refs, which pointed at whatever happened to sit at that index
 * in one snapshot — the cause of taps landing on the wrong element or failing outright.
 *
 * A key must identify the same element after a re-read, and must distinguish two
 * otherwise identical elements in a list.
 */
class ElementKeyTest {

    private fun key(
        id: String? = null,
        className: String? = null,
        text: String? = null,
        cd: String? = null,
        bounds: Bounds = Bounds(0, 0, 100, 100),
    ) = ElementKey.of(id, className, text, cd, bounds)

    @Test
    fun `the same element yields the same key`() {
        assertThat(key(text = "Search", className = "android.widget.Button"))
            .isEqualTo(key(text = "Search", className = "android.widget.Button"))
    }

    @Test
    fun `different elements yield different keys`() {
        assertThat(key(text = "Search")).isNotEqualTo(key(text = "Cancel"))
    }

    @Test
    fun `two identical rows are told apart by position`() {
        // A list of identical "Add" buttons is the classic case a content-only key gets wrong.
        val first = key(text = "Add", bounds = Bounds(0, 0, 100, 100))
        val second = key(text = "Add", bounds = Bounds(0, 200, 100, 300))

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `a one pixel shift does not change the key`() {
        // Layout jitter and scroll animations must not invalidate a key.
        val a = key(text = "Search", bounds = Bounds(0, 0, 100, 100))
        val b = key(text = "Search", bounds = Bounds(0, 1, 100, 101))

        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `the key is short and hex`() {
        val k = key(text = "Search")
        assertThat(k).hasLength(6)
        assertThat(k).matches("[0-9a-f]{6}")
    }

    @Test
    fun `a view id alone is enough to identify an element`() {
        val a = key(id = "com.android.chrome:id/url_bar")
        val b = key(id = "com.android.chrome:id/url_bar")
        val c = key(id = "com.android.chrome:id/tab_switcher")

        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }
}
