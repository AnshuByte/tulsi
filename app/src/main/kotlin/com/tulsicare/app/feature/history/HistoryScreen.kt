package com.tulsicare.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiEmptyState
import com.tulsicare.app.core.designsystem.component.TulsiListRow
import com.tulsicare.app.core.designsystem.component.TulsiOutlinedButton
import com.tulsicare.app.core.designsystem.component.TulsiSectionHeader
import com.tulsicare.app.core.designsystem.component.TulsiStatusDot
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    HistoryScreenContent(
        uiState = uiState,
        onRangeSelected = viewModel::loadHistory,
        onExportPdf = { viewModel.exportPdf(context) },
        modifier = modifier
    )
}

@Composable
fun HistoryScreenContent(
    uiState: HistoryUiState,
    onRangeSelected: (Int) -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors

    // Group events by date (e.g. "Monday, Nov 16")
    val groupedEvents = groupEventsByDate(uiState.events)

    Box(
        modifier = modifier.fillMaxSize().background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Screen Title
            item(key = "title_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = TulsiScreenPadding, end = TulsiScreenPadding, top = 8.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "History",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Segmented control: 7 days | 30 days
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(TulsiBorderWidth, colors.border, RoundedCornerShape(TulsiCornerRadius))
                            .background(colors.surface, RoundedCornerShape(TulsiCornerRadius))
                            .padding(4.dp)
                    ) {
                        SegmentedOption(
                            text = "7 days",
                            isSelected = uiState.rangeDays == 7,
                            onClick = { onRangeSelected(7) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOption(
                            text = "30 days",
                            isSelected = uiState.rangeDays == 30,
                            onClick = { onRangeSelected(30) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Three numbers in a row: Adherence, Streak, Missed
            item(key = "stats_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TulsiScreenPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatNumberBox(
                        value = "${uiState.adherencePercent}%",
                        label = "Adherence",
                        modifier = Modifier.weight(1f)
                    )
                    StatNumberBox(
                        value = "${uiState.streakDays}d",
                        label = "Streak",
                        modifier = Modifier.weight(1f)
                    )
                    StatNumberBox(
                        value = "${uiState.missedDoses}",
                        label = "Missed",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Export PDF Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TulsiScreenPadding)
                ) {
                    TulsiOutlinedButton(
                        text = "Export PDF report",
                        onClick = onExportPdf,
                        isLoading = uiState.isExportingPdf,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = TulsiBorderWidth,
                    color = colors.border,
                    modifier = Modifier.padding(horizontal = TulsiScreenPadding)
                )
            }

            // Events List grouped by Day
            if (groupedEvents.isEmpty()) {
                item(key = "empty_history") {
                    Spacer(modifier = Modifier.height(48.dp))
                    TulsiEmptyState(
                        title = "No history recorded",
                        message = "Dose actions from the last ${uiState.rangeDays} days will appear here."
                    )
                }
            } else {
                groupedEvents.forEach { (dateHeader, events) ->
                    item(key = "header_$dateHeader") {
                        TulsiSectionHeader(title = dateHeader)
                    }

                    items(events, key = { it.id }) { event ->
                        val timeStr = formatTime(event.scheduledAt)
                        TulsiListRow(
                            title = event.medicineName,
                            subtitle = "${event.dosage} · $timeStr",
                            leadingContent = {
                                TulsiStatusDot(
                                    status = event.status,
                                    showLabel = false,
                                    dotSize = 10.dp
                                )
                            },
                            trailingContent = {
                                TulsiStatusDot(
                                    status = event.status,
                                    showLabel = true
                                )
                            },
                            showDivider = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) colors.buttonPrimary else Color.Transparent,
                shape = RoundedCornerShape(TulsiCornerRadius - 2.dp)
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = if (isSelected) colors.buttonText else colors.textPrimary
        )
    }
}

@Composable
private fun StatNumberBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = colors.textSecondary
        )
    }
}

private fun groupEventsByDate(events: List<DoseEvent>): Map<String, List<DoseEvent>> {
    val sdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val result = linkedMapOf<String, MutableList<DoseEvent>>()
    events.sortedByDescending { it.scheduledAt }.forEach { event ->
        val dateStr = sdf.format(Date(event.scheduledAt))
        result.getOrPut(dateStr) { mutableListOf() }.add(event)
    }
    return result
}

private fun formatTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

// -----------------------------------------------------------------------------
// Previews: Light, Dark, 1.5x Font Scale
// -----------------------------------------------------------------------------

@Preview(name = "History - Light", showBackground = true)
@Composable
fun HistoryScreenPreviewLight() {
    TulsiTheme(darkTheme = false) {
        HistoryScreenContent(
            uiState = getSampleHistoryUiState(),
            onRangeSelected = {},
            onExportPdf = {}
        )
    }
}

@Preview(name = "History - Dark", showBackground = true)
@Composable
fun HistoryScreenPreviewDark() {
    TulsiTheme(darkTheme = true) {
        HistoryScreenContent(
            uiState = getSampleHistoryUiState(),
            onRangeSelected = {},
            onExportPdf = {}
        )
    }
}

@Preview(name = "History - 1.5x Font Scale", fontScale = 1.5f, showBackground = true)
@Composable
fun HistoryScreenPreviewLargeFont() {
    TulsiTheme(darkTheme = false) {
        HistoryScreenContent(
            uiState = getSampleHistoryUiState(),
            onRangeSelected = {},
            onExportPdf = {}
        )
    }
}

private fun getSampleHistoryUiState(): HistoryUiState {
    val now = System.currentTimeMillis()
    return HistoryUiState(
        rangeDays = 7,
        adherencePercent = 94,
        streakDays = 6,
        missedDoses = 1,
        events = listOf(
            DoseEvent(id = "1", medicineName = "Metformin", dosage = "500mg", scheduledAt = now, status = DoseStatus.TAKEN),
            DoseEvent(id = "2", medicineName = "Aspirin", dosage = "75mg", scheduledAt = now - 36000000L, status = DoseStatus.MISSED)
        )
    )
}
