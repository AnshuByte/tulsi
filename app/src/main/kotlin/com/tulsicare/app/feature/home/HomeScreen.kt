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
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiButton
import com.tulsicare.app.core.designsystem.component.TulsiListRow
import com.tulsicare.app.core.designsystem.component.TulsiSectionHeader
import com.tulsicare.app.core.designsystem.component.TulsiStatusDot
import com.tulsicare.app.core.designsystem.component.TulsiTextButton
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.feature.medicine.AddEditMedicineViewModel
import com.tulsicare.app.feature.medicine.AddMedicineBottomSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TulsiGreen = Color(0xFF3ECF8E)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddMedicine: () -> Unit = {},
    onNavigateToEditMedicine: (medicineId: String) -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addMedViewModel: AddEditMedicineViewModel = hiltViewModel()
    var showAddMedicineSheet by remember { mutableStateOf(false) }
    var showFamilyPromptDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openAddMedicineSheet() {
        addMedViewModel.resetForm(initialGroupId = uiState.currentGroup?.id.orEmpty())
        showAddMedicineSheet = true
    }

    HomeScreenContent(
        uiState = uiState,
        onOpenAddMedicine = ::openAddMedicineSheet,
        onSelectMember = viewModel::selectMember,
        onMarkTaken = viewModel::markDoseTaken,
        onMarkSkipped = viewModel::markDoseSkipped,
        onRetry = viewModel::loadData,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )

    if (showAddMedicineSheet) {
        AddMedicineBottomSheet(
            viewModel = addMedViewModel,
            onDismiss = { showAddMedicineSheet = false },
            onSaveSuccess = { medicineName, nextDoseTime ->
                showAddMedicineSheet = false
                addMedViewModel.resetForm(initialGroupId = uiState.currentGroup?.id.orEmpty())
                viewModel.loadData()
                val doseMsg = if (nextDoseTime.isNotBlank() && nextDoseTime != "scheduled") "at $nextDoseTime" else "scheduled"
                scope.launch {
                    snackbarHostState.showSnackbar("$medicineName added. Next dose $doseMsg.")
                }
                if (uiState.members.isEmpty() || uiState.currentGroup == null) {
                    showFamilyPromptDialog = true
                }
            }
        )
    }

    if (showFamilyPromptDialog) {
        AlertDialog(
            onDismissRequest = { showFamilyPromptDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Family Alerts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Add a family member to get missed-dose alerts.",
                    fontSize = 14.sp,
                    color = Color(0xFFCCCCCC)
                )
            },
            confirmButton = {
                TulsiButton(
                    text = "Go to Family",
                    onClick = {
                        showFamilyPromptDialog = false
                        onNavigateToFamily()
                    }
                )
            },
            dismissButton = {
                TulsiTextButton(
                    text = "Dismiss",
                    onClick = { showFamilyPromptDialog = false }
                )
            }
        )
    }
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onOpenAddMedicine: () -> Unit,
    onSelectMember: (String?) -> Unit,
    onMarkTaken: (String) -> Unit,
    onMarkSkipped: (String) -> Unit,
    onRetry: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
    val allDosesTaken = totalDoses > 0 && takenDoses == totalDoses

    // Subtitle logic
    val progressSubtext = when {
        totalDoses > 0 -> "$takenDoses of $totalDoses taken"
        uiState.currentGroup == null -> "Family circle: not set up"
        else -> ""
    }

    // Group doses into Morning, Afternoon, Evening
    val groupedDoses = groupDosesByTimeOfDay(filteredDoses)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Show FAB ONLY when medicines/doses exist
            if (filteredDoses.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onOpenAddMedicine,
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Date Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TulsiScreenPadding, end = TulsiScreenPadding, top = 12.dp, bottom = 8.dp)
            ) {
                if (progressSubtext.isNotBlank() && totalDoses == 0) {
                    Text(
                        text = progressSubtext,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = todayFormatted,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )

                if (totalDoses > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progressSubtext,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                }
            }

            // Restoring / Syncing Banner
            if (uiState.isRestoringData) {
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

            // Restoration Error Banner with Retry
            if (uiState.restorationError != null) {
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

            // Admin Person Filter Chips
            if (uiState.isAdmin && uiState.members.isNotEmpty() && totalDoses > 0) {
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

            // Content Area: Empty State OR Dose List
            if (filteredDoses.isEmpty()) {
                // Calm, vertically centered empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(TulsiScreenPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Circular pill badge
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF16251E), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Medication,
                                contentDescription = null,
                                tint = TulsiGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "No medicines yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add your first medicine to get reminders.",
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        TulsiButton(
                            text = "Add a medicine",
                            onClick = onOpenAddMedicine,
                            modifier = Modifier.width(180.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // "All done for today" check banner
                    if (allDosesTaken) {
                        item(key = "all_done_banner") {
                            val firstDoseTime = filteredDoses.firstOrNull()?.let { formatDoseTime(it.scheduledAt) } ?: "08:00"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = TulsiScreenPadding, vertical = 8.dp)
                                    .background(Color(0xFF15221B), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(TulsiGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "All done for today",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Next: $firstDoseTime tomorrow",
                                        fontSize = 13.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        }
                    }

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
            .background(
                color = if (isSelected) colors.buttonPrimary else colors.surface,
                shape = RoundedCornerShape(TulsiCornerRadius)
            )
            .border(
                width = TulsiBorderWidth,
                color = if (isSelected) colors.buttonPrimary else colors.border,
                shape = RoundedCornerShape(TulsiCornerRadius)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) colors.buttonText else colors.textPrimary
        )
    }
}

private fun groupDosesByTimeOfDay(doses: List<DoseEvent>): Map<String, List<DoseEvent>> {
    val sorted = doses.sortedBy { it.scheduledAt }
    val grouped = linkedMapOf<String, MutableList<DoseEvent>>()

    for (dose in sorted) {
        val calendar = Calendar.getInstance().apply { timeInMillis = dose.scheduledAt }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeSlot = when {
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            else -> "Evening"
        }
        grouped.getOrPut(timeSlot) { mutableListOf() }.add(dose)
    }

    return grouped
}

private fun formatDoseTime(timestamp: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}
