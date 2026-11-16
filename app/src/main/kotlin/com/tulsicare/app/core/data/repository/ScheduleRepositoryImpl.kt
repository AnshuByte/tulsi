package com.tulsicare.app.core.data.repository

import android.content.Context
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.network.PushRelayClient
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val firestore: FirebaseFirestore,
    private val pushRelayClient: PushRelayClient
) : ScheduleRepository {

    override fun observeActiveSchedules(groupId: String): Flow<List<Schedule>> {
        return scheduleDao.observeActiveSchedules(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getActiveSchedules(groupId: String): List<Schedule> {
        return scheduleDao.getActiveSchedules(groupId).map { it.toDomainModel() }
    }

    override suspend fun getSchedulesForMedicine(medicineId: String): List<Schedule> {
        return scheduleDao.getSchedulesForMedicine(medicineId).map { it.toDomainModel() }
    }

    override suspend fun addSchedule(schedule: Schedule): Result<String> = runCatching {
        val id = if (schedule.id.isBlank()) UUID.randomUUID().toString() else schedule.id
        val toSave = schedule.copy(id = id)

        // Cache in Room immediately
        scheduleDao.insertSchedule(toSave.toEntity())

        // Reschedule alarms immediately
        ExactAlarmScheduler.rescheduleAll(context)

        // Sync to Firestore in background
        runCatching {
            firestore.collection("groups")
                .document(toSave.groupId)
                .collection("schedules")
                .document(id)
                .set(toSave)
                .await()
        }

        // Notify family members via Cloudflare Worker push relay
        runCatching {
            pushRelayClient.relayScheduleSync(toSave.groupId)
        }

        id
    }

    override suspend fun updateSchedule(schedule: Schedule): Result<Unit> = runCatching {
        scheduleDao.updateSchedule(schedule.toEntity())
        ExactAlarmScheduler.cancelAlarm(context, schedule.id)
        ExactAlarmScheduler.rescheduleAll(context)

        runCatching {
            firestore.collection("groups")
                .document(schedule.groupId)
                .collection("schedules")
                .document(schedule.id)
                .set(schedule)
                .await()
        }

        runCatching {
            pushRelayClient.relayScheduleSync(schedule.groupId)
        }
    }

    override suspend fun deleteSchedule(groupId: String, scheduleId: String): Result<Unit> = runCatching {
        firestore.collection("groups")
            .document(groupId)
            .collection("schedules")
            .document(scheduleId)
            .update("isActive", false)
            .await()

        scheduleDao.deleteScheduleById(scheduleId)
        ExactAlarmScheduler.cancelAlarm(context, scheduleId)
        ExactAlarmScheduler.rescheduleAll(context)
        pushRelayClient.relayScheduleSync(groupId)
    }

    override fun syncSchedulesRealtime(groupId: String): Flow<List<Schedule>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("schedules")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val schedules = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Schedule::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(schedules)
            }

        awaitClose { registration.remove() }
    }
}
