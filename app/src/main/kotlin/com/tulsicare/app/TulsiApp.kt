package com.tulsicare.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TulsiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService<NotificationManager>() ?: return

            // Channel 1: Dose alarm (Full-screen alarm sound, high importance, bypass DND)
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_DOSE_ALARM,
                "Dose alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud, gentle alarm when your medicine is due"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(alarmSoundUri, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Channel 2: Upcoming dose (Pre-reminder, default importance, no sound loop)
            val upcomingChannel = NotificationChannel(
                CHANNEL_ID_UPCOMING_DOSE,
                "Upcoming dose",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle pre-reminder before your dose is due"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Channel 3: Family Escalation & Heads-up alerts
            val escalationChannel = NotificationChannel(
                CHANNEL_ID_FAMILY_ESCALATION,
                "Tulsi Family Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warm realtime updates when a family member takes or misses a dose"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Channel 4: Background sync and general updates
            val syncChannel = NotificationChannel(
                CHANNEL_ID_SYNC,
                "Tulsi Sync & Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quiet background synchronization for medicine schedules"
            }

            notificationManager.createNotificationChannels(
                listOf(alarmChannel, upcomingChannel, escalationChannel, syncChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_ID_DOSE_ALARM = "tulsi_dose_alarm"
        const val CHANNEL_ID_MEDICINE_ALARM = "tulsi_dose_alarm" // Backwards compatibility alias
        const val CHANNEL_ID_UPCOMING_DOSE = "tulsi_upcoming_dose"
        const val CHANNEL_ID_FAMILY_ESCALATION = "tulsi_family_escalation"
        const val CHANNEL_ID_SYNC = "tulsi_sync"
    }
}
