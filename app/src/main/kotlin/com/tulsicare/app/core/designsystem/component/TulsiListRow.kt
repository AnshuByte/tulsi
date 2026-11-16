package com.tulsicare.app.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiMinTouchTarget
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiSimpleModeTouchTarget
import com.tulsicare.app.core.designsystem.TulsiTheme

/**
 * Clean plain row separated by a hairline 1dp divider (no cards inside cards).
 * Uses weight(1f) to prevent text from ever wrapping one character per line.
 */
@Composable
fun TulsiListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    isSimpleMode: Boolean = false,
    horizontalPadding: Dp = TulsiScreenPadding
) {
    val colors = TulsiTheme.colors
    val minHeight: Dp = if (isSimpleMode) TulsiSimpleModeTouchTarget else TulsiMinTouchTarget

    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = horizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 120.dp)
            ) {
                Text(
                    text = title,
                    fontSize = if (isSimpleMode) 18.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = if (isSimpleMode) 16.sp else 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.widthIn(min = 48.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    trailingContent()
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                thickness = TulsiBorderWidth,
                color = colors.border
            )
        }
    }
}
