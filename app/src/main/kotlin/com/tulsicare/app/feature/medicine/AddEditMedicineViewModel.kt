package com.tulsicare.app.feature.medicine

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.FamilyGroupRepository
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.data.repository.ScheduleRepository
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.Medicine
import com.tulsicare.app.core.model.MedicineForm
import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.model.ScheduleType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddEditMedicineUiState(
    val medicineId: String? = null,
    val groupId: String = "",
    val name: String = "",
    val dosage: String = "",
    val form: MedicineForm = MedicineForm.PILL,
    val instructions: String = "",
    val colorHex: String = "#0F9D8A",
    val escalationWindowMinutes: Int = 30,
    val assignedMemberId: String = "",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val timesOfDay: List<String> = listOf("08:00", "20:00"),
    val intervalHours: Int = 8,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val availableMembers: List<GroupMember> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddEditMedicineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val medicineRepository: MedicineRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
    private val familyGroupRepository: FamilyGroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditMedicineUiState())
    val uiState: StateFlow<AddEditMedicineUiState> = _uiState.asStateFlow()

    private var existingScheduleId: String? = null

    init {
        val medicineId = savedStateHandle.get<String>("medicineId")
        val initialGroupId = savedStateHandle.get<String>("groupId").orEmpty()
        initData(medicineId, initialGroupId)
    }

    fun resetForm(medicineId: String? = null, initialGroupId: String = "") {
        existingScheduleId = null
        _uiState.value = AddEditMedicineUiState()
        initData(medicineId, initialGroupId)
    }

    private fun initData(medicineId: String?, initialGroupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = authRepository.getCurrentUser()
            val rawGroupId = if (initialGroupId.isNotBlank()) initialGroupId else user?.currentGroupId.orEmpty()
            val groupId = rawGroupId.ifBlank { "default_group" }
            val userId = user?.id.orEmpty()

            val members = if (rawGroupId.isNotBlank()) {
                familyGroupRepository.observeMembers(rawGroupId).firstOrNull() ?: emptyList()
            } else emptyList()

            _uiState.update {
                it.copy(
                    groupId = groupId,
                    assignedMemberId = userId,
                    availableMembers = members,
                    isLoading = false
                )
            }

            if (!medicineId.isNullOrBlank()) {
                loadExistingMedicine(medicineId)
            }
        }
    }

    private suspend fun loadExistingMedicine(id: String) {
        val medicine = medicineRepository.getMedicineById(id) ?: return
        val schedules = scheduleRepository.getSchedulesForMedicine(id)
        val schedule = schedules.firstOrNull()
        existingScheduleId = schedule?.id

        _uiState.update {
            it.copy(
                medicineId = medicine.id,
                groupId = medicine.groupId,
                name = medicine.name,
                dosage = medicine.dosage,
                form = medicine.form,
                instructions = medicine.instructions,
                colorHex = medicine.colorHex ?: "#0F9D8A",
                escalationWindowMinutes = medicine.escalationWindowMinutes,
                assignedMemberId = medicine.assignedMemberId,
                scheduleType = schedule?.type ?: ScheduleType.DAILY,
                daysOfWeek = schedule?.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7),
                timesOfDay = schedule?.timesOfDay ?: listOf("08:00"),
                intervalHours = schedule?.intervalHours ?: 8,
                startDate = schedule?.startDate ?: System.currentTimeMillis(),
                endDate = schedule?.endDate
            )
        }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(name = name, errorMessage = null) }
    fun onDosageChanged(dosage: String) = _uiState.update { it.copy(dosage = dosage) }
    fun onFormChanged(form: MedicineForm) = _uiState.update { it.copy(form = form) }
    fun onInstructionsChanged(instructions: String) = _uiState.update { it.copy(instructions = instructions) }
    fun onColorChanged(colorHex: String) = _uiState.update { it.copy(colorHex = colorHex) }
    fun onEscalationChanged(minutes: Int) = _uiState.update { it.copy(escalationWindowMinutes = minutes) }
    fun onMemberSelected(memberId: String) = _uiState.update { it.copy(assignedMemberId = memberId) }
    fun onScheduleTypeChanged(type: ScheduleType) = _uiState.update { it.copy(scheduleType = type) }

    fun toggleDayOfWeek(day: Int) {
        _uiState.update { state ->
            val current = state.daysOfWeek.toMutableList()
            if (current.contains(day)) {
                if (current.size > 1) current.remove(day) // Keep at least one day
            } else {
                current.add(day)
                current.sort()
            }
            state.copy(daysOfWeek = current)
        }
    }

    fun addTime(time: String) {
        _uiState.update { state ->
            if (!state.timesOfDay.contains(time)) {
                val updated = (state.timesOfDay + time).sorted()
                state.copy(timesOfDay = updated)
            } else state
        }
    }

    fun removeTime(time: String) {
        _uiState.update { state ->
            if (state.timesOfDay.size > 1) {
                state.copy(timesOfDay = state.timesOfDay.filter { it != time })
            } else state
        }
    }

    fun setIntervalHours(hours: Int) = _uiState.update { it.copy(intervalHours = hours) }

    fun saveMedicine() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter the medicine name") }
            return
        }
        if (state.scheduleType != ScheduleType.AS_NEEDED && state.timesOfDay.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please add at least one reminder time") }
            return
        }
        val effectiveGroupId = state.groupId.ifBlank { "default_group" }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val now = System.currentTimeMillis()
            val medicineId = state.medicineId ?: UUID.randomUUID().toString()

            val medicine = Medicine(
                id = medicineId,
                groupId = effectiveGroupId,
                assignedMemberId = state.assignedMemberId,
                name = state.name.trim(),
                dosage = state.dosage.trim(),
                form = state.form,
                instructions = state.instructions.trim(),
                colorHex = state.colorHex,
                escalationWindowMinutes = state.escalationWindowMinutes,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            val scheduleId = existingScheduleId ?: UUID.randomUUID().toString()
            val schedule = Schedule(
                id = scheduleId,
                groupId = medicine.groupId,
                medicineId = medicineId,
                type = state.scheduleType,
                daysOfWeek = state.daysOfWeek,
                intervalHours = if (state.scheduleType == ScheduleType.INTERVAL) state.intervalHours else null,
                timesOfDay = state.timesOfDay,
                startDate = state.startDate,
                endDate = state.endDate,
                isActive = true
            )

            // 1. Save medicine (confirmed written to Firestore or offline queue)
            val medResult = if (state.medicineId == null) {
                medicineRepository.addMedicine(medicine)
            } else {
                medicineRepository.updateMedicine(medicine)
            }
            if (medResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to save medicine: ${medResult.exceptionOrNull()?.message ?: "Write failed"}"
                    )
                }
                return@launch
            }

            // 2. Save schedule
            val schedResult = if (existingScheduleId == null) {
                scheduleRepository.addSchedule(schedule)
            } else {
                scheduleRepository.updateSchedule(schedule)
            }
            if (schedResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to save schedule: ${schedResult.exceptionOrNull()?.message ?: "Write failed"}"
                    )
                }
                return@launch
            }

            // 3. Generate today's dose events
            doseEventRepository.generateTodayDosesIfMissing(medicine.groupId)

            // 4. Reschedule local exact alarms
            ExactAlarmScheduler.rescheduleAll(context)

            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    fun deleteMedicine() {
        val state = _uiState.value
        val medId = state.medicineId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            medicineRepository.deleteMedicine(state.groupId, medId)
            existingScheduleId?.let { scheduleRepository.deleteSchedule(state.groupId, it) }
            ExactAlarmScheduler.rescheduleAll(context)
            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
