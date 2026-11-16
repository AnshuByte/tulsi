package com.tulsicare.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// tulsi Brand Shapes: Friendly 16dp rounded corners
val tulsiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),   // Default card / container radius
    large = RoundedCornerShape(20.dp),    // Dialogs, sheets
    extraLarge = RoundedCornerShape(28.dp) // Pill badges, large modal bottom sheets
)
