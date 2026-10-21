package com.example.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.agent.R

/*
 * One face, Inter, in bold-leaning weights. Headlines are SemiBold (the heaviest weight
 * bundled) so the hierarchy reads like Instamart's heavy Gilroy.
 */

/** The app's only typeface. Static instances (400/500/600) so weights resolve exactly. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
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
    displayLarge = ui(36, 42, FontWeight.SemiBold, -0.5),
    displayMedium = ui(30, 36, FontWeight.SemiBold, -0.4),
    headlineMedium = ui(26, 32, FontWeight.SemiBold, -0.3),
    headlineSmall = ui(22, 28, FontWeight.SemiBold, -0.2),

    titleLarge = ui(20, 26, FontWeight.SemiBold),
    titleMedium = ui(17, 24, FontWeight.SemiBold),
    bodyLarge = ui(16, 24, FontWeight.Normal),
    bodyMedium = ui(14, 21, FontWeight.Normal),
    bodySmall = ui(12, 17, FontWeight.Normal),
    labelLarge = ui(15, 20, FontWeight.SemiBold),
    labelMedium = ui(13, 18, FontWeight.SemiBold),
    labelSmall = ui(12, 16, FontWeight.Medium, 0.2),
)
