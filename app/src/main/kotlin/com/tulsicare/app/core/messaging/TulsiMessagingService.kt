package com.tulsicare.app.core.messaging

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.tulsicare.app.TulsiApp
import com.tulsicare.app.MainActivity
import com.tulsicare.app.R
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TulsiMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenManager.updateToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"] ?: return
        when (type) {
            TYPE_SCHEDULE_SYNC -> {
                // High-priority push when family schedule changes: resync and reschedule alarms
                handleScheduleSync()
            }
            TYPE_DOSE_TAKEN -> {
                val memberName = message.data["memberName"] ?: "Family member"
                val medicineName = message.data["medicineName"] ?: "medicine"
                val time = message.data["time"] ?: "just now"
                showNotification(
                    title = "Tulsi: $memberName took $medicineName",
                    content = "Taken at $time. Take care, together.",
                    channelId = TulsiApp.CHANNEL_ID_FAMILY_ESCALATION,
                    notificationId = (memberName + medicineName).hashCode()
                )
            }
            TYPE_ESCALATION -> {
                val memberName = message.data["memberName"] ?: "Family member"
                val medicineName = message.data["medicineName"] ?: "medicine"
                val phoneNumber = message.data["phoneNumber"] ?: ""
                showEscalationNotification(
                    title = "Tulsi reminder: $memberName's $medicineName is due",
                    content = "Scheduled dose was due. Tap to check in or call.",
                    phoneNumber = phoneNumber,
                    notificationId = (memberName + medicineName + "escalation").hashCode()
                )
            }
        }
    }

    private fun handleScheduleSync() {
        // Trigger rescheduling of local alarms even when the app is closed
        ExactAlarmScheduler.rescheduleAll(applicationContext)
    }

    private fun showNotification(
        title: String,
        content: String,
        channelId: String,
        notificationId: Int
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService<NotificationManager>()
        notificationManager?.notify(notificationId, notification)
    }

    private fun showEscalationNotification(
        title: String,
        content: String,
        phoneNumber: String,
        notificationId: Int
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, TulsiApp.CHANNEL_ID_FAMILY_ESCALATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)

        // Action Dial: CALL button opens phone dialer with number pre-filled (No CALL_PHONE permission required)
        if (phoneNumber.isNotBlank()) {
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            val callPendingIntent = PendingIntent.getActivity(
                this,
                notificationId + 1,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Call Now", callPendingIntent)
        }

        val notificationManager = getSystemService<NotificationManager>()
        notificationManager?.notify(notificationId, builder.build())
    }

    companion object {
        const val TYPE_SCHEDULE_SYNC = "schedule_sync"
        const val TYPE_DOSE_TAKEN = "dose_taken"
        const val TYPE_ESCALATION = "escalation"
    }
}
