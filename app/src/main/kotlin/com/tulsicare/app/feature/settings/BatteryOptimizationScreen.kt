package com.tulsicare.app.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiButton
import com.tulsicare.app.core.designsystem.component.TulsiListRow
import com.tulsicare.app.core.designsystem.component.TulsiOutlinedButton
import com.tulsicare.app.core.designsystem.component.TulsiSectionHeader
import com.tulsicare.app.core.designsystem.component.TulsiStatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isNotificationsAllowed by remember { mutableStateOf(true) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    var isBatteryIgnoringOptimizations by remember { mutableStateOf(true) }
    var showManufacturerSteps by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        // 1. Notifications
        isNotificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()

        // 2. Exact Alarms
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        canScheduleExactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

        // 3. Battery Optimizations
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryIgnoringOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshPermissions()
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    val colors = TulsiTheme.colors
    val totalIssues = listOf(isNotificationsAllowed, canScheduleExactAlarms, isBatteryIgnoringOptimizations).count { !it }
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reminder reliability",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = TulsiScreenPadding, vertical = 12.dp)
        ) {
            // Overall Status Top Banner
            item(key = "overall_status") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(TulsiBorderWidth, colors.border, RoundedCornerShape(TulsiCornerRadius))
                        .background(colors.surface, RoundedCornerShape(TulsiCornerRadius))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (totalIssues == 0) colors.statusTaken else colors.statusSnoozed,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (totalIssues == 0) "All set — alarms and notifications are ready" else "$totalIssues thing${if (totalIssues > 1) "s need" else " needs"} attention",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                TulsiSectionHeader(title = "Core permissions", horizontalPadding = 0.dp)
            }

            // 1. Notifications Row
            item(key = "notif_row") {
                ReliabilityItem(
                    title = "Notifications",
                    isAllowed = isNotificationsAllowed,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
                HorizontalDivider(thickness = TulsiBorderWidth, color = colors.border)
            }

            // 2. Exact Alarms Row
            item(key = "alarm_row") {
                ReliabilityItem(
                    title = "Exact alarms",
                    isAllowed = canScheduleExactAlarms,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
                HorizontalDivider(thickness = TulsiBorderWidth, color = colors.border)
            }

            // 3. Battery Optimization Row
            item(key = "battery_row") {
                ReliabilityItem(
                    title = "Battery optimization",
                    isAllowed = isBatteryIgnoringOptimizations,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
                HorizontalDivider(thickness = TulsiBorderWidth, color = colors.border)
            }

            // Collapsible Manufacturer-specific steps
            item(key = "oem_steps") {
                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManufacturerSteps = !showManufacturerSteps }
                        .border(TulsiBorderWidth, colors.border, RoundedCornerShape(TulsiCornerRadius))
                        .background(colors.surface, RoundedCornerShape(TulsiCornerRadius))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extra steps for your phone ($manufacturer)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Keep alarms ringing when the phone sleeps",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    Icon(
                        imageVector = if (showManufacturerSteps) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = showManufacturerSteps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 8.dp, end = 8.dp)
                    ) {
                        val steps = getManufacturerInstructions(manufacturer)
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = step,
                                    fontSize = 14.sp,
                                    color = colors.textSecondary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReliabilityItem(
    title: String,
    isAllowed: Boolean,
    onFix: () -> Unit
) {
    val colors = TulsiTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (isAllowed) colors.statusTaken else colors.statusMissed,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAllowed) "Allowed" else "Needs attention",
                    fontSize = 14.sp,
                    color = if (isAllowed) colors.textSecondary else colors.statusMissed
                )
            }
        }

        if (!isAllowed) {
            Spacer(modifier = Modifier.width(12.dp))
            TulsiOutlinedButton(
                text = "Allow",
                onClick = onFix
            )
        }
    }
}

private fun getManufacturerInstructions(manufacturer: String): List<String> {
    val brand = manufacturer.lowercase()
    return when {
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> listOf(
            "Open Security app > Manage apps > Tulsi > Enable 'Autostart'.",
            "Under Battery Saver, select 'No restrictions'.",
            "Lock Tulsi in the recent apps tray."
        )
        brand.contains("samsung") -> listOf(
            "Open Settings > Apps > Tulsi > Battery > Select 'Unrestricted'.",
            "Go to Settings > Device Care > Battery > Background usage limits > Add Tulsi to 'Never sleeping apps'."
        )
        brand.contains("oneplus") || brand.contains("oppo") || brand.contains("realme") -> listOf(
            "Open Settings > Battery > More settings > Optimize battery use > Select 'Don't optimize' for Tulsi.",
            "Enable 'Allow background activity' and 'Allow auto-launch'."
        )
        brand.contains("vivo") || brand.contains("iqoo") -> listOf(
            "Open Settings > Battery > High background power consumption > Allow Tulsi.",
            "Open Settings > Applications > Autostart > Enable for Tulsi."
        )
        brand.contains("huawei") || brand.contains("honor") -> listOf(
            "Open Settings > Battery > App launch > Set Tulsi to 'Manage manually'.",
            "Enable Auto-launch, Secondary launch, and Run in background."
        )
        else -> listOf(
            "Open Settings > Apps > Tulsi > Battery > Choose 'Unrestricted'.",
            "Ensure system alarms and notifications are permitted."
        )
    }
}

// -----------------------------------------------------------------------------
// Previews: Light, Dark, 1.5x Font Scale
// -----------------------------------------------------------------------------

@Preview(name = "Reliability - Light", showBackground = true)
@Composable
fun BatteryOptimizationScreenPreviewLight() {
    TulsiTheme(darkTheme = false) {
        BatteryOptimizationScreen(onNavigateBack = {})
    }
}

@Preview(name = "Reliability - Dark", showBackground = true)
@Composable
fun BatteryOptimizationScreenPreviewDark() {
    TulsiTheme(darkTheme = true) {
        BatteryOptimizationScreen(onNavigateBack = {})
    }
}

@Preview(name = "Reliability - 1.5x Font Scale", fontScale = 1.5f, showBackground = true)
@Composable
fun BatteryOptimizationScreenPreviewLargeFont() {
    TulsiTheme(darkTheme = false) {
        BatteryOptimizationScreen(onNavigateBack = {})
    }
}
