package com.example.agent.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Spidey palette — intentionally near-monochromatic (black / white) with a single
 * restrained brown accent. Colour is used for hierarchy and meaning, never decoration.
 */

// Light theme
val LightBackground = Color(0xFFF7F7F5)
val LightPrimary = Color(0xFF111111)
val LightSecondary = Color(0xFFFFFFFF)
val LightText = Color(0xFF111111)
val LightTextSecondary = Color(0xFF6B6B67)
val LightBorder = Color(0xFFE5E3DE)
val LightBrownAccent = Color(0xFF8B5E3C)
val LightBrownDark = Color(0xFF5C3D2E)
val LightGlass = Color(0xA6FFFFFF) // rgba(255,255,255,0.65)

// Dark theme
val DarkBackground = Color(0xFF0D0D0D)
val DarkPrimary = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFF171717)
val DarkText = Color(0xFFF5F5F5)
val DarkTextSecondary = Color(0xFFA5A5A0)
val DarkBorder = Color(0xFF292929)
val DarkBrownAccent = Color(0xFFA77B5A)
val DarkBrownDark = Color(0xFF6F4935)
val DarkGlass = Color(0x0FFFFFFF) // rgba(255,255,255,0.06)

// Splash identity — used only on the launch screen.
val SpideyBlack = Color(0xFF000000)
val SpideyRed = Color(0xFFD32F2F)
val SpideyRedDeep = Color(0xFF8E1F1F)

// Semantic status colours (used sparingly, kept muted to preserve the monochrome feel)
val StatusSuccessLight = Color(0xFF4B6B4B)
val StatusSuccessDark = Color(0xFF8FB08F)
val StatusWarningLight = Color(0xFF8B5E3C)
val StatusWarningDark = Color(0xFFA77B5A)
val StatusErrorLight = Color(0xFF9B4A3F)
val StatusErrorDark = Color(0xFFC98A80)

/**
 * Colours that Material 3's [androidx.compose.material3.ColorScheme] does not model.
 * Exposed through [LocalSpideyColors] so every component reads from one source of truth.
 */
@Immutable
data class SpideyColors(
    val textSecondary: Color,
    val border: Color,
    val brownAccent: Color,
    val brownDark: Color,
    val glass: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

val LightSpideyColors = SpideyColors(
    textSecondary = LightTextSecondary,
    border = LightBorder,
    brownAccent = LightBrownAccent,
    brownDark = LightBrownDark,
    glass = LightGlass,
    success = StatusSuccessLight,
    warning = StatusWarningLight,
    error = StatusErrorLight,
)

val DarkSpideyColors = SpideyColors(
    textSecondary = DarkTextSecondary,
    border = DarkBorder,
    brownAccent = DarkBrownAccent,
    brownDark = DarkBrownDark,
    glass = DarkGlass,
    success = StatusSuccessDark,
    warning = StatusWarningDark,
    error = StatusErrorDark,
)
