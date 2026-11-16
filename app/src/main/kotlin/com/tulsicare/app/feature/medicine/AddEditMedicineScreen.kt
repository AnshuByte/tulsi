package com.tulsicare.app.feature.medicine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.model.MedicineForm
import com.tulsicare.app.core.model.ScheduleType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicineScreen(
    viewModel: AddEditMedicineViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    var showTimePickerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.medicineId == null) "Add Medicine" else "Edit Medicine",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.secondary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TulsiTheme.colors.textPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Section 1: Basic Info
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Medicine Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = viewModel::onNameChanged,
                            label = { Text("Medicine Name") },
                            placeholder = { Text("e.g. Metformin, Amlodipine") },
                            leadingIcon = {
                                Icon(Icons.Default.Medication, contentDescription = null, tint = TulsiTheme.colors.textPrimary)
                            },
                            isError = uiState.errorMessage != null && uiState.name.isBlank(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.dosage,
                            onValueChange = viewModel::onDosageChanged,
                            label = { Text("Dosage / Strength") },
                            placeholder = { Text("e.g. 500 mg, 1 tablet, 5 ml") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Medicine Form Chips
                        Text(
                            text = "Form",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MedicineFormSelector(
                            selectedForm = uiState.form,
                            onFormSelected = viewModel::onFormChanged
                        )

                        OutlinedTextField(
                            value = uiState.instructions,
                            onValueChange = viewModel::onInstructionsChanged,
                            label = { Text("Instructions (Optional)") },
                            placeholder = { Text("e.g. Take with a glass of water after food") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 2: Family Assignment (if available)
                if (uiState.availableMembers.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Who takes this medicine?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            MemberSelector(
                                members = uiState.availableMembers,
                                selectedMemberId = uiState.assignedMemberId,
                                onMemberSelected = viewModel::onMemberSelected
                            )
                        }
                    }
                }

                // Section 3: Schedule & Reminder Times
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Schedule & Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        // Schedule Type selector
                        ScheduleTypeSelector(
                            selectedType = uiState.scheduleType,
                            onTypeSelected = viewModel::onScheduleTypeChanged
                        )

                        // Specific Days Selector
                        if (uiState.scheduleType == ScheduleType.SPECIFIC_DAYS) {
                            DaysOfWeekSelector(
                                selectedDays = uiState.daysOfWeek,
                                onToggleDay = viewModel::toggleDayOfWeek
                            )
                        }

                        // Reminder Times List
                        if (uiState.scheduleType != ScheduleType.AS_NEEDED) {
                            Text(
                                text = "Reminder Times",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            ReminderTimesList(
                                times = uiState.timesOfDay,
                                onAddTimeClick = { showTimePickerDialog = true },
                                onRemoveTime = viewModel::removeTime
                            )
                        }
                    }
                }

                // Section 4: Family Escalation Window
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = TulsiTheme.colors.textPrimary)
                            Text(
                                text = "Family Alert Window",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Text(
                            text = "If no action is recorded after this duration, your family circle receives an instant notification to follow up.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        EscalationWindowSelector(
                            selectedMinutes = uiState.escalationWindowMinutes,
                            onMinutesSelected = viewModel::onEscalationChanged
                        )
                    }
                }

                // Error Message Display
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Save Button
                Button(
                    onClick = viewModel::saveMedicine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TulsiTheme.colors.buttonPrimary, contentColor = TulsiTheme.colors.buttonText),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.medicineId == null) "Save Medicine & Set Alarms" else "Save Changes",
                        style = MaterialTheme.typography.titleMedium,
                        color = TulsiTheme.colors.buttonText
                    )
                }

                // Delete Button (if editing)
                if (uiState.medicineId != null) {
                    OutlinedButton(
                        onClick = viewModel::deleteMedicine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TulsiTheme.colors.statusMissed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete Medicine",
                            style = MaterialTheme.typography.titleMedium,
                            color = TulsiTheme.colors.statusMissed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Time Picker Dialog
    if (showTimePickerDialog) {
        TimePickerModal(
            onDismiss = { showTimePickerDialog = false },
            onConfirm = { hour, minute ->
                val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
                viewModel.addTime(formatted)
                showTimePickerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineFormSelector(
    selectedForm: MedicineForm,
    onFormSelected: (MedicineForm) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MedicineForm.entries.forEach { form ->
            val isSelected = form == selectedForm
            FilterChip(
                selected = isSelected,
                onClick = { onFormSelected(form) },
                label = { Text(form.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TulsiTheme.colors.buttonPrimary,
                    selectedLabelColor = TulsiTheme.colors.buttonText
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberSelector(
    members: List<com.tulsicare.app.core.model.GroupMember>,
    selectedMemberId: String,
    onMemberSelected: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        members.forEach { member ->
            val isSelected = member.userId == selectedMemberId
            FilterChip(
                selected = isSelected,
                onClick = { onMemberSelected(member.userId) },
                label = { Text(member.displayName) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TulsiTheme.colors.buttonPrimary,
                    selectedLabelColor = TulsiTheme.colors.buttonText
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleTypeSelector(
    selectedType: ScheduleType,
    onTypeSelected: (ScheduleType) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScheduleType.entries.forEach { type ->
            val isSelected = type == selectedType
            val label = when (type) {
                ScheduleType.DAILY -> "Every Day"
                ScheduleType.SPECIFIC_DAYS -> "Specific Days"
                ScheduleType.INTERVAL -> "Every X Hours"
                ScheduleType.AS_NEEDED -> "As Needed"
            }
            FilterChip(
                selected = isSelected,
                onClick = { onTypeSelected(type) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TulsiTheme.colors.buttonPrimary,
                    selectedLabelColor = TulsiTheme.colors.buttonText
                )
            )
        }
    }
}

@Composable
fun DaysOfWeekSelector(
    selectedDays: List<Int>,
    onToggleDay: (Int) -> Unit
) {
    val dayLabels = listOf(
        1 to "M",
        2 to "T",
        3 to "W",
        4 to "T",
        5 to "F",
        6 to "S",
        7 to "S"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dayLabels.forEach { (day, label) ->
            val isSelected = selectedDays.contains(day)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) TulsiTheme.colors.buttonPrimary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleDay(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) TulsiTheme.colors.buttonText else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderTimesList(
    times: List<String>,
    onAddTimeClick: () -> Unit,
    onRemoveTime: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        times.forEach { timeStr ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TulsiTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (times.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { onRemoveTime(timeStr) }
                        )
                    }
                }
            }
        }

        // Add Time button chip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = TulsiTheme.colors.surface,
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAddTimeClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = TulsiTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Time",
                    color = TulsiTheme.colors.textPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun EscalationWindowSelector(
    selectedMinutes: Int,
    onMinutesSelected: (Int) -> Unit
) {
    val options = listOf(15, 30, 45, 60)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { minutes ->
            val isSelected = selectedMinutes == minutes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) TulsiTheme.colors.buttonPrimary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onMinutesSelected(minutes) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${minutes}m",
                    color = if (isSelected) TulsiTheme.colors.buttonText else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(is24Hour = false)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Reminder Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                TimePicker(state = timePickerState)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                        colors = ButtonDefaults.buttonColors(containerColor = TulsiTheme.colors.buttonPrimary, contentColor = TulsiTheme.colors.buttonText)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
