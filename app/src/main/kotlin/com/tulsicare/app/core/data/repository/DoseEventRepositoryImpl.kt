package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.ScheduleType
import com.tulsicare.app.core.network.PushRelayClient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoseEventRepositoryImpl @Inject constructor(
    private val doseEventDao: DoseEventDao,
    private val medicineDao: MedicineDao,
    private val scheduleDao: ScheduleDao,
    private val firestore: FirebaseFirestore,
    private val pushRelayClient: PushRelayClient
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

    override suspend fun recordDoseAction(
        groupId: String,
        eventId: String,
        status: DoseStatus,
        actedBy: String
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()

        // 1. Update Room DB immediately for offline responsiveness
        doseEventDao.updateDoseStatus(eventId, status, now, actedBy)

        // 2. Sync to Firestore
        val updates = mapOf(
            "status" to status.name,
            "actedAt" to now,
            "actedBy" to actedBy
        )
        firestore.collection("groups")
            .document(groupId)
            .collection("doseEvents")
            .document(eventId)
            .set(updates, SetOptions.merge())
            .await()

        // 3. Relay push notification to other family members via Cloudflare Worker
        val event = doseEventDao.getDoseEventById(eventId)
        val medName = event?.medicineName ?: "Medicine"
        val dosage = event?.dosage.orEmpty()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        pushRelayClient.relayDoseStatus(groupId, "Family Member", medName, dosage, status.name, timeStr)
    }

    override suspend fun snoozeDose(
        groupId: String,
        eventId: String,
        snoozeMinutes: Int
    ): Result<Unit> = runCatching {
        val snoozeUntil = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        // 1. Update Room DB
        doseEventDao.snoozeDose(eventId, DoseStatus.SNOOZED, snoozeUntil)

        // 2. Sync to Firestore
        val updates = mapOf(
            "status" to DoseStatus.SNOOZED.name,
            "snoozeUntil" to snoozeUntil
        )
        firestore.collection("groups")
            .document(groupId)
            .collection("doseEvents")
            .document(eventId)
            .set(updates, SetOptions.merge())
            .await()
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

                // Check if already created
                val existing = doseEventDao.getEventForScheduleAndSlot(schedule.id, scheduledAt)
                if (existing == null) {
                    val newEvent = DoseEvent(
                        id = UUID.randomUUID().toString(),
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
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("doseEvents")
            .whereGreaterThanOrEqualTo("scheduledAt", startMillis)
            .whereLessThanOrEqualTo("scheduledAt", endMillis)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
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

        awaitClose { registration.remove() }
    }
}
