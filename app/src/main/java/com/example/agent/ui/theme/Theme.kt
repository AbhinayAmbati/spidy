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

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightBrownAccent,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSecondary,
    onSurface = LightText,
    surfaceVariant = Color(0xFFEFEDE8),
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = StatusErrorLight,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.Black,
    secondary = DarkBrownAccent,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSecondary,
    onSurface = DarkText,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = StatusErrorDark,
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
