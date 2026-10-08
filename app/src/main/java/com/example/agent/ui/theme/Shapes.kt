package com.example.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Modern rounded corners — never fully pill-shaped everywhere.
 */
val SpideyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Floating surfaces (sheets, FABs, status overlays) use a larger radius. */
val FloatingShape = RoundedCornerShape(24.dp)
