package com.tulsicare.app.core.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
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
import com.tulsicare.app.TulsiApp
import com.tulsicare.app.R
import com.tulsicare.app.feature.alarm.AlarmActivity

/**
 * A brief foreground service used ONLY while the alarm is actively ringing/vibrating.
 * Stops immediately once the dose is acted upon, snooze is tapped, or after a 2-minute safety timeout.
 */
class AlarmPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        val medicineId = intent?.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
        val medicineName = intent?.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_NAME) ?: "Medicine"
        val dosage = intent?.getStringExtra(AlarmReceiver.EXTRA_DOSAGE) ?: ""
        val scheduleId = intent?.getStringExtra(AlarmReceiver.EXTRA_SCHEDULE_ID) ?: ""

        val notification = buildAlarmNotification(medicineId, medicineName, dosage, scheduleId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_ALARM,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_ALARM, notification)
        }

        startRingingAndVibration()

        // Safety auto-stop after 2 minutes so this is never a persistent foreground service
        handler.removeCallbacks(autoStopRunnable)
        handler.postDelayed(autoStopRunnable, 120_000L)

        return START_NOT_STICKY
    }

    private fun startRingingAndVibration() {
        try {
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
        } catch (_: Exception) {
            // Fallback gracefully if system audio device is restricted
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildAlarmNotification(
        medicineId: String,
        medicineName: String,
        dosage: String,
        scheduleId: String
    ): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(AlarmReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            medicineId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TulsiApp.CHANNEL_ID_MEDICINE_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tulsi reminder: Time for $medicineName")
            .setContentText(if (dosage.isNotBlank()) "$dosage is due. Take care, together." else "Your dose is due. Take care, together.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
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

        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID_ALARM = 1001
        const val ACTION_STOP_ALARM = "com.tulsicare.app.ACTION_STOP_ALARM"

        fun stop(context: Context) {
            val stopIntent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(stopIntent)
        }
    }
}
