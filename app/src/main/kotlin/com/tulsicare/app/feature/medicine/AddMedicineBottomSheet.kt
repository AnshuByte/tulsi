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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tulsicare.app.core.model.MedicineForm
import com.tulsicare.app.core.model.ScheduleType
import kotlinx.coroutines.launch
import java.util.Locale

val TulsiGreen = Color(0xFF3ECF8E)
val TulsiFieldBg = Color(0xFF222222)
val TulsiCardDark = Color(0xFF181818)
val TulsiSubtleBorder = Color(0xFF333333)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMedicineBottomSheet(
    viewModel: AddEditMedicineViewModel,
    onDismiss: () -> Unit,
    onSaveSuccess: (medicineName: String, nextDoseTime: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var showFormDropdown by remember { mutableStateOf(false) }
    var showInstructionsField by remember { mutableStateOf(false) }
    var showTimePickerModal by remember { mutableStateOf(false) }
    var showInlineNameError by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            val nextDose = uiState.timesOfDay.firstOrNull() ?: "scheduled"
            onSaveSuccess(uiState.name, nextDose)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = TulsiCardDark,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF555555),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .imePadding()
        ) {
            // Header Row: Title + Dismiss "X"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add medicine",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Medicine Name Field
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(TulsiFieldBg, RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (showInlineNameError) Color(0xFFFF5252) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (uiState.name.isEmpty()) {
                            Text(
                                text = "Medicine name",
                                color = Color(0xFF777777),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = uiState.name,
                            onValueChange = {
                                viewModel.onNameChanged(it)
                                if (it.isNotBlank()) showInlineNameError = false
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(TulsiGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (showInlineNameError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter a medicine name",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Member Selector: "Who is this medicine for?"
                if (uiState.availableMembers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Who is this medicine for?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF888888)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.availableMembers.forEach { member ->
                                val isSelected = uiState.assignedMemberId == member.userId
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color.White else Color(0xFF222222))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.White else Color(0xFF333333),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { viewModel.onMemberSelected(member.userId) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.displayName.ifBlank { "Member" },
                                        color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Dosage + Form Dropdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dosage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(TulsiFieldBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (uiState.dosage.isEmpty()) {
                            Text(
                                text = "Dosage",
                                color = Color(0xFF777777),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = uiState.dosage,
                            onValueChange = viewModel::onDosageChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(TulsiGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Form Dropdown
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(TulsiFieldBg, RoundedCornerShape(12.dp))
                            .clickable { showFormDropdown = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.form.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Form",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFormDropdown,
                            onDismissRequest = { showFormDropdown = false },
                            modifier = Modifier.background(Color(0xFF262626))
                        ) {
                            MedicineForm.entries.forEach { form ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = form.name.lowercase().replaceFirstChar { it.uppercase() },
                                            color = if (form == uiState.form) TulsiGreen else Color.White
                                        )
                                    },
                                    onClick = {
                                        viewModel.onFormChanged(form)
                                        showFormDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Segmented Control: Daily / Days / Every X h / As needed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        ScheduleType.DAILY to "Daily",
                        ScheduleType.SPECIFIC_DAYS to "Days",
                        ScheduleType.INTERVAL to "Every X h",
                        ScheduleType.AS_NEEDED to "As needed"
                    )

                    tabs.forEach { (type, label) ->
                        val isSelected = uiState.scheduleType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { viewModel.onScheduleTypeChanged(type) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else Color(0xFF999999)
                            )
                        }
                    }
                }

                // Days Picker (Visible only when Days is selected)
                if (uiState.scheduleType == ScheduleType.SPECIFIC_DAYS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
                        (1..7).forEach { day ->
                            val isSelected = uiState.daysOfWeek.contains(day)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color(0xFF222222))
                                    .clickable { viewModel.toggleDayOfWeek(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNames[day - 1],
                                    color = if (isSelected) Color.Black else Color(0xFF888888),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Interval Picker (Visible only when Every X h is selected)
                if (uiState.scheduleType == ScheduleType.INTERVAL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(4, 6, 8, 12).forEach { hours ->
                            val isSelected = uiState.intervalHours == hours
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else Color(0xFF222222))
                                    .clickable { viewModel.setIntervalHours(hours) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${hours}h",
                                    color = if (isSelected) Color.Black else Color(0xFF888888),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 4. Reminder Times Section
                if (uiState.scheduleType != ScheduleType.AS_NEEDED) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Reminder times",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF888888)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.timesOfDay.forEach { time ->
                                Row(
                                    modifier = Modifier
                                        .height(38.dp)
                                        .background(Color(0xFF222222), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccessTime,
                                        contentDescription = null,
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = time,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (uiState.timesOfDay.size > 1) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $time",
                                            tint = Color(0xFF888888),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.removeTime(time) }
                                        )
                                    }
                                }
                            }

                            // "+ Add" Chip
                            Row(
                                modifier = Modifier
                                    .height(38.dp)
                                    .background(Color(0xFF14241B), RoundedCornerShape(10.dp))
                                    .border(1.dp, TulsiGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable { showTimePickerModal = true }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add time",
                                    tint = TulsiGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Add",
                                    color = TulsiGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 5. Expandable Instructions Section
                Column {
                    if (!showInstructionsField && uiState.instructions.isBlank()) {
                        Text(
                            text = "+ Add instructions",
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { showInstructionsField = true }
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(TulsiFieldBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (uiState.instructions.isEmpty()) {
                                Text(
                                    text = "Instructions (e.g., Take with food)",
                                    color = Color(0xFF777777),
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.instructions,
                                onValueChange = viewModel::onInstructionsChanged,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(TulsiGreen),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 6. Family Alert Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF16251E))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = TulsiGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Alert\nfamily after",
                                color = TulsiGreen,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Segmented control: 15m / 30m / 45m / 60m
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                val isSelected = uiState.escalationWindowMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { viewModel.onEscalationChanged(mins) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (isSelected) Color.Black else Color(0xFF999999),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                if (!uiState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Pinned Action Button at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState.name.trim().isBlank()) {
                            showInlineNameError = true
                            return@Button
                        }
                        viewModel.saveMedicine()
                    },
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TulsiGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (uiState.isLoading) "Saving..." else "Save and set alarms",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }

    // TimePicker Modal Dialog
    if (showTimePickerModal) {
        TimePickerModal(
            onDismiss = { showTimePickerModal = false },
            onConfirm = { hour, minute ->
                val formatted = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                viewModel.addTime(formatted)
                showTimePickerModal = false
            }
        )
    }
}
