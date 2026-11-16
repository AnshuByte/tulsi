package com.tulsicare.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiButton
import com.tulsicare.app.core.designsystem.component.TulsiEmptyState
import com.tulsicare.app.core.designsystem.component.TulsiListRow
import com.tulsicare.app.core.designsystem.component.TulsiSectionHeader
import com.tulsicare.app.core.designsystem.component.TulsiStatusDot
import com.tulsicare.app.core.designsystem.component.TulsiTextButton
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddMedicine: () -> Unit,
    onNavigateToEditMedicine: (medicineId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onNavigateToAddMedicine = onNavigateToAddMedicine,
        onSelectMember = viewModel::selectMember,
        onMarkTaken = viewModel::markDoseTaken,
        onMarkSkipped = viewModel::markDoseSkipped,
        onRetry = viewModel::loadData,
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToAddMedicine: () -> Unit,
    onSelectMember: (String?) -> Unit,
    onMarkTaken: (String) -> Unit,
    onMarkSkipped: (String) -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors

    // Format current date
    val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val todayFormatted = dateFormat.format(Date())

    // Filter doses if a specific person is selected
    val filteredDoses = if (uiState.selectedMemberId != null) {
        uiState.todayDoses.filter { it.assignedMemberId == uiState.selectedMemberId }
    } else {
        uiState.todayDoses
    }.distinctBy { it.id }

    val totalDoses = filteredDoses.size
    val takenDoses = filteredDoses.count { it.status == DoseStatus.TAKEN }
    val progressSubtext = if (totalDoses > 0) "$takenDoses of $totalDoses taken" else "No scheduled doses today"

    // Group doses into Morning, Afternoon, Evening
    val groupedDoses = groupDosesByTimeOfDay(filteredDoses)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // Single black circular button with '+'
            FloatingActionButton(
                onClick = onNavigateToAddMedicine,
                containerColor = colors.buttonPrimary,
                contentColor = colors.buttonText,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Medicine",
                    tint = colors.buttonText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Title & subtext header
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = TulsiScreenPadding, end = TulsiScreenPadding, top = 8.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = todayFormatted,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = progressSubtext,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                }
            }

            // Restoring from Cloud Loading Banner
            if (uiState.isRestoringData) {
                item(key = "restoring_banner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TulsiScreenPadding, vertical = 6.dp)
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(TulsiCornerRadius))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Restoring your data...",
                            fontSize = 14.sp,
                            color = Color(0xFFDDDDDD),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Restoration Error Banner with Retry
            if (uiState.restorationError != null) {
                item(key = "restoration_error_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TulsiScreenPadding, vertical = 6.dp)
                            .background(Color(0xFF2B1A1A), RoundedCornerShape(TulsiCornerRadius))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.restorationError,
                            fontSize = 13.sp,
                            color = Color(0xFFFF9999),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TulsiButton(
                            text = "Retry",
                            onClick = onRetry
                        )
                    }
                }
            }

            // Admin Person Filter Chips
            if (uiState.isAdmin && uiState.members.isNotEmpty()) {
                item(key = "admin_filter") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = TulsiScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PersonChip(
                                label = "Everyone",
                                isSelected = uiState.selectedMemberId == null,
                                onClick = { onSelectMember(null) }
                            )
                        }
                        items(uiState.members, key = { it.userId }) { member ->
                            PersonChip(
                                label = member.displayName.ifBlank { "Member" },
                                isSelected = uiState.selectedMemberId == member.userId,
                                onClick = { onSelectMember(member.userId) }
                            )
                        }
                    }
                }
            }

            // Empty State
            if (filteredDoses.isEmpty()) {
                item(key = "empty_state") {
                    Spacer(modifier = Modifier.height(48.dp))
                    TulsiEmptyState(
                        title = "No doses scheduled",
                        message = "All doses for today have been taken or there are no medicines scheduled.",
                        actionButton = {
                            TulsiButton(
                                text = "Add a medicine",
                                onClick = onNavigateToAddMedicine
                            )
                        }
                    )
                }
            } else {
                // Render doses grouped by time of day (Morning, Afternoon, Evening)
                groupedDoses.forEach { (timeSlot, doses) ->
                    item(key = "section_$timeSlot") {
                        TulsiSectionHeader(title = timeSlot)
                    }

                    items(doses, key = { it.id }) { doseEvent ->
                        val isPending = doseEvent.status == DoseStatus.PENDING
                        val isTaken = doseEvent.status == DoseStatus.TAKEN
                        val timeStr = formatDoseTime(doseEvent.scheduledAt)

                        TulsiListRow(
                            title = doseEvent.medicineName,
                            subtitle = "${doseEvent.dosage} · $timeStr",
                            leadingContent = {
                                if (isTaken) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(colors.accent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Taken",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    TulsiStatusDot(
                                        status = doseEvent.status,
                                        showLabel = false,
                                        dotSize = 10.dp
                                    )
                                }
                            },
                            trailingContent = {
                                if (isPending) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TulsiTextButton(
                                            text = "Skip",
                                            onClick = { onMarkSkipped(doseEvent.id) },
                                            textColor = colors.textSecondary
                                        )
                                        TulsiButton(
                                            text = "Take",
                                            onClick = { onMarkTaken(doseEvent.id) }
                                        )
                                    }
                                } else {
                                    TulsiStatusDot(
                                        status = doseEvent.status,
                                        showLabel = true
                                    )
                                }
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
private fun PersonChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = TulsiTheme.colors

    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) colors.buttonPrimary else Color.Transparent,
                shape = RoundedCornerShape(TulsiCornerRadius)
            )
            .border(
                width = TulsiBorderWidth,
                color = if (isSelected) colors.buttonPrimary else colors.border,
                shape = RoundedCornerShape(TulsiCornerRadius)
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = if (isSelected) colors.buttonText else colors.textPrimary
        )
    }
}

