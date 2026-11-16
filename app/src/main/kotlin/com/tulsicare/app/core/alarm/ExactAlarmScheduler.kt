package com.tulsicare.app.core.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.google.firebase.auth.FirebaseAuth
import com.tulsicare.app.core.database.dao.AlarmRecordDao
import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.AlarmRecordEntity
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.model.ScheduleType
import com.tulsicare.app.core.model.generateDoseId
import com.tulsicare.app.core.preferences.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

enum class AlarmType {
    PRE_REMINDER,
    DOSE_ALARM,
    SNOOZE,
    ESCALATION_CHECK,
    MISSED_NOTIFICATION
}

object ExactAlarmScheduler {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SchedulerEntryPoint {
        fun scheduleDao(): ScheduleDao
        fun medicineDao(): MedicineDao
        fun doseEventDao(): DoseEventDao
        fun alarmRecordDao(): AlarmRecordDao
        fun userPreferencesRepository(): UserPreferencesRepository
        fun auth(): FirebaseAuth
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService<AlarmManager>()
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    fun generateRequestCode(doseId: String, type: String): Int {
        return (doseId + "_" + type).hashCode()
    }

    suspend fun scheduleAlarm(
        context: Context,
        userId: String,
        groupId: String,
        medicineId: String,
        doseId: String,
        scheduledAt: Long,
        triggerAtMillis: Long,
        type: AlarmType,
        medicineName: String = "",
        dosage: String = "",
        personName: String = "",
        leadMinutes: Int = 0
    ) = withContext(Dispatchers.IO) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return@withContext
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_ALARM_TYPE, type.name)
            putExtra(AlarmReceiver.EXTRA_USER_ID, userId)
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, groupId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, doseId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAtMillis)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_PERSON_NAME, personName)
            putExtra(AlarmReceiver.EXTRA_LEAD_MINUTES, leadMinutes)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, doseId) // Backwards compatibility
        }

        val requestCode = generateRequestCode(doseId, type.name)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = canScheduleExactAlarms(context)
        if (canExact) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        // Store into central AlarmRegistry in Room
        try {
            val appContext = context.applicationContext ?: context
            val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)
            val record = AlarmRecordEntity(
                id = "${doseId}_${type.name}",
                userId = userId,
                groupId = groupId,
                medicineId = medicineId,
                doseId = doseId,
                type = type.name,
                triggerAt = triggerAtMillis,
                requestCode = requestCode
            )
            entryPoint.alarmRecordDao().insertAlarmRecord(record)
        } catch (_: Exception) {}
    }

    // Backwards-compatible synchronous schedule helper
    fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        medicineId: String,
        medicineName: String,
        dosage: String,
        scheduleId: String
    ) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_ALARM_TYPE, AlarmType.DOSE_ALARM.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = canScheduleExactAlarms(context)
        if (canExact) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    suspend fun cancelAlarm(context: Context, doseId: String, type: AlarmType) = withContext(Dispatchers.IO) {
        try {
            val alarmManager = context.getSystemService<AlarmManager>() ?: return@withContext
            val requestCode = generateRequestCode(doseId, type.name)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }

            val appContext = context.applicationContext ?: context
            val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)
            entryPoint.alarmRecordDao().deleteRecordForDoseAndType(doseId, type.name)
        } catch (_: Exception) {}
    }

    fun cancelAlarm(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    suspend fun cancelAlarmsForDose(context: Context, doseId: String) = withContext(Dispatchers.IO) {
        try {
            val alarmManager = context.getSystemService<AlarmManager>() ?: return@withContext
            val appContext = context.applicationContext ?: context
            val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)
            val records = entryPoint.alarmRecordDao().getRecordsForDose(doseId)

            for (record in records) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_ALARM_TRIGGER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    record.requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }

            // Also cancel deterministic notification if posted
            val notificationManager = context.getSystemService<NotificationManager>()
            notificationManager?.cancel(doseId.hashCode())

            entryPoint.alarmRecordDao().deleteRecordsForDose(doseId)
        } catch (_: Exception) {}
    }

    suspend fun cancelAlarmsForMedicine(context: Context, medicineId: String) = withContext(Dispatchers.IO) {
        try {
            val alarmManager = context.getSystemService<AlarmManager>() ?: return@withContext
            val appContext = context.applicationContext ?: context
            val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)
            val records = entryPoint.alarmRecordDao().getRecordsForMedicine(medicineId)

            val notificationManager = context.getSystemService<NotificationManager>()
            for (record in records) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_ALARM_TRIGGER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    record.requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
                notificationManager?.cancel(record.doseId.hashCode())
            }

            entryPoint.alarmRecordDao().deleteRecordsForMedicine(medicineId)
        } catch (_: Exception) {}
    }

    suspend fun cancelAlarmsForGroup(context: Context, groupId: String) = withContext(Dispatchers.IO) {
        try {
            val alarmManager = context.getSystemService<AlarmManager>() ?: return@withContext
            val appContext = context.applicationContext ?: context
            val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)
            val records = entryPoint.alarmRecordDao().getRecordsForGroup(groupId)

            val notificationManager = context.getSystemService<NotificationManager>()
            for (record in records) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_ALARM_TRIGGER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    record.requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
                notificationManager?.cancel(record.doseId.hashCode())
            }

            entryPoint.alarmRecordDao().deleteRecordsForGroup(groupId)
        } catch (_: Exception) {}
    }

    suspend fun cancelAllAlarms(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 1. Immediately stop any ringing alarm sound/vibration
            AlarmPlaybackService.stop(context)

            // 2. Dismiss all posted notifications
            val notificationManager = context.getSystemService<NotificationManager>()
            notificationManager?.cancelAll()

            // 3. Cancel every scheduled alarm in system AlarmManager via registry
            val alarmManager = context.getSystemService<AlarmManager>()
            val appContext = context.applicationContext ?: context
            val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)
            val allRecords = entryPoint.alarmRecordDao().getAllRecords()

            for (record in allRecords) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_ALARM_TRIGGER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    record.requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager?.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }

            // 4. Also clean up any legacy schedule IDs
            val schedules = entryPoint.scheduleDao().getAllActiveSchedules()
            for (schedule in schedules) {
                cancelAlarm(appContext, schedule.id)
            }

            // 5. Clear Room registry table
            entryPoint.alarmRecordDao().clearAll()
        } catch (_: Exception) {}
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext ?: context
                val entryPoint = EntryPointAccessors.fromApplication(appContext, SchedulerEntryPoint::class.java)

                val currentUserId = entryPoint.auth().currentUser?.uid
                if (currentUserId.isNullOrBlank()) {
                    // No user signed in: cancel all alarms and return
                    cancelAllAlarms(appContext)
                    return@launch
                }

                val leadMinutes = entryPoint.userPreferencesRepository().preReminderLeadMinutes.firstOrNull() ?: 10
                val activeSchedules = entryPoint.scheduleDao().getAllActiveSchedules()
                val now = System.currentTimeMillis()

                // Clear existing alarms in registry to rebuild fresh
                val existingRecords = entryPoint.alarmRecordDao().getRecordsForUser(currentUserId)
                val alarmManager = appContext.getSystemService<AlarmManager>()
                for (record in existingRecords) {
                    val intent = Intent(appContext, AlarmReceiver::class.java).apply {
                        action = AlarmReceiver.ACTION_ALARM_TRIGGER
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        record.requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pendingIntent != null) {
                        alarmManager?.cancel(pendingIntent)
                        pendingIntent.cancel()
                    }
                }
                entryPoint.alarmRecordDao().deleteRecordsForUser(currentUserId)

                // Reschedule for all active schedules
                for (scheduleEntity in activeSchedules) {
                    val schedule = scheduleEntity.toDomainModel()
                    if (!schedule.isActive) continue
                    if (schedule.endDate != null && schedule.endDate < now) continue

                    val medicineEntity = entryPoint.medicineDao().getMedicineById(schedule.medicineId) ?: continue
                    val medicine = medicineEntity.toDomainModel()
                    if (!medicine.isActive) continue

                    val upcomingTimes = computeUpcomingTriggerTimes(schedule, now, lookaheadDays = 1)
                    for (scheduledAt in upcomingTimes) {
                        val doseId = generateDoseId(medicine.id, scheduledAt)
                        val existingDose = entryPoint.doseEventDao().getDoseEventById(doseId)?.toDomainModel()

                        // If already acted upon, skip scheduling
                        if (existingDose != null && (existingDose.status == DoseStatus.TAKEN || existingDose.status == DoseStatus.SKIPPED)) {
                            continue
                        }

                        // If snoozed, schedule snooze alarm
                        if (existingDose != null && existingDose.status == DoseStatus.SNOOZED && existingDose.snoozeUntil != null) {
                            if (existingDose.snoozeUntil > now) {
                                scheduleAlarm(
                                    context = appContext,
                                    userId = currentUserId,
                                    groupId = medicine.groupId,
                                    medicineId = medicine.id,
                                    doseId = doseId,
                                    scheduledAt = scheduledAt,
                                    triggerAtMillis = existingDose.snoozeUntil,
                                    type = AlarmType.SNOOZE,
                                    medicineName = medicine.name,
                                    dosage = medicine.dosage,
                                    personName = "",
                                    leadMinutes = 0
                                )
                                continue
                            }
                        }

                        // Schedule Pre-reminder if leadMinutes > 0 and (scheduledAt - leadMinutes) is in future
                        if (leadMinutes > 0) {
                            val preReminderTime = scheduledAt - (leadMinutes * 60 * 1000L)
                            // Edge case: if dose is created less than leadTime before it is due, skip pre-reminder
                            if (preReminderTime > now) {
                                scheduleAlarm(
                                    context = appContext,
                                    userId = currentUserId,
                                    groupId = medicine.groupId,
                                    medicineId = medicine.id,
                                    doseId = doseId,
                                    scheduledAt = scheduledAt,
                                    triggerAtMillis = preReminderTime,
                                    type = AlarmType.PRE_REMINDER,
                                    medicineName = medicine.name,
                                    dosage = medicine.dosage,
                                    personName = "",
                                    leadMinutes = leadMinutes
                                )
                            }
                        }

                        // Schedule Dose Alarm at dose time
                        if (scheduledAt > now) {
                            scheduleAlarm(
                                context = appContext,
                                userId = currentUserId,
                                groupId = medicine.groupId,
                                medicineId = medicine.id,
                                doseId = doseId,
                                scheduledAt = scheduledAt,
                                triggerAtMillis = scheduledAt,
                                type = AlarmType.DOSE_ALARM,
                                medicineName = medicine.name,
                                dosage = medicine.dosage,
                                personName = "",
                                leadMinutes = 0
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun computeNextTriggerTime(schedule: Schedule, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long? {
        val times = computeUpcomingTriggerTimes(schedule, now, lookaheadDays = 7, timeZone = timeZone)
        return times.firstOrNull()
    }

    fun computeUpcomingTriggerTimes(
        schedule: Schedule,
        now: Long,
        lookaheadDays: Int = 1,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<Long> {
        if (!schedule.isActive) return emptyList()
        if (schedule.endDate != null && schedule.endDate <= now) return emptyList()
        if (schedule.type == ScheduleType.AS_NEEDED || schedule.timesOfDay.isEmpty()) return emptyList()

        val results = mutableListOf<Long>()

        for (dayOffset in 0..lookaheadDays) {
            val calendar = Calendar.getInstance(timeZone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }

            val calDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val modelDayOfWeek = if (calDayOfWeek == Calendar.SUNDAY) 7 else calDayOfWeek - 1

            val shouldFireToday = when (schedule.type) {
                ScheduleType.DAILY, ScheduleType.INTERVAL -> true
                ScheduleType.SPECIFIC_DAYS -> schedule.daysOfWeek.contains(modelDayOfWeek)
                ScheduleType.AS_NEEDED -> false
            }
            if (!shouldFireToday) continue

            for (timeStr in schedule.timesOfDay) {
                val parts = timeStr.split(":")
                if (parts.size != 2) continue
                val hour = parts[0].toIntOrNull() ?: continue
                val minute = parts[1].toIntOrNull() ?: continue

                val slot = Calendar.getInstance(timeZone).apply {
                    timeInMillis = calendar.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val slotMillis = slot.timeInMillis
                if (slotMillis <= now) continue
                if (schedule.startDate > slotMillis) continue
                if (schedule.endDate != null && slotMillis > schedule.endDate) continue

                results.add(slotMillis)
            }
        }

        return results.sorted()
    }
}
