package com.example.agent.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Generous on purpose: prefer 24dp between major sections so the
 * big rounded cards have room to breathe.
 */
object SpideySpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 40.dp

    /** Standard horizontal screen gutter. */
    val screenGutter: Dp = 16.dp

    /** Default vertical gap between major sections. */
    val sectionGap: Dp = 24.dp

    /** Height of buttons and the search bar. */
    val controlHeight: Dp = 52.dp

    /** Default inset inside a card. */
    val cardPadding: Dp = 16.dp

    /** Minimum tap target. */
    val minTouchTarget: Dp = 48.dp

    /** Room left at the bottom of a scrolling screen so a sticky action bar never hides content. */
    val stickyBarClearance: Dp = 96.dp
}
