package com.tulsicare.app.feature.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tulsicare.app.core.alarm.AlarmPlaybackService
import com.tulsicare.app.core.alarm.AlarmReceiver
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.PrimaryTeal
import com.tulsicare.app.core.model.DoseStatus
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

        setContent {
            TulsiTheme {
                AlarmReminderContent(
                    medicineName = medicineName,
                    dosage = dosage,
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

@Composable
fun AlarmReminderContent(
    medicineName: String,
    dosage: String,
    onTaken: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkip: () -> Unit
) {
    var showSnoozeOptions by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Friendly Pill Alarm Visual
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(PrimaryTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Text(
                    text = "Time for your medicine",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = medicineName,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (dosage.isNotBlank()) {
                            Text(
                                text = dosage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (showSnoozeOptions) {
                    // Snooze interval picker (5 / 10 / 15 min)
                    Text(
                        text = "Snooze for how long?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(5, 10, 15).forEach { min ->
                            OutlinedButton(
                                onClick = { onSnooze(min) },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("$min min", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Primary Action: Taken
                Button(
                    onClick = onTaken,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TulsiTheme.semanticColors.taken
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mark as Taken",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

                // Secondary Action: Snooze
                Button(
                    onClick = { showSnoozeOptions = !showSnoozeOptions },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TulsiTheme.semanticColors.snooze
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showSnoozeOptions) "Cancel Snooze" else "Snooze (10 min)",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

                // Tertiary Action: Skip
                OutlinedButton(
                    onClick = onSkip,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Skip this dose",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TulsiTheme.semanticColors.skipped
                    )
                }
            }
        }
    }
}
