package com.example.agent.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Spidey palette — a saturated Instamart-style blue on clean white. Blue carries every
 * primary action and selected state; green and red are reserved for success and failure.
 */

// Brand
val SpideyBlue = Color(0xFF0050FF)
val SpideyBlueDeep = Color(0xFF0A2A8F)
val SpideyBlueTint = Color(0xFFE8EEFF)
val SpideyBlueLight = Color(0xFF5C8DFF)

// Light theme
val LightBackground = Color(0xFFF4F6FB)
val LightSurface = Color(0xFFFFFFFF)
val LightText = Color(0xFF282C3F)
val LightTextSecondary = Color(0xFF6B7080)
val LightBorder = Color(0xFFE3E6EF)

// Dark theme
val DarkBackground = Color(0xFF0B1020)
val DarkSurface = Color(0xFF151B2E)
val DarkText = Color(0xFFF1F3FA)
val DarkTextSecondary = Color(0xFFA2A8BC)
val DarkBorder = Color(0xFF262D45)

// Semantic status colours
val StatusSuccessLight = Color(0xFF00A16C)
val StatusSuccessDark = Color(0xFF3DD6A0)
val StatusWarningLight = Color(0xFFE08A00)
val StatusWarningDark = Color(0xFFFFB74D)
val StatusErrorLight = Color(0xFFD93025)
val StatusErrorDark = Color(0xFFFF7A70)

/** Always-black pill used for hero call-to-action buttons, as on Instamart banners. */
val SpideyInk = Color(0xFF02060C)

/**
 * Colours that Material 3's [androidx.compose.material3.ColorScheme] does not model.
 * Exposed through [LocalSpideyColors] so every component reads from one source of truth.
 */
@Immutable
data class SpideyColors(
    val textSecondary: Color,
    val border: Color,
    val blue: Color,
    val blueDeep: Color,
    val blueTint: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

val LightSpideyColors = SpideyColors(
    textSecondary = LightTextSecondary,
    border = LightBorder,
    blue = SpideyBlue,
    blueDeep = SpideyBlueDeep,
    blueTint = SpideyBlueTint,
    success = StatusSuccessLight,
    warning = StatusWarningLight,
    error = StatusErrorLight,
)

val DarkSpideyColors = SpideyColors(
    textSecondary = DarkTextSecondary,
    border = DarkBorder,
    blue = SpideyBlueLight,
    blueDeep = SpideyBlue,
    blueTint = Color(0xFF1B2A55),
    success = StatusSuccessDark,
    warning = StatusWarningDark,
    error = StatusErrorDark,
)
