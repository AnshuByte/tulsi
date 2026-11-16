package com.tulsicare.app.core.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.auth.FirebaseAuth
import com.tulsicare.app.R
import com.tulsicare.app.TulsiApp
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.model.DoseStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun doseEventRepository(): DoseEventRepository
        fun medicineDao(): MedicineDao
        fun auth(): FirebaseAuth
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val medicineId = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, 0L)
        val doseId = intent.getStringExtra(AlarmReceiver.EXTRA_DOSE_ID).orEmpty()
        val medicineName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_NAME).orEmpty()
        val dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE).orEmpty()
        val personName = intent.getStringExtra(AlarmReceiver.EXTRA_PERSON_NAME).orEmpty()
        val groupId = intent.getStringExtra(AlarmReceiver.EXTRA_GROUP_ID).orEmpty()
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)

        val notificationManager = context.getSystemService<NotificationManager>()

        when (action) {
            ACTION_PRE_REMINDER_TAKEN -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext ?: context,
                            ActionEntryPoint::class.java
                        )
                        val currentUserId = entryPoint.auth().currentUser?.uid.orEmpty()

                        // 1. Record dose as TAKEN
                        entryPoint.doseEventRepository().recordDose(
                            medicineId = medicineId,
                            scheduledAt = scheduledAt,
                            status = DoseStatus.TAKEN,
                            actedBy = currentUserId
                        )

                        // 2. Cancel the alarm, escalation check and any snooze for that dose
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)

                        // 3. Replace notification with short "Marked as taken" auto-dismissed in 3s
                        val confirmationNotif = NotificationCompat.Builder(context, TulsiApp.CHANNEL_ID_UPCOMING_DOSE)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Marked as taken")
                            .setContentText(if (medicineName.isNotBlank()) "$medicineName recorded as taken" else "Dose recorded as taken")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .build()

                        notificationManager?.notify(doseId.hashCode(), confirmationNotif)
                        Handler(Looper.getMainLooper()).postDelayed({
                            notificationManager?.cancel(doseId.hashCode())
                        }, 3000L)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_PRE_REMINDER_DISMISS -> {
                // Dismiss notification only. Dose alarm still rings at dose time.
                notificationManager?.cancel(doseId.hashCode())
            }

            ACTION_ALARM_TAKEN -> {
                AlarmPlaybackService.stop(context)
                notificationManager?.cancel(doseId.hashCode())
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext ?: context,
                            ActionEntryPoint::class.java
                        )
                        val currentUserId = entryPoint.auth().currentUser?.uid.orEmpty()
                        entryPoint.doseEventRepository().recordDose(
                            medicineId = medicineId,
                            scheduledAt = scheduledAt,
                            status = DoseStatus.TAKEN,
                            actedBy = currentUserId
                        )
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_ALARM_SNOOZE -> {
                AlarmPlaybackService.stop(context)
                notificationManager?.cancel(doseId.hashCode())
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext ?: context,
                            ActionEntryPoint::class.java
                        )
                        val currentUserId = entryPoint.auth().currentUser?.uid.orEmpty()
                        entryPoint.doseEventRepository().recordDose(
                            medicineId = medicineId,
                            scheduledAt = scheduledAt,
                            status = DoseStatus.SNOOZED,
                            actedBy = currentUserId,
                            snoozeMinutes = snoozeMinutes
                        )

                        val snoozeTrigger = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
                        ExactAlarmScheduler.scheduleAlarm(
                            context = context,
                            userId = currentUserId,
                            groupId = groupId,
                            medicineId = medicineId,
                            doseId = doseId,
                            scheduledAt = scheduledAt,
                            triggerAtMillis = snoozeTrigger,
                            type = AlarmType.SNOOZE,
                            medicineName = medicineName,
                            dosage = dosage,
                            personName = personName,
                            leadMinutes = 0
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_ALARM_SKIP -> {
                AlarmPlaybackService.stop(context)
                notificationManager?.cancel(doseId.hashCode())
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext ?: context,
                            ActionEntryPoint::class.java
                        )
                        val currentUserId = entryPoint.auth().currentUser?.uid.orEmpty()
                        entryPoint.doseEventRepository().recordDose(
                            medicineId = medicineId,
                            scheduledAt = scheduledAt,
                            status = DoseStatus.SKIPPED,
                            actedBy = currentUserId
                        )
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_MISSED_TAKEN -> {
                notificationManager?.cancel(doseId.hashCode())
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext ?: context,
                            ActionEntryPoint::class.java
                        )
                        val currentUserId = entryPoint.auth().currentUser?.uid.orEmpty()
                        entryPoint.doseEventRepository().recordDose(
                            medicineId = medicineId,
                            scheduledAt = scheduledAt,
                            status = DoseStatus.TAKEN,
                            actedBy = currentUserId
                        )
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_MISSED_SKIP -> {
                notificationManager?.cancel(doseId.hashCode())
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext ?: context,
                            ActionEntryPoint::class.java
                        )
                        val currentUserId = entryPoint.auth().currentUser?.uid.orEmpty()
                        entryPoint.doseEventRepository().recordDose(
                            medicineId = medicineId,
                            scheduledAt = scheduledAt,
                            status = DoseStatus.SKIPPED,
                            actedBy = currentUserId
                        )
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PRE_REMINDER_TAKEN = "com.tulsicare.app.ACTION_PRE_REMINDER_TAKEN"
        const val ACTION_PRE_REMINDER_DISMISS = "com.tulsicare.app.ACTION_PRE_REMINDER_DISMISS"
        const val ACTION_ALARM_TAKEN = "com.tulsicare.app.ACTION_ALARM_TAKEN"
        const val ACTION_ALARM_SNOOZE = "com.tulsicare.app.ACTION_ALARM_SNOOZE"
        const val ACTION_ALARM_SKIP = "com.tulsicare.app.ACTION_ALARM_SKIP"
        const val ACTION_MISSED_TAKEN = "com.tulsicare.app.ACTION_MISSED_TAKEN"
        const val ACTION_MISSED_SKIP = "com.tulsicare.app.ACTION_MISSED_SKIP"

        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }
}
