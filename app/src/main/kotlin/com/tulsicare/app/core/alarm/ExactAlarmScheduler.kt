package com.tulsicare.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.model.ScheduleType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object ExactAlarmScheduler {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SchedulerEntryPoint {
        fun scheduleDao(): ScheduleDao
        fun medicineDao(): MedicineDao
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService<AlarmManager>()
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

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
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
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
            // Graceful fallback if exact alarm permission was denied
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
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

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext ?: context
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    SchedulerEntryPoint::class.java
                )
                val activeSchedules = entryPoint.scheduleDao().getAllActiveSchedules()
                val now = System.currentTimeMillis()

                for (scheduleEntity in activeSchedules) {
                    val schedule = scheduleEntity.toDomainModel()
                    if (!schedule.isActive) continue
                    if (schedule.endDate != null && schedule.endDate < now) continue

                    val medicineEntity = entryPoint.medicineDao().getMedicineById(schedule.medicineId) ?: continue
                    val medicine = medicineEntity.toDomainModel()
                    if (!medicine.isActive) continue

                    // Calculate next trigger time
                    val nextTrigger = computeNextTriggerTime(schedule, now)
                    if (nextTrigger != null && nextTrigger > now) {
                        scheduleAlarm(
                            context = appContext,
                            triggerAtMillis = nextTrigger,
                            medicineId = medicine.id,
                            medicineName = medicine.name,
                            dosage = medicine.dosage,
                            scheduleId = schedule.id
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore or log if Hilt entry point is not yet initialized (e.g. during very early boot)
            }
        }
    }

    private fun computeNextTriggerTime(schedule: com.tulsicare.app.core.model.Schedule, now: Long): Long? {
        val todayCal = Calendar.getInstance().apply { timeInMillis = now }
        val calDayOfWeek = todayCal.get(Calendar.DAY_OF_WEEK)
        val modelDayOfWeek = if (calDayOfWeek == Calendar.SUNDAY) 7 else calDayOfWeek - 1

        val potentialTimes = mutableListOf<Long>()

        for (timeStr in schedule.timesOfDay) {
            val parts = timeStr.split(":")
            if (parts.size != 2) continue
            val hour = parts[0].toIntOrNull() ?: continue
            val minute = parts[1].toIntOrNull() ?: continue

            // Try today
            val todaySlot = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val shouldFireToday = when (schedule.type) {
                ScheduleType.DAILY -> true
                ScheduleType.SPECIFIC_DAYS -> schedule.daysOfWeek.contains(modelDayOfWeek)
                ScheduleType.INTERVAL -> true
                ScheduleType.AS_NEEDED -> false
            }

            if (shouldFireToday && todaySlot.timeInMillis > now) {
                potentialTimes.add(todaySlot.timeInMillis)
            } else if (schedule.type != ScheduleType.AS_NEEDED) {
                // Try tomorrow
                val tomorrowSlot = Calendar.getInstance().apply {
                    timeInMillis = todaySlot.timeInMillis + (24 * 60 * 60 * 1000L)
                }
                val tomorrowCalDay = tomorrowSlot.get(Calendar.DAY_OF_WEEK)
                val tomorrowModelDay = if (tomorrowCalDay == Calendar.SUNDAY) 7 else tomorrowCalDay - 1
                val shouldFireTomorrow = when (schedule.type) {
                    ScheduleType.DAILY -> true
                    ScheduleType.SPECIFIC_DAYS -> schedule.daysOfWeek.contains(tomorrowModelDay)
                    ScheduleType.INTERVAL -> true
                    ScheduleType.AS_NEEDED -> false
                }
                if (shouldFireTomorrow) {
                    potentialTimes.add(tomorrowSlot.timeInMillis)
                }
            }
        }

        return potentialTimes.minOrNull()
    }
}
