package com.example.agent.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Spidey deliberately uses *more* whitespace than a typical
 * Android app: prefer 24–32dp between major sections so the UI can breathe.
 */
object SpideySpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 40.dp
    val xxxl: Dp = 48.dp
    val huge: Dp = 64.dp

    /** Standard horizontal screen gutter. */
    val screenGutter: Dp = 24.dp

    /** Default vertical gap between major sections. */
    val sectionGap: Dp = 32.dp

    /** Minimum accessible touch target (Android guidance is 48dp). */
    val minTouchTarget: Dp = 48.dp
}

object SpideyElevation {
    val flat: Dp = 0.dp
    val soft: Dp = 2.dp
    val raised: Dp = 6.dp
    val floating: Dp = 12.dp
}
