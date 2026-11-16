package com.tulsicare.app.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.tulsicare.app.core.alarm.AlarmPlaybackService
import com.tulsicare.app.core.alarm.AlarmReceiver
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.FamilyGroupRepository
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.database.TulsiDatabase
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.Medicine
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.model.User
import com.tulsicare.app.core.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val currentUser: User? = null,
    val currentGroup: FamilyGroup? = null,
    val todayDoses: List<DoseEvent> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val members: List<GroupMember> = emptyList(),
    val selectedMemberId: String? = null, // null means "Everyone"
    val isAdmin: Boolean = false,
    val isSimpleMode: Boolean = false,
    val preReminderLeadMinutes: Int = 10,
    val isLoading: Boolean = true,
    val isRestoringData: Boolean = false,
    val restorationError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val familyGroupRepository: FamilyGroupRepository,
    private val medicineRepository: MedicineRepository,
    private val doseEventRepository: DoseEventRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: TulsiDatabase,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCurrentUser()
        observePreferences()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user == null) {
                    loadDataJob?.cancel()
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            currentGroup = null,
                            todayDoses = emptyList(),
                            medicines = emptyList(),
                            members = emptyList(),
                            isLoading = false,
                            isRestoringData = false,
                            restorationError = null
                        )
                    }
                } else {
                    loadData()
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.isSimpleMode.collectLatest { enabled ->
                _uiState.update { it.copy(isSimpleMode = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preReminderLeadMinutes.collectLatest { minutes ->
                _uiState.update { it.copy(preReminderLeadMinutes = minutes) }
            }
        }
    }

    fun toggleSimpleMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSimpleMode(enabled)
        }
    }

    fun setPreReminderLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPreReminderLeadMinutes(minutes)
            ExactAlarmScheduler.rescheduleAll(context)
        }
    }

    fun selectMember(memberId: String?) {
        _uiState.update { it.copy(selectedMemberId = memberId) }
    }

    private var loadDataJob: Job? = null

    fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, restorationError = null) }

            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    it.copy(
                        currentUser = null,
                        currentGroup = null,
                        todayDoses = emptyList(),
                        medicines = emptyList(),
                        members = emptyList(),
                        isLoading = false,
                        isRestoringData = false
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(currentUser = user) }

            val groupId = user.currentGroupId.orEmpty()
            if (groupId.isBlank()) {
                _uiState.update {
                    it.copy(
                        currentGroup = null,
                        todayDoses = emptyList(),
                        medicines = emptyList(),
                        members = emptyList(),
                        isLoading = false,
                        isRestoringData = false
                    )
                }
                return@launch
            }

            // -------------------------------------------------------------
            // Login Rehydration (Rule 5):
            // Check if Room is empty for this group. If empty, fetch
            // group, members, medicines, schedules, and doses from Firestore.
            // -------------------------------------------------------------
            val localMeds = database.medicineDao().getActiveMedicines(groupId)
            if (localMeds.isEmpty()) {
                _uiState.update { it.copy(isRestoringData = true) }
                try {
                    // 1. Group & Members
                    val groupDoc = firestore.collection("groups").document(groupId).get().await()
                    val group = groupDoc.toObject(FamilyGroup::class.java)?.copy(id = groupDoc.id)
                    if (group != null) {
                        database.familyGroupDao().insertGroup(group.toEntity())
                    }
                    val membersSnap = firestore.collection("groups").document(groupId).collection("members").get().await()
                    val members = membersSnap.documents.mapNotNull { it.toObject(GroupMember::class.java) }
                    if (members.isNotEmpty()) {
                        database.familyGroupDao().insertMembers(members.map { it.toEntity(groupId) })
                    }

                    // 2. Fetch medicines
                    val medsSnap = firestore.collection("groups").document(groupId).collection("medicines")
                        .whereEqualTo("isActive", true).get().await()
                    val meds = medsSnap.documents.mapNotNull { doc -> doc.toObject(Medicine::class.java)?.copy(id = doc.id) }
                    if (meds.isNotEmpty()) {
                        database.medicineDao().insertMedicines(meds.map { it.toEntity() })
                    }

                    // 3. Fetch schedules
                    val schedSnap = firestore.collection("groups").document(groupId).collection("schedules")
                        .whereEqualTo("isActive", true).get().await()
                    val schedules = schedSnap.documents.mapNotNull { doc -> doc.toObject(Schedule::class.java)?.copy(id = doc.id) }
                    if (schedules.isNotEmpty()) {
                        database.scheduleDao().insertSchedules(schedules.map { it.toEntity() })
                    }

                    // 4. Fetch current day's doses
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startOfDay = calendar.timeInMillis
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    val endOfDay = calendar.timeInMillis

                    val dosesSnap = firestore.collection("groups").document(groupId).collection("doseEvents")
                        .whereGreaterThanOrEqualTo("scheduledAt", startOfDay)
                        .whereLessThanOrEqualTo("scheduledAt", endOfDay)
                        .get().await()
                    val doses = dosesSnap.documents.mapNotNull { doc -> doc.toObject(DoseEvent::class.java)?.copy(id = doc.id) }
                    if (doses.isNotEmpty()) {
                        database.doseEventDao().insertDoseEvents(doses.map { it.toEntity() })
                    }

                    // 5. Generate today's doses if missing
                    doseEventRepository.generateTodayDosesIfMissing(groupId)

                    // 6. Rebuild alarms
                    ExactAlarmScheduler.rescheduleAll(context)

                    _uiState.update { it.copy(isRestoringData = false, restorationError = null) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isRestoringData = false,
                            restorationError = "Could not sync your data. Tap to retry."
                        )
                    }
                }
            } else {
                doseEventRepository.generateTodayDosesIfMissing(groupId)
                ExactAlarmScheduler.rescheduleAll(context)
            }

            val group = familyGroupRepository.getGroup(groupId)
            _uiState.update { it.copy(currentGroup = group) }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
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

            launch {
                familyGroupRepository.observeMembers(groupId)
                    .collectLatest { membersList ->
                        val currentUid = user.id
                        val admin = membersList.any { it.userId == currentUid && it.role == MemberRole.ADMIN }
                        _uiState.update { it.copy(members = membersList, isAdmin = admin) }
                    }
            }
        }
    }

    fun markDoseTaken(eventId: String) {
        val currentUid = _uiState.value.currentUser?.id.orEmpty()
        viewModelScope.launch {
            val event = database.doseEventDao().getDoseEventById(eventId)
            if (event != null) {
                doseEventRepository.recordDose(
                    medicineId = event.medicineId,
                    scheduledAt = event.scheduledAt,
                    status = DoseStatus.TAKEN,
                    actedBy = currentUid
                )
                ExactAlarmScheduler.cancelAlarmsForDose(context, eventId)
            }
        }
    }

    fun markDoseSkipped(eventId: String) {
        val currentUid = _uiState.value.currentUser?.id.orEmpty()
        viewModelScope.launch {
            val event = database.doseEventDao().getDoseEventById(eventId)
            if (event != null) {
                doseEventRepository.recordDose(
                    medicineId = event.medicineId,
                    scheduledAt = event.scheduledAt,
                    status = DoseStatus.SKIPPED,
                    actedBy = currentUid
                )
                ExactAlarmScheduler.cancelAlarmsForDose(context, eventId)
            }
        }
    }

    fun triggerTestAlarm(context: Context) {
        val firstMed = _uiState.value.medicines.firstOrNull()
        val medId = firstMed?.id ?: "test_med"
        val medName = firstMed?.name ?: "Metformin 500mg"
        val dosage = firstMed?.dosage ?: "1 tablet"
        val scheduledAt = System.currentTimeMillis()

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, "test_schedule")
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, "test_schedule")
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, scheduledAt)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
