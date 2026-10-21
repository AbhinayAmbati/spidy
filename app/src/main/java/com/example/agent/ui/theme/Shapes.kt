package com.example.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Large, friendly radii: cards read as soft tiles, inputs and buttons as pills,
 * as on Instamart.
 */
val SpideyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Hero banners and the biggest cards. */
val HeroShape = RoundedCornerShape(24.dp)

/** Buttons, chips, status pills. */
val PillShape = RoundedCornerShape(percent = 50)

/** Bottom sheets round only their top corners. */
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
