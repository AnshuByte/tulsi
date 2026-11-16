package com.tulsicare.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme

/**
 * Section header: 20sp SemiBold title with optional action item.
 */
@Composable
fun TulsiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = TulsiScreenPadding,
    action: (@Composable () -> Unit)? = null
) {
    val colors = TulsiTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )

        if (action != null) {
            action()
        }
    }
}