private fun groupDosesByTimeOfDay(doses: List<DoseEvent>): Map<String, List<DoseEvent>> {
    val morning = mutableListOf<DoseEvent>()
    val afternoon = mutableListOf<DoseEvent>()
    val evening = mutableListOf<DoseEvent>()

    val calendar = Calendar.getInstance()
    doses.sortedBy { it.scheduledAt }.forEach { dose ->
        calendar.timeInMillis = dose.scheduledAt
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> morning.add(dose)
            hour < 17 -> afternoon.add(dose)
            else -> evening.add(dose)
        }
    }

    val result = mutableMapOf<String, List<DoseEvent>>()
    if (morning.isNotEmpty()) result["Morning"] = morning
    if (afternoon.isNotEmpty()) result["Afternoon"] = afternoon
    if (evening.isNotEmpty()) result["Evening"] = evening
    return result
}

private fun formatDoseTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

// -----------------------------------------------------------------------------
// Previews: Light, Dark, 1.5x Font Scale
// -----------------------------------------------------------------------------

@Preview(name = "Today - Light", showBackground = true)
@Composable
fun HomeScreenPreviewLight() {
    TulsiTheme(darkTheme = false) {
        HomeScreenContent(
            uiState = getSampleHomeUiState(),
            onNavigateToAddMedicine = {},
            onSelectMember = {},
            onMarkTaken = {},
            onMarkSkipped = {}
        )
    }
}

@Preview(name = "Today - Dark", showBackground = true)
@Composable
fun HomeScreenPreviewDark() {
    TulsiTheme(darkTheme = true) {
        HomeScreenContent(
            uiState = getSampleHomeUiState(),
            onNavigateToAddMedicine = {},
            onSelectMember = {},
            onMarkTaken = {},
            onMarkSkipped = {}
        )
    }
}

@Preview(name = "Today - 1.5x Font Scale", fontScale = 1.5f, showBackground = true)
@Composable
fun HomeScreenPreviewLargeFont() {
    TulsiTheme(darkTheme = false) {
        HomeScreenContent(
            uiState = getSampleHomeUiState(),
            onNavigateToAddMedicine = {},
            onSelectMember = {},
            onMarkTaken = {},
            onMarkSkipped = {}
        )
    }
}

private fun getSampleHomeUiState(): HomeUiState {
    val now = System.currentTimeMillis()
    return HomeUiState(
        todayDoses = listOf(
            DoseEvent(id = "1", medicineName = "Metformin", dosage = "500mg", scheduledAt = now, status = DoseStatus.PENDING),
            DoseEvent(id = "2", medicineName = "Aspirin", dosage = "75mg", scheduledAt = now, status = DoseStatus.TAKEN),
            DoseEvent(id = "3", medicineName = "Atorvastatin", dosage = "20mg", scheduledAt = now + 36000000L, status = DoseStatus.PENDING)
        ),
        isAdmin = true,
        members = listOf(
            GroupMember(userId = "1", displayName = "Asha", role = MemberRole.ADMIN),
            GroupMember(userId = "2", displayName = "Ravi", role = MemberRole.MEMBER)
        ),
        isLoading = false
    )
}
