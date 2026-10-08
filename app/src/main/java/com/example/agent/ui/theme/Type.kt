package com.example.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.agent.R

/*
 * Two faces, deliberately split by role:
 *
 *   Headings      Instrument Serif  — display + headline styles
 *   Body / UI     Inter             — titles, body, labels, buttons
 *
 * Inter is bundled as static instances (400/500/600) rather than the variable
 * font, so weights resolve exactly without relying on experimental variation APIs.
 */

/** Body/UI face. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Heading face. Instrument Serif ships a single weight. */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
)

private fun heading(
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = InstrumentSerif,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private fun ui(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val SpideyTypography = Typography(
    // ---- Headings: Instrument Serif ----
    displayLarge = heading(36, 44, -0.25),
    displayMedium = heading(32, 40, -0.25),
    headlineMedium = heading(28, 36),
    headlineSmall = heading(24, 32),

    // ---- Body / UI: Inter ----
    titleLarge = ui(20, 28, FontWeight.SemiBold),
    titleMedium = ui(18, 26, FontWeight.Medium),
    bodyLarge = ui(17, 26, FontWeight.Normal),
    bodyMedium = ui(15, 23, FontWeight.Normal),
    labelLarge = ui(15, 20, FontWeight.Medium),
    labelMedium = ui(14, 20, FontWeight.Medium, 0.1),
    labelSmall = ui(13, 18, FontWeight.Medium, 0.2),
)
