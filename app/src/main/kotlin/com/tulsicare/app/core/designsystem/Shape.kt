package com.tulsicare.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Tulsi Spacing and Dimensions Tokens
val TulsiGrid = 8.dp
val TulsiScreenPadding = 20.dp
val TulsiCornerRadius = 12.dp
val TulsiBorderWidth = 1.dp
val TulsiMinTouchTarget = 48.dp
val TulsiSimpleModeTouchTarget = 64.dp

// Tulsi Minimalist Shapes: 12dp corner radius across components
val tulsiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)
