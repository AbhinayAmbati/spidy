package com.example.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Deliberately restrained corner radii. Surfaces read as crisp panels rather than
 * pills — enough softening to feel modern, without the rounded-everything look.
 */
val SpideyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Floating surfaces (sheets, status overlays) get a touch more. */
val FloatingShape = RoundedCornerShape(14.dp)
