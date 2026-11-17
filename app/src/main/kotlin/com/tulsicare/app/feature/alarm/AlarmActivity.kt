package com.tulsicare.app.feature.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.tulsicare.app.core.alarm.AlarmPlaybackService
import com.tulsicare.app.core.alarm.AlarmReceiver
import com.tulsicare.app.core.alarm.AlarmType
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.generateDoseId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var doseEventRepository: DoseEventRepository
    @Inject lateinit var medicineRepository: MedicineRepository
    @Inject lateinit var authRepository: AuthRepository

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowWhenLocked()

        val filter = IntentFilter(AlarmPlaybackService.ACTION_DISMISS_ALARM_ACTIVITY)
        ContextCompat.registerReceiver(this, dismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val medicineId = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_ID).orEmpty()
        val medicineName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_NAME) ?: "Medicine"
        val dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE).orEmpty()
        val scheduleId = intent.getStringExtra(AlarmReceiver.EXTRA_SCHEDULE_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
        val doseId = intent.getStringExtra(AlarmReceiver.EXTRA_DOSE_ID).orEmpty().ifBlank {
            if (medicineId.isNotBlank()) generateDoseId(medicineId, scheduledAt) else scheduleId
        }
        val personName = intent.getStringExtra(AlarmReceiver.EXTRA_PERSON_NAME).orEmpty()
        val groupId = intent.getStringExtra(AlarmReceiver.EXTRA_GROUP_ID).orEmpty()

        val keyguardManager = getSystemService<KeyguardManager>()
        val powerManager = getSystemService<PowerManager>()
        val isScreenOn = powerManager?.isInteractive ?: true
        val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false
        val isUnlockedPopup = intent.getBooleanExtra(AlarmReceiver.EXTRA_IS_UNLOCKED_POPUP, false) || (isScreenOn && !isKeyguardLocked)

        setContent {
            TulsiTheme(darkTheme = true) {
                if (isUnlockedPopup) {
                    // Phone unlocked & in active use: Compact popup modal over current app
                    CompactAlarmPopupContent(
                        medicineName = medicineName,
                        dosage = dosage,
                        personName = personName,
                        onTaken = {
                            handleTaken(medicineId, scheduledAt, doseId)
                        },
                        onSnooze = { minutes ->
                            handleSnooze(medicineId, scheduledAt, doseId, groupId, medicineName, dosage, personName, minutes)
                        },
                        onSkip = {
                            handleSkip(medicineId, scheduledAt, doseId)
                        }
                    )
                } else {
                    // Phone locked or screen off: Full-screen alarm over lock screen
                    AlarmReminderContent(
                        medicineName = medicineName,
                        dosage = dosage,
                        personName = personName,
                        onTaken = {
                            handleTaken(medicineId, scheduledAt, doseId)
                        },
                        onSnooze = { minutes ->
                            handleSnooze(medicineId, scheduledAt, doseId, groupId, medicineName, dosage, personName, minutes)
                        },
                        onSkip = {
                            handleSkip(medicineId, scheduledAt, doseId)
                        }
                    )
                }
            }
        }
    }

    private fun handleTaken(medicineId: String, scheduledAt: Long, doseId: String) {
        AlarmPlaybackService.stop(this)
        getSystemService<NotificationManager>()?.cancel(doseId.hashCode())
        lifecycleScope.launch {
            try {
                val userId = authRepository.getCurrentUser()?.id.orEmpty()
                doseEventRepository.recordDose(
                    medicineId = medicineId,
                    scheduledAt = scheduledAt,
                    status = DoseStatus.TAKEN,
                    actedBy = userId
                )
                ExactAlarmScheduler.cancelAlarmsForDose(this@AlarmActivity, doseId)
            } catch (_: Exception) {}
            finishAndRemoveTask()
        }
    }

    private fun handleSnooze(
        medicineId: String,
        scheduledAt: Long,
        doseId: String,
        groupId: String,
        medicineName: String,
        dosage: String,
        personName: String,
        minutes: Int
    ) {
        AlarmPlaybackService.stop(this)
        getSystemService<NotificationManager>()?.cancel(doseId.hashCode())
        val snoozeTrigger = System.currentTimeMillis() + (minutes * 60 * 1000L)
        lifecycleScope.launch {
            try {
                val userId = authRepository.getCurrentUser()?.id.orEmpty()
                doseEventRepository.recordDose(
                    medicineId = medicineId,
                    scheduledAt = scheduledAt,
                    status = DoseStatus.SNOOZED,
                    actedBy = userId,
                    snoozeMinutes = minutes
                )
                ExactAlarmScheduler.scheduleAlarm(
                    context = this@AlarmActivity,
                    userId = userId,
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
            } catch (_: Exception) {}
            finishAndRemoveTask()
        }
    }

    private fun handleSkip(medicineId: String, scheduledAt: Long, doseId: String) {
        AlarmPlaybackService.stop(this)
        getSystemService<NotificationManager>()?.cancel(doseId.hashCode())
        lifecycleScope.launch {
            try {
                val userId = authRepository.getCurrentUser()?.id.orEmpty()
                doseEventRepository.recordDose(
                    medicineId = medicineId,
                    scheduledAt = scheduledAt,
                    status = DoseStatus.SKIPPED,
                    actedBy = userId
                )
                ExactAlarmScheduler.cancelAlarmsForDose(this@AlarmActivity, doseId)
            } catch (_: Exception) {}
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {}
        AlarmPlaybackService.stop(this)
        super.onDestroy()
    }

    private fun turnScreenOnAndShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

/**
 * Minimalist Pure Black Full-screen Alarm Screen (Used when phone is locked or screen off)
 */
@Composable
fun AlarmReminderContent(
    medicineName: String,
    dosage: String,
    personName: String = "",
    onTaken: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    var selectedSnoozeMinutes by remember { mutableIntStateOf(10) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Time & Medicine Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTime,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFAFAFA),
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = medicineName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val subtext = buildString {
                    if (dosage.isNotBlank()) append(dosage)
                    if (personName.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("For $personName")
                    }
                }

                if (subtext.isNotBlank()) {
                    Text(
                        text = subtext,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF9A9A9A),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom: Three Big Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Taken: Solid White with Black text
                Button(
                    onClick = onTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp),
                    shape = RoundedCornerShape(TulsiCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Taken",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                // 2. Snooze selector row + button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(5, 10, 15).forEach { mins ->
                        val isSelected = selectedSnoozeMinutes == mins
                        OutlinedButton(
                            onClick = { selectedSnoozeMinutes = mins },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(TulsiCornerRadius),
                            border = BorderStroke(
                                TulsiBorderWidth,
                                if (isSelected) Color.White else Color(0xFF404040)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) Color(0xFF262626) else Color.Transparent,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "${mins}m",
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onSnooze(selectedSnoozeMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    shape = RoundedCornerShape(TulsiCornerRadius),
                    border = BorderStroke(TulsiBorderWidth, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Snooze ($selectedSnoozeMinutes min)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                // 3. Skip: Quiet text button
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(TulsiCornerRadius),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF9A9A9A)
                    )
                ) {
                    Text(
                        text = "Skip dose",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF9A9A9A)
                    )
                }
            }
        }
    }
}

