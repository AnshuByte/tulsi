package com.tulsicare.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.model.DoseStatus

/**
 * Status dot: Small 8dp circle with optional 14sp caption text.
 * Strictly adheres to semantic status colors (green/red/amber/grey) as dots/text,
 * NEVER as large container fills.
 */
@Composable
fun TulsiStatusDot(
    status: DoseStatus,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    showLabel: Boolean = true
) {
    val colors = TulsiTheme.colors
    val (dotColor, labelText) = when (status) {
        DoseStatus.TAKEN -> colors.statusTaken to "Taken"
        DoseStatus.MISSED -> colors.statusMissed to "Missed"
        DoseStatus.SNOOZED -> colors.statusSnoozed to "Snoozed"
        DoseStatus.SKIPPED -> colors.statusSkipped to "Skipped"
        DoseStatus.PENDING -> colors.textSecondary to "Pending"
    }

    TulsiStatusDot(
        color = dotColor,
        label = if (showLabel) labelText else null,
        modifier = modifier,
        dotSize = dotSize
    )
}

@Composable
fun TulsiStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
    dotSize: Dp = 8.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(color, CircleShape)
        )
        if (!label.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = color
            )
        }
    }
}
