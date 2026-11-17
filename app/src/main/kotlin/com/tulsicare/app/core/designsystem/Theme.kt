package com.tulsicare.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LightButtonPrimary,
    onPrimary = LightButtonText,
    primaryContainer = LightSurface,
    onPrimaryContainer = LightTextPrimary,
    secondary = TulsiGreenLight,
    onSecondary = LightButtonText,
    secondaryContainer = LightSurface,
    onSecondaryContainer = TulsiGreenLight,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightBackground,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = StatusMissed,
    onError = LightBackground
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkButtonPrimary,
    onPrimary = DarkButtonText,
    primaryContainer = DarkSurface,
    onPrimaryContainer = DarkTextPrimary,
    secondary = TulsiGreenDark,
    onSecondary = DarkButtonText,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = TulsiGreenDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkBackground,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = StatusMissed,
    onError = DarkBackground
)

private val LightTulsiColors = TulsiColors(
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

private val DarkTulsiColors = TulsiColors(
    background = DarkBackground,
    surface = DarkSurface,
    border = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textDisabled = DarkTextDisabled,
    buttonPrimary = DarkButtonPrimary,
    buttonText = DarkButtonText,
    accent = TulsiGreenDark,
    statusTaken = TulsiGreenDark,
    statusMissed = StatusMissed,
    statusSnoozed = StatusSnoozed,
    statusSkipped = DarkTextSecondary,
    isDark = true
)

@Composable
fun TulsiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tulsiColors = if (darkTheme) DarkTulsiColors else LightTulsiColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalTulsiColors provides tulsiColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = tulsiTypography,
            shapes = tulsiShapes,
            content = content
        )
    }
}

object TulsiTheme {
    val colors: TulsiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTulsiColors.current
}
