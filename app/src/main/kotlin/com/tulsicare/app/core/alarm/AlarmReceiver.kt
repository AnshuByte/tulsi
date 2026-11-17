package com.tulsicare.app.core.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.firebase.auth.FirebaseAuth
import com.tulsicare.app.MainActivity
import com.tulsicare.app.R
import com.tulsicare.app.TulsiApp
import com.tulsicare.app.core.database.dao.AlarmRecordDao
import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.feature.alarm.AlarmActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun auth(): FirebaseAuth
        fun medicineDao(): MedicineDao
        fun doseEventDao(): DoseEventDao
        fun alarmRecordDao(): AlarmRecordDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID).orEmpty()
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "Medicine"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE).orEmpty()
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID).orEmpty()
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID).orEmpty().ifBlank { scheduleId }
        val userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        val alarmTypeStr = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: AlarmType.DOSE_ALARM.name
        val personName = intent.getStringExtra(EXTRA_PERSON_NAME).orEmpty()
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 10)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext ?: context
                val entryPoint = EntryPointAccessors.fromApplication(appContext, ReceiverEntryPoint::class.java)

                // -------------------------------------------------------------
                // Validate on fire:
                // (a) a user is signed in and the alarm's userId matches current user
                // -------------------------------------------------------------
                val currentUid = entryPoint.auth().currentUser?.uid
                if (currentUid.isNullOrBlank() || (userId.isNotBlank() && currentUid != userId)) {
                    // Stale or logged out user alarm! Cancel silently
                    ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                    return@launch
                }

                // -------------------------------------------------------------
                // (b) the medicine still exists and is active
                // -------------------------------------------------------------
                if (medicineId.isNotBlank()) {
                    val medicine = entryPoint.medicineDao().getMedicineById(medicineId)
                    if (medicine == null || !medicine.isActive) {
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                        return@launch
                    }
                }

                // -------------------------------------------------------------
                // (c) the dose is still PENDING or SNOOZED and the time is valid
                // -------------------------------------------------------------
                if (doseId.isNotBlank()) {
                    val doseEvent = entryPoint.doseEventDao().getDoseEventById(doseId)
                    if (doseEvent != null && (doseEvent.status == DoseStatus.TAKEN || doseEvent.status == DoseStatus.SKIPPED)) {
                        // Already taken or skipped! Cancel silently: no sound, no notification
                        ExactAlarmScheduler.cancelAlarmsForDose(context, doseId)
                        return@launch
                    }
                }

                // All validations passed! Handle alarm according to type
                val alarmType = try {
                    AlarmType.valueOf(alarmTypeStr)
                } catch (_: Exception) {
                    AlarmType.DOSE_ALARM
                }

                when (alarmType) {
                    AlarmType.PRE_REMINDER -> {
                        showPreReminderNotification(
                            context = context,
                            medicineId = medicineId,
                            medicineName = medicineName,
                            doseId = doseId,
                            groupId = groupId,
                            scheduledAt = scheduledAt,
                            personName = personName,
                            leadMinutes = leadMinutes
                        )
                    }

                    AlarmType.DOSE_ALARM, AlarmType.SNOOZE -> {
                        triggerDoseAlarm(
                            context = context,
                            medicineId = medicineId,
                            medicineName = medicineName,
                            dosage = dosage,
                            scheduleId = scheduleId,
                            doseId = doseId,
                            scheduledAt = scheduledAt,
                            groupId = groupId,
                            userId = currentUid,
                            personName = personName
                        )
                    }

                    else -> {}
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showPreReminderNotification(
        context: Context,
        medicineId: String,
        medicineName: String,
        doseId: String,
        groupId: String,
        scheduledAt: Long,
        personName: String,
        leadMinutes: Int
    ) {
        val notificationManager = context.getSystemService<NotificationManager>() ?: return

        val title = if (personName.isNotBlank() && !personName.equals("me", ignoreCase = true)) {
            "$personName's $medicineName in $leadMinutes minutes"
        } else {
            "$medicineName in $leadMinutes minutes"
        }

        val timeStr = if (scheduledAt > 0) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(scheduledAt))
        } else ""

        val contentText = if (timeStr.isNotBlank()) "Scheduled for $timeStr. Tap if already taken." else "Due in $leadMinutes minutes."

        // 1. "Taken" Action Button: BroadcastReceiver PendingIntent (FLAG_IMMUTABLE)
        val takenIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_PRE_REMINDER_TAKEN
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_PERSON_NAME, personName)
            putExtra(EXTRA_GROUP_ID, groupId)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            ("${doseId}_pre_taken").hashCode(),
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. "Dismiss" Action Button: BroadcastReceiver PendingIntent (FLAG_IMMUTABLE)
        val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_PRE_REMINDER_DISMISS
            putExtra(EXTRA_DOSE_ID, doseId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            ("${doseId}_pre_dismiss").hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ContentIntent: Tapping the notification body (not the buttons) opens MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            doseId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TulsiApp.CHANNEL_ID_UPCOMING_DOSE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(0, "Taken", takenPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()

        notificationManager.notify(doseId.hashCode(), notification)
    }

    private fun triggerDoseAlarm(
        context: Context,
        medicineId: String,
        medicineName: String,
        dosage: String,
        scheduleId: String,
        doseId: String,
        scheduledAt: Long,
        groupId: String,
        userId: String,
        personName: String
    ) {
        // Start AlarmPlaybackService for sound and vibration playback
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(EXTRA_GROUP_ID, groupId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_PERSON_NAME, personName)
        }

        ContextCompat.startForegroundService(context, serviceIntent)

        // Check if unlocked and in active use:
        val keyguardManager = context.getSystemService<KeyguardManager>()
        val powerManager = context.getSystemService<PowerManager>()
        val isScreenOn = powerManager?.isInteractive ?: true
        val isLocked = keyguardManager?.isKeyguardLocked ?: false

        if (isScreenOn && !isLocked) {
            // Screen is on and unlocked: launch compact popup activity over current app
            val popupIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_MEDICINE_ID, medicineId)
                putExtra(EXTRA_MEDICINE_NAME, medicineName)
                putExtra(EXTRA_DOSAGE, dosage)
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_DOSE_ID, doseId)
                putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                putExtra(EXTRA_PERSON_NAME, personName)
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_IS_UNLOCKED_POPUP, true)
            }
            try {
                context.startActivity(popupIntent)
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.tulsicare.app.ACTION_ALARM_TRIGGER"

        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"
        const val EXTRA_TRIGGER_AT = "extra_trigger_at"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_PERSON_NAME = "extra_person_name"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_IS_UNLOCKED_POPUP = "extra_is_unlocked_popup"
    }
}
