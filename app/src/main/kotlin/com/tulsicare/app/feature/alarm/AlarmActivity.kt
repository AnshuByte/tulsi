package com.tulsicare.app.feature.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tulsicare.app.core.alarm.AlarmPlaybackService
import com.tulsicare.app.core.alarm.AlarmReceiver
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.model.DoseStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var doseEventRepository: DoseEventRepository
    @Inject lateinit var medicineRepository: MedicineRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowWhenLocked()

        val medicineId = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_ID) ?: ""
        val medicineName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICINE_NAME) ?: "Medicine"
        val dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE) ?: ""
        val scheduleId = intent.getStringExtra(AlarmReceiver.EXTRA_SCHEDULE_ID) ?: ""
        val personName = intent.getStringExtra("EXTRA_PERSON_NAME") ?: ""

        setContent {
            TulsiTheme(darkTheme = true) {
                AlarmReminderContent(
                    medicineName = medicineName,
                    dosage = dosage,
                    personName = personName,
                    onTaken = {
                        AlarmPlaybackService.stop(this)
                        lifecycleScope.launch {
                            try {
                                val user = authRepository.getCurrentUser()
                                val userId = user?.id.orEmpty()
                                val medicine = medicineRepository.getMedicineById(medicineId)
                                val groupId = medicine?.groupId.orEmpty().ifBlank { user?.currentGroupId.orEmpty() }
                                val eventId = if (scheduleId.isNotBlank()) scheduleId else UUID.randomUUID().toString()
                                doseEventRepository.recordDoseAction(groupId, eventId, DoseStatus.TAKEN, userId)
                            } catch (_: Exception) {}
                            finish()
                        }
                    },
                    onSnooze = { minutes ->
                        AlarmPlaybackService.stop(this)
                        val snoozeTrigger = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        ExactAlarmScheduler.scheduleAlarm(
                            context = this,
                            triggerAtMillis = snoozeTrigger,
                            medicineId = medicineId,
                            medicineName = medicineName,
                            dosage = dosage,
                            scheduleId = "${scheduleId}_snooze_${minutes}"
                        )
                        lifecycleScope.launch {
                            try {
                                val user = authRepository.getCurrentUser()
                                val medicine = medicineRepository.getMedicineById(medicineId)
                                val groupId = medicine?.groupId.orEmpty().ifBlank { user?.currentGroupId.orEmpty() }
                                doseEventRepository.snoozeDose(groupId, scheduleId, minutes)
                            } catch (_: Exception) {}
                            finish()
                        }
                    },
                    onSkip = {
                        AlarmPlaybackService.stop(this)
                        lifecycleScope.launch {
                            try {
                                val user = authRepository.getCurrentUser()
                                val userId = user?.id.orEmpty()
                                val medicine = medicineRepository.getMedicineById(medicineId)
                                val groupId = medicine?.groupId.orEmpty().ifBlank { user?.currentGroupId.orEmpty() }
                                val eventId = if (scheduleId.isNotBlank()) scheduleId else UUID.randomUUID().toString()
                                doseEventRepository.recordDoseAction(groupId, eventId, DoseStatus.SKIPPED, userId)
                            } catch (_: Exception) {}
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
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
 * Minimalist Pure Black Alarm Screen:
 * - Pure black background
 * - Large time
 * - Medicine name and person
 * - Three big buttons: Taken (solid white), Snooze (outlined), Skip (text)
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
                // Large Time (44sp)
                Text(
                    text = currentTime,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFAFAFA),
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Medicine Name (28sp title)
                Text(
                    text = medicineName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dose & Person Subtext
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

                // 2. Snooze: Outlined White with transparent background
                OutlinedButton(
                    onClick = { onSnooze(10) }, // Default 10 min snooze
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp),
                    shape = RoundedCornerShape(TulsiCornerRadius),
                    border = BorderStroke(TulsiBorderWidth, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Snooze (10 min)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                // 3. Skip: Quiet text button
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
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

// -----------------------------------------------------------------------------
// Preview: Pure Black Alarm Screen
// -----------------------------------------------------------------------------

@Preview(name = "Alarm Screen", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AlarmReminderContentPreview() {
    TulsiTheme(darkTheme = true) {
        AlarmReminderContent(
            medicineName = "Metformin",
            dosage = "500mg",
            personName = "Ravi",
            onTaken = {},
            onSnooze = {},
            onSkip = {}
        )
    }
}
