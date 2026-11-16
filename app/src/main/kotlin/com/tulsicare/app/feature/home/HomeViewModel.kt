package com.tulsicare.app.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsicare.app.core.alarm.AlarmReceiver
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.FamilyGroupRepository
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.Medicine
import com.tulsicare.app.core.model.User
import com.tulsicare.app.core.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val currentUser: User? = null,
    val currentGroup: FamilyGroup? = null,
    val todayDoses: List<DoseEvent> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val isSimpleMode: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyGroupRepository: FamilyGroupRepository,
    private val medicineRepository: MedicineRepository,
    private val doseEventRepository: DoseEventRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.isSimpleMode.collectLatest { enabled ->
                _uiState.update { it.copy(isSimpleMode = enabled) }
            }
        }
    }

    fun toggleSimpleMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSimpleMode(enabled)
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Ensure user exists (sign in anonymously if not authenticated yet)
            var user = authRepository.getCurrentUser()
            if (user == null) {
                user = authRepository.signInAnonymously().getOrNull()
            }
            _uiState.update { it.copy(currentUser = user) }

            val groupId = user?.currentGroupId.orEmpty().ifBlank { "my_family" }

            // Load group
            val group = familyGroupRepository.getGroup(groupId)
            _uiState.update { it.copy(currentGroup = group) }

            // Generate today's doses if needed
            doseEventRepository.generateTodayDosesIfMissing(groupId)

            // Start observing today's doses & active medicines
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startOfDay = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val endOfDay = calendar.timeInMillis

            launch {
                doseEventRepository.observeEventsForDateRange(groupId, startOfDay, endOfDay)
                    .collectLatest { events ->
                        _uiState.update { it.copy(todayDoses = events) }
                    }
            }

            launch {
                medicineRepository.observeActiveMedicines(groupId)
                    .collectLatest { meds ->
                        _uiState.update { it.copy(medicines = meds, isLoading = false) }
                    }
            }
        }
    }

    fun markDoseTaken(eventId: String) {
        val groupId = _uiState.value.currentGroup?.id ?: _uiState.value.currentUser?.currentGroupId.orEmpty().ifBlank { "my_family" }
        val userId = _uiState.value.currentUser?.id.orEmpty()
        viewModelScope.launch {
            doseEventRepository.recordDoseAction(groupId, eventId, DoseStatus.TAKEN, userId)
        }
    }

    fun markDoseSkipped(eventId: String) {
        val groupId = _uiState.value.currentGroup?.id ?: _uiState.value.currentUser?.currentGroupId.orEmpty().ifBlank { "my_family" }
        val userId = _uiState.value.currentUser?.id.orEmpty()
        viewModelScope.launch {
            doseEventRepository.recordDoseAction(groupId, eventId, DoseStatus.SKIPPED, userId)
        }
    }

    fun triggerTestAlarm(context: Context) {
        val firstMed = _uiState.value.medicines.firstOrNull()
        val medId = firstMed?.id ?: "test_med"
        val medName = firstMed?.name ?: "Metformin"
        val dosage = firstMed?.dosage ?: "500 mg"

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, "test_schedule")
        }
        context.sendBroadcast(intent)
    }
}
