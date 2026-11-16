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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = PureWhite,
    primaryContainer = SurfaceTintTeal,
    onPrimaryContainer = PrimaryTealDark,
    secondary = DeepNavy,
    onSecondary = PureWhite,
    secondaryContainer = StatusSkippedGreyLight,
    onSecondaryContainer = DeepNavy,
    tertiary = StatusSnoozeAmber,
    onTertiary = PureWhite,
    background = SoftBackground,
    onBackground = DeepNavy,
    surface = PureWhite,
    onSurface = DeepNavy,
    surfaceVariant = SurfaceTintTeal,
    onSurfaceVariant = NavyMuted,
    error = StatusMissedCoral,
    onError = PureWhite,
    errorContainer = StatusMissedCoralLight,
    onErrorContainer = StatusMissedCoral,
    outline = StatusSkippedGrey
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkTealPrimary,
    secondary = DarkTextPrimary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = StatusSnoozeAmber,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = StatusMissedCoral,
    onError = PureWhite,
    errorContainer = DarkSurfaceVariant,
    onErrorContainer = StatusMissedCoral,
    outline = NavyMuted
)

private val LightSemanticColors = tulsiSemanticColors(
    taken = StatusTakenGreen,
    takenContainer = StatusTakenGreenLight,
    snooze = StatusSnoozeAmber,
    snoozeContainer = StatusSnoozeAmberLight,
    missed = StatusMissedCoral,
    missedContainer = StatusMissedCoralLight,
    skipped = StatusSkippedGrey,
    skippedContainer = StatusSkippedGreyLight,
    deepNavy = DeepNavy,
    softBackground = SoftBackground
)

private val DarkSemanticColors = tulsiSemanticColors(
    taken = StatusTakenGreen,
    takenContainer = DarkSurfaceVariant,
    snooze = StatusSnoozeAmber,
    snoozeContainer = DarkSurfaceVariant,
    missed = StatusMissedCoral,
    missedContainer = DarkSurfaceVariant,
    skipped = StatusSkippedGrey,
    skippedContainer = DarkSurfaceVariant,
    deepNavy = DarkTextPrimary,
    softBackground = DarkBackground
)

@Composable
fun TulsiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Use WindowCompat (edge-to-edge) instead of deprecated statusBarColor
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocaltulsiSemanticColors provides semanticColors
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
    val semanticColors: tulsiSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocaltulsiSemanticColors.current
}
