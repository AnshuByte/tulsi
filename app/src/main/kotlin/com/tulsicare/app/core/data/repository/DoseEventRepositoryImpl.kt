package com.tulsicare.app.core.data.repository

import android.content.Context
import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.messaging.AdminPushWorker
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.ScheduleType
import com.tulsicare.app.core.model.generateDoseId
import com.tulsicare.app.core.network.PushRelayClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.tulsicare.app.core.data.sync.FirestoreListenerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoseEventRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val doseEventDao: DoseEventDao,
    private val medicineDao: MedicineDao,
    private val scheduleDao: ScheduleDao,
    private val firestore: FirebaseFirestore,
    private val pushRelayClient: PushRelayClient,
    private val firestoreListenerManager: FirestoreListenerManager
) : DoseEventRepository {

    override fun observeEventsForDateRange(
        groupId: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<DoseEvent>> {
        return doseEventDao.observeEventsForDateRange(groupId, startMillis, endMillis).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getEventsForDateRange(
        groupId: String,
        startMillis: Long,
        endMillis: Long
    ): List<DoseEvent> {
        return doseEventDao.getEventsForDateRange(groupId, startMillis, endMillis).map { it.toDomainModel() }
    }

    override suspend fun recordDose(
        medicineId: String,
        scheduledAt: Long,
        status: DoseStatus,
        actedBy: String?,
        snoozeMinutes: Int?
    ): Result<DoseEvent> = runCatching {
        val now = System.currentTimeMillis()
        val doseId = generateDoseId(medicineId, scheduledAt)
        val actor = actedBy ?: auth.currentUser?.uid.orEmpty()
        val actorName = auth.currentUser?.displayName?.ifBlank { "Family Member" } ?: "Family Member"

        val existing = doseEventDao.getDoseEventById(doseId)?.toDomainModel()
        val medicine = medicineDao.getMedicineById(medicineId)?.toDomainModel()

        val updated = if (existing != null) {
            when (status) {
                DoseStatus.SNOOZED -> {
                    val minutes = snoozeMinutes ?: 10
                    val snoozeUntil = now + (minutes * 60 * 1000L)
                    existing.copy(
                        status = DoseStatus.SNOOZED,
                        snoozeUntil = snoozeUntil,
                        snoozeCount = existing.snoozeCount + 1,
                        actedAt = now,
                        actedBy = actor
                    )
                }
                else -> {
                    existing.copy(
                        status = status,
                        actedAt = now,
                        actedBy = actor
                    )
                }
            }
        } else {
            val medName = medicine?.name ?: "Medicine"
            val dosage = medicine?.dosage.orEmpty()
            val groupId = medicine?.groupId.orEmpty().ifBlank { "my_family" }
            val memberId = medicine?.assignedMemberId.orEmpty()

            when (status) {
                DoseStatus.SNOOZED -> {
                    val minutes = snoozeMinutes ?: 10
                    val snoozeUntil = now + (minutes * 60 * 1000L)
                    DoseEvent(
                        id = doseId,
                        groupId = groupId,
                        medicineId = medicineId,
                        medicineName = medName,
                        dosage = dosage,
                        assignedMemberId = memberId,
                        scheduledAt = scheduledAt,
                        status = DoseStatus.SNOOZED,
                        actedAt = now,
                        actedBy = actor,
                        snoozeUntil = snoozeUntil,
                        snoozeCount = 1,
                        createdAt = now
                    )
                }
                else -> {
                    DoseEvent(
                        id = doseId,
                        groupId = groupId,
                        medicineId = medicineId,
                        medicineName = medName,
                        dosage = dosage,
                        assignedMemberId = memberId,
                        scheduledAt = scheduledAt,
                        status = status,
                        actedAt = now,
                        actedBy = actor,
                        createdAt = now
                    )
                }
            }
        }

        // 1. Update Room DB immediately for offline responsiveness
        doseEventDao.insertDoseEvent(updated.toEntity())

        // 2. Sync to Firestore (offline queue enabled)
        runCatching {
            if (updated.groupId.isNotBlank()) {
                firestore.collection("groups")
                    .document(updated.groupId)
                    .collection("doseEvents")
                    .document(doseId)
                    .set(updated, SetOptions.merge())
                    .await()
            }
        }

        // 3. Relay push notification to admin via WorkManager (offline queue + exactly once)
        if (status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
            AdminPushWorker.enqueue(
                context = context,
                groupId = updated.groupId,
                actorUserId = actor,
                actorName = actorName,
                medicineName = updated.medicineName,
                dosage = updated.dosage,
                status = status.name,
                time = timeStr,
                doseId = doseId
            )
        }

        updated
    }

    override suspend fun recordDoseAction(
        groupId: String,
        eventId: String,
        status: DoseStatus,
        actedBy: String
    ): Result<Unit> = runCatching {
        val existing = doseEventDao.getDoseEventById(eventId)
        if (existing != null) {
            recordDose(existing.medicineId, existing.scheduledAt, status, actedBy).getOrThrow()
        } else {
            val now = System.currentTimeMillis()
            doseEventDao.updateDoseStatus(eventId, status, now, actedBy)
            runCatching {
                firestore.collection("groups")
                    .document(groupId)
                    .collection("doseEvents")
                    .document(eventId)
                    .set(mapOf("status" to status.name, "actedAt" to now, "actedBy" to actedBy), SetOptions.merge())
            }
        }
        Unit
    }

    override suspend fun snoozeDose(
        groupId: String,
        eventId: String,
        snoozeMinutes: Int
    ): Result<Unit> = runCatching {
        val existing = doseEventDao.getDoseEventById(eventId)
        if (existing != null) {
            recordDose(existing.medicineId, existing.scheduledAt, DoseStatus.SNOOZED, snoozeMinutes = snoozeMinutes).getOrThrow()
        } else {
            val now = System.currentTimeMillis()
            val snoozeUntil = now + (snoozeMinutes * 60 * 1000L)
            doseEventDao.snoozeDose(eventId, DoseStatus.SNOOZED, snoozeUntil)
            runCatching {
                firestore.collection("groups")
                    .document(groupId)
                    .collection("doseEvents")
                    .document(eventId)
                    .set(mapOf("status" to DoseStatus.SNOOZED.name, "snoozeUntil" to snoozeUntil), SetOptions.merge())
            }
        }
        Unit
    }

    override suspend fun generateTodayDosesIfMissing(
        groupId: String,
        targetDateMillis: Long
    ): Result<List<DoseEvent>> = runCatching {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = targetDateMillis
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

        // Day of week: Calendar returns 1 for Sunday, 2 for Monday ... 7 for Saturday
        // In our model: 1 = Monday ... 7 = Sunday
        val calDayOfWeek = Calendar.getInstance().apply { timeInMillis = targetDateMillis }.get(Calendar.DAY_OF_WEEK)
        val modelDayOfWeek = if (calDayOfWeek == Calendar.SUNDAY) 7 else calDayOfWeek - 1

        val activeSchedules = scheduleDao.getActiveSchedules(groupId)
        val generatedEvents = mutableListOf<DoseEvent>()

        for (schedule in activeSchedules) {
            val medicine = medicineDao.getMedicineById(schedule.medicineId) ?: continue
            if (!medicine.isActive) continue

            val shouldScheduleToday = when (schedule.type) {
                ScheduleType.DAILY -> true
                ScheduleType.SPECIFIC_DAYS -> schedule.daysOfWeek.contains(modelDayOfWeek)
                ScheduleType.INTERVAL -> true
                ScheduleType.AS_NEEDED -> false
            }

            if (!shouldScheduleToday) continue

            for (timeStr in schedule.timesOfDay) {
                val parts = timeStr.split(":")
                if (parts.size != 2) continue
                val hour = parts[0].toIntOrNull() ?: continue
                val minute = parts[1].toIntOrNull() ?: continue

                val slotCal = Calendar.getInstance().apply {
                    timeInMillis = targetDateMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val scheduledAt = slotCal.timeInMillis
                val doseId = generateDoseId(medicine.id, scheduledAt)

                // Check if already created using deterministic doseId
                val existing = doseEventDao.getDoseEventById(doseId) ?: doseEventDao.getEventForScheduleAndSlot(schedule.id, scheduledAt)
                if (existing == null) {
                    val newEvent = DoseEvent(
                        id = doseId,
                        groupId = groupId,
                        medicineId = medicine.id,
                        medicineName = medicine.name,
                        dosage = medicine.dosage,
                        assignedMemberId = medicine.assignedMemberId,
                        scheduleId = schedule.id,
                        scheduledAt = scheduledAt,
                        status = DoseStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                    doseEventDao.insertDoseEvent(newEvent.toEntity())
                    // Write to Firestore asynchronously
                    runCatching {
                        firestore.collection("groups")
                            .document(groupId)
                            .collection("doseEvents")
                            .document(newEvent.id)
                            .set(newEvent)
                    }
                    generatedEvents.add(newEvent)
                }
            }
        }
        generatedEvents
    }

    override fun syncDoseEventsRealtime(
        groupId: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<DoseEvent>> = callbackFlow {
        val currentUid = auth.currentUser?.uid.orEmpty()
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("doseEvents")
            .whereGreaterThanOrEqualTo("scheduledAt", startMillis)
            .whereLessThanOrEqualTo("scheduledAt", endMillis)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val isNotFoundOrDenied = error.code == FirebaseFirestoreException.Code.NOT_FOUND ||
                            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    if (isNotFoundOrDenied) {
                        trySend(emptyList())
                    }
                    return@addSnapshotListener
                }

                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(DoseEvent::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Cache in Room
                if (events.isNotEmpty()) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        doseEventDao.insertDoseEvents(events.map { it.toEntity() })
                    }
                }
                trySend(events)
            }

        if (currentUid.isNotBlank()) {
            firestoreListenerManager.register(currentUid, groupId, "doseEvents", registration)
        }

        awaitClose {
            if (currentUid.isNotBlank()) {
                firestoreListenerManager.removeListener(currentUid, groupId, "doseEvents")
            }
            registration.remove()
        }
    }
}