/**
 * Compact Popup Dialog Alarm Content (Used when phone is unlocked and actively in use).
 * Does not take over full screen; renders as a translucent bottom card modal.
 */
@Composable
fun CompactAlarmPopupContent(
    medicineName: String,
    dosage: String,
    personName: String = "",
    onTaken: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    var selectedSnoozeMinutes by remember { mutableIntStateOf(10) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141414),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row: Title + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dose reminder",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF888888)
                    )
                    Text(
                        text = currentTime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCCCCCC)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Medicine Name
                Text(
                    text = medicineName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                val subtext = buildString {
                    if (dosage.isNotBlank()) append(dosage)
                    if (personName.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("For $personName")
                    }
                }
                if (subtext.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtext,
                        fontSize = 15.sp,
                        color = Color(0xFF999999),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action 1: Taken
                Button(
                    onClick = onTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(TulsiCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Taken",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Snooze options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15).forEach { mins ->
                        val isSelected = selectedSnoozeMinutes == mins
                        OutlinedButton(
                            onClick = {
                                selectedSnoozeMinutes = mins
                                onSnooze(mins)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(TulsiCornerRadius),
                            border = BorderStroke(
                                TulsiBorderWidth,
                                if (isSelected) Color.White else Color(0xFF383838)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Snooze ${mins}m",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action 3: Skip
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF888888)
                    )
                ) {
                    Text(
                        text = "Skip dose",
                        fontSize = 14.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    }
}

@Preview(name = "Locked Alarm Screen", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AlarmReminderContentPreview() {
    TulsiTheme(darkTheme = true) {
        AlarmReminderContent(
            medicineName = "Metformin",
            dosage = "500mg",
            personName = "Mom",
            onTaken = {},
            onSnooze = {},
            onSkip = {}
        )
    }
}

@Preview(name = "Compact Popup Dialog", showBackground = false)
@Composable
fun CompactAlarmPopupContentPreview() {
    TulsiTheme(darkTheme = true) {
        CompactAlarmPopupContent(
            medicineName = "Metformin",
            dosage = "500mg",
            personName = "Mom",
            onTaken = {},
            onSnooze = {},
            onSkip = {}
        )
    }
}
