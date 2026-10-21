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
 * Material 3's `lightColorScheme()`/`darkColorScheme()` fill any role you omit with the
 * baseline palette, whose neutrals are lavender-grey. Setting the whole surface ramp
 * keeps menus, sheets and containers on the blue/white palette.
 */

private val LightScheme = lightColorScheme(
    primary = SpideyBlue,
    onPrimary = Color.White,
    primaryContainer = SpideyBlueTint,
    onPrimaryContainer = SpideyBlueDeep,

    secondary = SpideyBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = SpideyBlueTint,
    onSecondaryContainer = SpideyBlueDeep,

    tertiary = StatusSuccessLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF5EC),
    onTertiaryContainer = Color(0xFF00573A),

    background = LightBackground,
    onBackground = LightText,

    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = SpideyBlue,
    inverseSurface = SpideyInk,
    inverseOnSurface = Color.White,
    inversePrimary = SpideyBlueLight,

    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3E6EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFE),
    surfaceContainer = LightMuted,
    surfaceContainerHigh = Color(0xFFEDF0F7),
    surfaceContainerHighest = Color(0xFFEBEEF6),

    outline = LightBorder,
    outlineVariant = LightBorder,

    error = StatusErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E4),
    onErrorContainer = Color(0xFF7A1A12),

    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = SpideyBlueLight,
    onPrimary = Color(0xFF00133D),
    primaryContainer = Color(0xFF1B2A55),
    onPrimaryContainer = Color(0xFFD6E1FF),

    secondary = Color(0xFF8FB0FF),
    onSecondary = Color(0xFF00133D),
    secondaryContainer = Color(0xFF1B2A55),
    onSecondaryContainer = Color(0xFFD6E1FF),

    tertiary = StatusSuccessDark,
    onTertiary = Color(0xFF00281A),
    tertiaryContainer = Color(0xFF0F3A2B),
    onTertiaryContainer = Color(0xFFB7F2DC),

    background = DarkBackground,
    onBackground = DarkText,

    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = SpideyBlueLight,
    inverseSurface = Color(0xFFF1F3FA),
    inverseOnSurface = Color(0xFF0B1020),
    inversePrimary = SpideyBlue,

    surfaceBright = Color(0xFF232B45),
    surfaceDim = DarkBackground,
    surfaceContainerLowest = Color(0xFF080C19),
    surfaceContainerLow = Color(0xFF111729),
    surfaceContainer = DarkMuted,
    surfaceContainerHigh = Color(0xFF1B2238),
    surfaceContainerHighest = Color(0xFF232B45),

    outline = DarkBorder,
    outlineVariant = DarkBorder,

    error = StatusErrorDark,
    onError = Color(0xFF3B0A06),
    errorContainer = Color(0xFF3F1512),
    onErrorContainer = Color(0xFFFFDAD6),

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
