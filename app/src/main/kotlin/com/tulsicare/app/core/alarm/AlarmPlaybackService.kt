package com.tulsicare.app.core.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.tulsicare.app.MainActivity
import com.tulsicare.app.R
import com.tulsicare.app.TulsiApp
import com.tulsicare.app.feature.alarm.AlarmActivity

/**
 * A brief foreground service used ONLY while the alarm is actively ringing/vibrating.
 * Stops immediately once the dose is acted upon, snooze is tapped, or after a 60-second timeout.
 */
class AlarmPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentMedicineId: String = ""
    private var currentMedicineName: String = "Medicine"
    private var currentDosage: String = ""
    private var currentDoseId: String = ""
    private var currentScheduledAt: Long = 0L
    private var currentGroupId: String = ""
    private var currentPersonName: String = ""

    private val autoStopRunnable = Runnable {
        onTimeoutReached()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentMedicineId = intent?.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_ID).orEmpty()
        currentMedicineName = intent?.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_NAME) ?: "Medicine"
        currentDosage = intent?.getStringExtra(AlarmReceiver.EXTRA_DOSAGE).orEmpty()
        val scheduleId = intent?.getStringExtra(AlarmReceiver.EXTRA_SCHEDULE_ID).orEmpty()
        currentDoseId = intent?.getStringExtra(AlarmReceiver.EXTRA_DOSE_ID).orEmpty().ifBlank { scheduleId }
        currentScheduledAt = intent?.getLongExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, 0L) ?: 0L
        currentGroupId = intent?.getStringExtra(AlarmReceiver.EXTRA_GROUP_ID).orEmpty()
        currentPersonName = intent?.getStringExtra(AlarmReceiver.EXTRA_PERSON_NAME).orEmpty()

        val notification = buildAlarmNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_ALARM,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_ALARM, notification)
        }

        requestAudioFocusAndPlaySound()
        startVibration()

        // 60-second timeout: stop sound, leave missed reminder notification, let escalation continue
        handler.removeCallbacks(autoStopRunnable)
        handler.postDelayed(autoStopRunnable, 60_000L)

        return START_NOT_STICKY
    }

    private fun requestAudioFocusAndPlaySound() {
        try {
            val audioManager = getSystemService<AudioManager>()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)

            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmPlaybackService, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Exception) {}
    }

    private fun buildAlarmNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, currentMedicineId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, currentMedicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, currentDosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, currentDoseId)
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, currentDoseId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, currentScheduledAt)
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, currentGroupId)
            putExtra(AlarmReceiver.EXTRA_PERSON_NAME, currentPersonName)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            currentDoseId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. "Taken" Action Button: BroadcastReceiver PendingIntent (FLAG_IMMUTABLE)
        val takenIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_ALARM_TAKEN
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, currentMedicineId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, currentScheduledAt)
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, currentDoseId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, currentMedicineName)
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, currentGroupId)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            this,
            ("${currentDoseId}_alarm_taken").hashCode(),
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. "Snooze" Action Button: BroadcastReceiver PendingIntent (FLAG_IMMUTABLE)
        val snoozeIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_ALARM_SNOOZE
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, currentMedicineId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, currentScheduledAt)
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, currentDoseId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, currentMedicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, currentDosage)
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, currentGroupId)
            putExtra(AlarmReceiver.EXTRA_PERSON_NAME, currentPersonName)
            putExtra(AlarmActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            ("${currentDoseId}_alarm_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. "Skip" Action Button: BroadcastReceiver PendingIntent (FLAG_IMMUTABLE)
        val skipIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_ALARM_SKIP
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, currentMedicineId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, currentScheduledAt)
            putExtra(AlarmReceiver.EXTRA_DOSE_ID, currentDoseId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, currentMedicineName)
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, currentGroupId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            this,
            ("${currentDoseId}_alarm_skip").hashCode(),
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtext = if (currentDosage.isNotBlank()) {
            if (currentPersonName.isNotBlank()) "$currentDosage · For $currentPersonName" else currentDosage
        } else {
            if (currentPersonName.isNotBlank()) "For $currentPersonName" else "Dose is due"
        }

        return NotificationCompat.Builder(this, TulsiApp.CHANNEL_ID_DOSE_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time for $currentMedicineName")
            .setContentText(subtext)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(0, "Taken", takenPendingIntent)
            .addAction(0, "Snooze", snoozePendingIntent)
            .addAction(0, "Skip", skipPendingIntent)
            .build()
    }

    private fun onTimeoutReached() {
        stopRingingAndVibration()

        // Close AlarmActivity if still shown
        sendBroadcast(Intent(ACTION_DISMISS_ALARM_ACTIVITY).setPackage(packageName))

        // Post persistent "Missed reminder: tap to respond" notification
        val notificationManager = getSystemService<NotificationManager>()
        if (notificationManager != null && currentDoseId.isNotBlank()) {
            val contentIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                this,
                currentDoseId.hashCode(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val takenIntent = Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_MISSED_TAKEN
                putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, currentMedicineId)
                putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, currentScheduledAt)
                putExtra(AlarmReceiver.EXTRA_DOSE_ID, currentDoseId)
                putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, currentMedicineName)
                putExtra(AlarmReceiver.EXTRA_GROUP_ID, currentGroupId)
            }
            val takenPendingIntent = PendingIntent.getBroadcast(
                this,
                ("${currentDoseId}_missed_taken").hashCode(),
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val skipIntent = Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_MISSED_SKIP
                putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, currentMedicineId)
                putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, currentScheduledAt)
                putExtra(AlarmReceiver.EXTRA_DOSE_ID, currentDoseId)
                putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, currentMedicineName)
                putExtra(AlarmReceiver.EXTRA_GROUP_ID, currentGroupId)
            }
            val skipPendingIntent = PendingIntent.getBroadcast(
                this,
                ("${currentDoseId}_missed_skip").hashCode(),
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val missedNotif = NotificationCompat.Builder(this, TulsiApp.CHANNEL_ID_UPCOMING_DOSE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Missed reminder: $currentMedicineName")
                .setContentText("Tap to respond or mark dose")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .addAction(0, "Taken", takenPendingIntent)
                .addAction(0, "Skip", skipPendingIntent)
                .build()

            notificationManager.notify(currentDoseId.hashCode(), missedNotif)
        }

        stopSelf()
    }

    private fun stopRingingAndVibration() {
        handler.removeCallbacks(autoStopRunnable)
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        try {
            val audioManager = getSystemService<AudioManager>()
            if (audioFocusRequest != null) {
                audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopRingingAndVibration()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID_ALARM = 1001
        const val ACTION_STOP_ALARM = "com.tulsicare.app.ACTION_STOP_ALARM"
        const val ACTION_DISMISS_ALARM_ACTIVITY = "com.tulsicare.app.ACTION_DISMISS_ALARM_ACTIVITY"

        fun stop(context: Context) {
            val stopIntent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(stopIntent)
        }
    }
}
