package com.tulsicare.app.feature.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.FamilyGroupRepository
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.report.PdfReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HistoryUiState(
    val rangeDays: Int = 7,
    val adherencePercent: Int = 100,
    val streakDays: Int = 0,
    val totalDoses: Int = 0,
    val takenDoses: Int = 0,
    val skippedDoses: Int = 0,
    val missedDoses: Int = 0,
    val events: List<DoseEvent> = emptyList(),
    val patientName: String = "Patient",
    val groupName: String = "Family Circle",
    val isLoading: Boolean = false,
    val isExportingPdf: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val doseEventRepository: DoseEventRepository,
    private val familyGroupRepository: FamilyGroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory(7)
    }

    fun loadHistory(days: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, rangeDays = days) }

            val user = authRepository.getCurrentUser()
            val groupId = user?.currentGroupId.orEmpty().ifBlank { "my_family" }
            val group = familyGroupRepository.getGroup(groupId)

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -days)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startMillis = cal.timeInMillis

            val allGroupEvents = doseEventRepository.getEventsForDateRange(groupId, startMillis, now)
            // Ensure patient data isolation: report contains only the active member's doses
            val events = if (user != null && user.id.isNotBlank()) {
                allGroupEvents.filter { it.assignedMemberId == user.id || it.assignedMemberId.isBlank() }
            } else {
                allGroupEvents
            }

            val total = events.size
            val taken = events.count { it.status == DoseStatus.TAKEN }
            val skipped = events.count { it.status == DoseStatus.SKIPPED }
            val missed = events.count { it.status == DoseStatus.MISSED || it.status == DoseStatus.PENDING }

            val adherence = if (total > 0) ((taken.toDouble() / total) * 100).toInt() else 100
            val streak = calculateStreakDays(events)

            _uiState.update {
                it.copy(
                    adherencePercent = adherence,
                    streakDays = streak,
                    totalDoses = total,
                    takenDoses = taken,
                    skippedDoses = skipped,
                    missedDoses = missed,
                    events = events.sortedByDescending { e -> e.scheduledAt },
                    patientName = user?.displayName?.ifBlank { "Patient" } ?: "Patient",
                    groupName = group?.name ?: "Family Circle",
                    isLoading = false
                )
            }
        }
    }

    private fun calculateStreakDays(events: List<DoseEvent>): Int {
        if (events.isEmpty()) return 0
        val groupedByDay = events.groupBy { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.scheduledAt }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }

        var streak = 0
        for ((_, dayEvents) in groupedByDay.entries.reversed()) {
            val allTaken = dayEvents.all { it.status == DoseStatus.TAKEN }
            if (allTaken) streak++
            else break
        }
        return streak
    }

    fun exportPdf(context: Context) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingPdf = true) }
            try {
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -state.rangeDays)
                }

                val pdfFile = PdfReportGenerator.generateReport(
                    context = context,
                    patientName = state.patientName,
                    groupName = state.groupName,
                    startDate = cal.timeInMillis,
                    endDate = now,
                    adherencePercent = state.adherencePercent,
                    streakDays = state.streakDays,
                    events = state.events
                )

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "tulsi Medication Adherence Report - ${state.patientName}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Adherence Report (PDF)")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isExportingPdf = false) }
            }
        }
    }
}
