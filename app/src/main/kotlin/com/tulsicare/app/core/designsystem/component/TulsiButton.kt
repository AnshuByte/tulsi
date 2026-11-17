package com.tulsicare.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tulsicare.app.R
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiMinTouchTarget
import com.tulsicare.app.core.designsystem.TulsiSimpleModeTouchTarget
import com.tulsicare.app.core.designsystem.TulsiTheme

/**
 * Primary action button: Solid black in Light, Solid white in Dark.
 * Respects 12dp corner radius and minimum 48dp (or 64dp simple mode) touch target.
 */
@Composable
fun TulsiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSimpleMode: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val minHeight: Dp = if (isSimpleMode) TulsiSimpleModeTouchTarget else TulsiMinTouchTarget
    val colors = TulsiTheme.colors

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(TulsiCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.buttonPrimary,
            contentColor = colors.buttonText,
            disabledContainerColor = colors.textDisabled,
            disabledContentColor = colors.background
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.buttonText,
                strokeWidth = 2.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = if (isSimpleMode) 18.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.buttonText
                )
            }
        }
    }
}

/**
 * Outlined button: 1dp border, transparent background.
 */
@Composable
fun TulsiOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSimpleMode: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val minHeight: Dp = if (isSimpleMode) TulsiSimpleModeTouchTarget else TulsiMinTouchTarget
    val colors = TulsiTheme.colors

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(TulsiCornerRadius),
        border = BorderStroke(TulsiBorderWidth, if (enabled) colors.border else colors.textDisabled),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.textPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = if (isSimpleMode) 18.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) colors.textPrimary else colors.textDisabled
                )
            }
        }
    }
}

/**
 * Text button: Borderless, quiet interaction.
 */
@Composable
fun TulsiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = TulsiTheme.colors.textPrimary,
    isSimpleMode: Boolean = false
) {
    val minHeight: Dp = if (isSimpleMode) TulsiSimpleModeTouchTarget else TulsiMinTouchTarget

    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        enabled = enabled,
        shape = RoundedCornerShape(TulsiCornerRadius),
        colors = ButtonDefaults.textButtonColors(
            contentColor = textColor,
            disabledContentColor = TulsiTheme.colors.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = if (isSimpleMode) 18.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) textColor else TulsiTheme.colors.textDisabled
        )
    }
}

/**
 * Dedicated Google Sign-In button:
 * White button with 1dp border, standard Google "G", "Continue with Google".
 */
@Composable
fun TulsiGoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSimpleMode: Boolean = false
) {
    val minHeight: Dp = if (isSimpleMode) TulsiSimpleModeTouchTarget else TulsiMinTouchTarget
    val colors = TulsiTheme.colors

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(TulsiCornerRadius),
        border = BorderStroke(TulsiBorderWidth, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (colors.isDark) colors.surface else Color.White,
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.textPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Continue with Google",
                    fontSize = if (isSimpleMode) 18.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            }
        }
    }
}
