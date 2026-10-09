package com.example.agent.domain.model

/**
 * A short, stable identifier for an element on screen.
 *
 * The positional `ref` it replaces was an index into a single snapshot, so the moment the
 * screen changed it pointed at something else — which is why taps by ref kept landing on
 * the wrong element or failing outright. A key is derived from what the element *is*, so
 * it still identifies the same thing after a re-read, and it simply disappears when the
 * element leaves the screen. That is a truthful failure rather than a wrong tap.
 */
object ElementKey {

    /**
     * Bounds are bucketed, so a one-pixel shift or a scrolling animation cannot change a
     * key, while two identical-looking rows in a list still get distinct keys.
     */
    private const val POSITION_BUCKET_PX = 16

    fun of(
        id: String?,
        className: String?,
        text: String?,
        contentDescription: String?,
        bounds: Bounds,
    ): String {
        val cx = bounds.centerX / POSITION_BUCKET_PX
        val cy = bounds.centerY / POSITION_BUCKET_PX
        val raw = listOf(
            id.orEmpty(),
            className.orEmpty(),
            text.orEmpty(),
            contentDescription.orEmpty(),
            "$cx,$cy",
        ).joinToString("\u0000")

        // String.hashCode is specified by the JDK, so this is stable across runs.
        val n = raw.hashCode().toLong() and 0xFFFFFFL
        return n.toString(16).padStart(6, '0')
    }
}
