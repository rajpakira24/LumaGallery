package com.webstudio.lumagallery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Shape Scale
 *
 * Defines corner radius values for components.
 * Rounded corners create a softer, more modern appearance.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Small chips, small buttons
    small = RoundedCornerShape(12.dp),       // Cards, dialogs
    medium = RoundedCornerShape(18.dp),      // Bottom sheets, photo grid items
    large = RoundedCornerShape(24.dp),       // Large cards, app bars
    extraLarge = RoundedCornerShape(32.dp)   // FABs, special elements
)
