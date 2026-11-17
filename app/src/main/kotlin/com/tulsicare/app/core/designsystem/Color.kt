package com.tulsicare.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// --- Light Theme Tokens ---
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF6F6F6)
val LightBorder = Color(0xFFE4E4E4)
val LightTextPrimary = Color(0xFF0A0A0A)
val LightTextSecondary = Color(0xFF6B6B6B)
val LightTextDisabled = Color(0xFFA8A8A8)
val LightButtonPrimary = Color(0xFF0A0A0A)
val LightButtonText = Color(0xFFFFFFFF)

// --- Dark Theme Tokens ---
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF111111)
val DarkBorder = Color(0xFF262626)
val DarkTextPrimary = Color(0xFFFAFAFA)
val DarkTextSecondary = Color(0xFF9A9A9A)
val DarkTextDisabled = Color(0xFF555555)
val DarkButtonPrimary = Color(0xFFFFFFFF)
val DarkButtonText = Color(0xFF000000)

// --- Accent: Tulsi Green (Used ONLY for leaf logo, selected state, and Taken status) ---
val TulsiGreenLight = Color(0xFF1F7A5C)
val TulsiGreenDark = Color(0xFF3DBE8B)

// --- Semantic Status Colors (Dots & text only - NEVER filled card backgrounds) ---
val StatusMissed = Color(0xFFD64545)
val StatusSnoozed = Color(0xFFC98A1B)

@Immutable
data class TulsiColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val buttonPrimary: Color,
    val buttonText: Color,
    val accent: Color,
    val statusTaken: Color,
    val statusMissed: Color = StatusMissed,
    val statusSnoozed: Color = StatusSnoozed,
    val statusSkipped: Color,
    val isDark: Boolean
)

val LocalTulsiColors = staticCompositionLocalOf {
    TulsiColors(
        background = LightBackground,
        surface = LightSurface,
        border = LightBorder,
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        textDisabled = LightTextDisabled,
        buttonPrimary = LightButtonPrimary,
        buttonText = LightButtonText,
        accent = TulsiGreenLight,
        statusTaken = TulsiGreenLight,
        statusMissed = StatusMissed,
        statusSnoozed = StatusSnoozed,
        statusSkipped = LightTextSecondary,
        isDark = false
    )
}
