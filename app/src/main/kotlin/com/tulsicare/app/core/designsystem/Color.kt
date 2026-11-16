package com.tulsicare.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primary Brand Colors
val PrimaryTeal = Color(0xFF0F9D8A)
val PrimaryTealLight = Color(0xFF35BBA7)
val PrimaryTealDark = Color(0xFF087263)

val DeepNavy = Color(0xFF12304A)
val DeepNavyLight = Color(0xFF1F4A70)
val NavyMuted = Color(0xFF4A6277)

val SoftBackground = Color(0xFFF6FAF9)
val PureWhite = Color(0xFFFFFFFF)
val SurfaceTintTeal = Color(0xFFE9F6F4)

// Semantic Status Colors
val StatusTakenGreen = Color(0xFF2E9E5B)
val StatusTakenGreenLight = Color(0xFFE4F7EC)

val StatusSnoozeAmber = Color(0xFFF2A93B)
val StatusSnoozeAmberLight = Color(0xFFFEF5E7)

val StatusMissedCoral = Color(0xFFF26B5B)
val StatusMissedCoralLight = Color(0xFFFDECEB)

val StatusSkippedGrey = Color(0xFF8A97A3)
val StatusSkippedGreyLight = Color(0xFFF0F3F5)

// Dark Theme Variants
val DarkBackground = Color(0xFF0D1B26)
val DarkSurface = Color(0xFF152635)
val DarkSurfaceVariant = Color(0xFF1D3447)
val DarkTextPrimary = Color(0xFFF0F5F8)
val DarkTextSecondary = Color(0xFFA1B4C3)
val DarkTealPrimary = Color(0xFF14B8A0)

/**
 * Custom semantic colors for tulsi adherence & status tags
 */
@Immutable
data class tulsiSemanticColors(
    val taken: Color = StatusTakenGreen,
    val takenContainer: Color = StatusTakenGreenLight,
    val snooze: Color = StatusSnoozeAmber,
    val snoozeContainer: Color = StatusSnoozeAmberLight,
    val missed: Color = StatusMissedCoral,
    val missedContainer: Color = StatusMissedCoralLight,
    val skipped: Color = StatusSkippedGrey,
    val skippedContainer: Color = StatusSkippedGreyLight,
    val deepNavy: Color = DeepNavy,
    val softBackground: Color = SoftBackground
)

val LocaltulsiSemanticColors = staticCompositionLocalOf { tulsiSemanticColors() }
