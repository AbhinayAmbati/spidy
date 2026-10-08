package com.example.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.agent.core.common.Motion
import com.example.agent.domain.model.AppearanceMode

val LocalSpideyColors = staticCompositionLocalOf { LightSpideyColors }

/** False when the user has asked the system to remove animations. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

/*
 * Every role is set explicitly.
 *
 * Material 3's `lightColorScheme()`/`darkColorScheme()` fill any role you omit with
 * the *baseline* palette, whose neutrals are lavender-grey (#F3EDF7 and friends).
 * Omitting the surfaceContainer family therefore leaked grey into any component
 * that reads it — DropdownMenu uses `surfaceContainer`, for example. Setting the
 * full neutral ramp keeps the UI monochrome.
 */

// Light neutral ramp
private val LSurfaceLowest = Color(0xFFFFFFFF)
private val LSurfaceLow = Color(0xFFFCFCFA)
private val LSurface = Color(0xFFF7F7F5)
private val LSurfaceHigh = Color(0xFFF1F1EE)
private val LSurfaceHighest = Color(0xFFEBEBE7)
private val LSurfaceDim = Color(0xFFE5E3DE)
private val LSurfaceBright = Color(0xFFFFFFFF)

// Dark neutral ramp
private val DSurfaceLowest = Color(0xFF0A0A0A)
private val DSurfaceLow = Color(0xFF121212)
private val DSurface = Color(0xFF171717)
private val DSurfaceHigh = Color(0xFF1F1F1F)
private val DSurfaceHighest = Color(0xFF262626)
private val DSurfaceDim = Color(0xFF0D0D0D)
private val DSurfaceBright = Color(0xFF2A2A2A)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE6E0),
    onPrimaryContainer = LightText,

    secondary = LightBrownAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEDE8),
    onSecondaryContainer = LightText,

    tertiary = LightBrownDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFEDE8),
    onTertiaryContainer = LightText,

    background = LSurface,
    onBackground = LightText,

    surface = LSurfaceLowest,
    onSurface = LightText,
    surfaceVariant = Color(0xFFEFEDE8),
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = LightPrimary,
    inverseSurface = LightPrimary,
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = DarkBrownAccent,

    surfaceBright = LSurfaceBright,
    surfaceDim = LSurfaceDim,
    surfaceContainerLowest = LSurfaceLowest,
    surfaceContainerLow = LSurfaceLow,
    surfaceContainer = LSurface,
    surfaceContainerHigh = LSurfaceHigh,
    surfaceContainerHighest = LSurfaceHighest,

    outline = LightBorder,
    outlineVariant = LightBorder,

    error = StatusErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFF3E0DD),
    onErrorContainer = Color(0xFF5C2B25),

    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E2620),
    onPrimaryContainer = DarkText,

    secondary = DarkBrownAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = DarkText,

    tertiary = DarkBrownDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2E2620),
    onTertiaryContainer = DarkText,

    background = DSurfaceDim,
    onBackground = DarkText,

    surface = DSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = DarkPrimary,
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF111111),
    inversePrimary = LightBrownAccent,

    surfaceBright = DSurfaceBright,
    surfaceDim = DSurfaceDim,
    surfaceContainerLowest = DSurfaceLowest,
    surfaceContainerLow = DSurfaceLow,
    surfaceContainer = DSurface,
    surfaceContainerHigh = DSurfaceHigh,
    surfaceContainerHighest = DSurfaceHighest,

    outline = DarkBorder,
    outlineVariant = DarkBorder,

    error = StatusErrorDark,
    onError = Color.Black,
    errorContainer = Color(0xFF3A211E),
    onErrorContainer = Color(0xFFF3D9D5),

    scrim = Color.Black,
)

/** Convenience accessor used across the UI. */
val MaterialTheme.spideyColors: SpideyColors
    @Composable get() = LocalSpideyColors.current

@Composable
fun SpideyTheme(
    appearance: AppearanceMode = AppearanceMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance) {
        AppearanceMode.System -> isSystemInDarkTheme()
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }

    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val spideyColors = if (darkTheme) DarkSpideyColors else LightSpideyColors

    val context = LocalContext.current
    val motionEnabled = remember(context) { Motion.animationsEnabled(context) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSpideyColors provides spideyColors,
        LocalMotionEnabled provides motionEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpideyTypography,
            shapes = SpideyShapes,
            content = content,
        )
    }
}
