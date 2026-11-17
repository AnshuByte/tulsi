package com.tulsicare.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tulsicare.app.BuildConfig
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiButton
import com.tulsicare.app.core.designsystem.component.TulsiListRow
import com.tulsicare.app.core.designsystem.component.TulsiOutlinedButton
import com.tulsicare.app.core.designsystem.component.TulsiSectionHeader
import com.tulsicare.app.core.designsystem.component.TulsiTextButton
import com.tulsicare.app.core.model.User

@Composable
fun SettingsScreen(
    currentUser: User?,
    isSimpleMode: Boolean,
    preReminderLeadMinutes: Int = 10,
    onToggleSimpleMode: (Boolean) -> Unit,
    onSetPreReminderLeadMinutes: (Int) -> Unit = {},
    onUpdateDisplayName: (String) -> Unit = {},
    onNavigateToReliability: () -> Unit,
    onTriggerTestAlarm: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors
    var isSigningOut by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showMedicalDisclaimer by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPreReminderDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize().background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Header
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = TulsiScreenPadding, end = TulsiScreenPadding, top = 8.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "Settings",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            // Profile row
            item(key = "profile") {
                val displayName = currentUser?.displayName?.ifBlank { "User" } ?: "User"
                val email = currentUser?.email?.ifBlank { "Signed in" } ?: "Signed in"
                val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

                TulsiListRow(
                    title = displayName,
                    subtitle = email,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colors.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    },
                    trailingContent = {
                        TulsiTextButton(
                            text = "Edit",
                            onClick = {
                                editedName = displayName
                                showEditNameDialog = true
                            }
                        )
                    },
                    onClick = {
                        editedName = displayName
                        showEditNameDialog = true
                    },
                    showDivider = true
                )
            }

            // Accessibility: Simple Mode Toggle
            item(key = "simple_mode") {
                TulsiListRow(
                    title = "Simple Mode",
                    subtitle = "Larger buttons and simplified layout for seniors",
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.TouchApp,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = isSimpleMode,
                            onCheckedChange = onToggleSimpleMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.buttonPrimary,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface
                            )
                        )
                    },
                    showDivider = true
                )
            }

            // Reminder Reliability
            item(key = "reliability") {
                TulsiListRow(
                    title = "Reminder reliability",
                    subtitle = "Notifications, exact alarms & battery optimization",
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.BatteryAlert,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    onClick = onNavigateToReliability,
                    showDivider = true
                )
            }

            // Pre-reminder lead time (Off / 5 / 10 / 15 / 30 min)
            item(key = "pre_reminder") {
                val leadText = if (preReminderLeadMinutes <= 0) "Off" else "$preReminderLeadMinutes minutes before each dose"
                TulsiListRow(
                    title = "Pre-reminder",
                    subtitle = leadText,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    onClick = { showPreReminderDialog = true },
                    showDivider = true
                )
            }

            // Privacy & Medical Disclaimer
            item(key = "disclaimer") {
                TulsiListRow(
                    title = "Privacy & medical disclaimer",
                    subtitle = "Emergency information and health liability notice",
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    onClick = { showMedicalDisclaimer = true },
                    showDivider = true
                )
            }

            // About
            item(key = "about") {
                TulsiListRow(
                    title = "About Tulsi",
                    subtitle = "Version 1.0.0 · $0 free forever family care",
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    onClick = { showAboutDialog = true },
                    showDivider = true
                )
            }

            // Developer Tools (strictly DEBUG builds only!)
            if (BuildConfig.DEBUG) {
                item(key = "developer_section") {
                    Spacer(modifier = Modifier.height(16.dp))
                    TulsiSectionHeader(title = "Developer")
                }

                item(key = "dev_test_alarm") {
                    TulsiListRow(
                        title = "Test Alarm Screen",
                        subtitle = "Simulates incoming medicine alarm with full sound & buttons",
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingContent = {
                            TulsiOutlinedButton(
                                text = "Trigger",
                                onClick = onTriggerTestAlarm
                            )
                        },
                        showDivider = true
                    )
                }
            }

            // Log Out Button
            item(key = "logout") {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TulsiScreenPadding),
                    contentAlignment = Alignment.Center
                ) {
                    TulsiTextButton(
                        text = "Sign out",
                        onClick = { if (!isSigningOut) showLogoutDialog = true },
                        textColor = colors.statusMissed
                    )
                }
            }
        }

        // Dialog: Medical Disclaimer
        if (showMedicalDisclaimer) {
            AlertDialog(
                onDismissRequest = { showMedicalDisclaimer = false },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "Medical Disclaimer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "Tulsi is a collaborative family reminder tool designed to assist caregivers and patients with medication adherence. It is not a substitute for professional medical advice, diagnosis, or clinical care. In any medical emergency, always call local emergency services immediately.",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TulsiButton(
                        text = "Understood",
                        onClick = { showMedicalDisclaimer = false }
                    )
                }
            )
        }

        // Dialog: About Tulsi
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "About Tulsi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Tulsi is crafted to give families total peace of mind with robust medicine reminders and caregiver escalation.",
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Built on open standards and 100% free $0 cloud infrastructure (Firebase Spark & Cloudflare Workers).",
                            fontSize = 12.sp,
                            color = colors.textDisabled
                        )
                    }
                },
                confirmButton = {
                    TulsiButton(
                        text = "Close",
                        onClick = { showAboutDialog = false }
                    )
                }
            )
        }

        // Dialog: Confirm Logout
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "Sign Out?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "You will need to sign in again to view your family schedule and alarms.",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TulsiButton(
                        text = "Sign out",
                        enabled = !isSigningOut,
                        onClick = {
                            if (!isSigningOut) {
                                isSigningOut = true
                                showLogoutDialog = false
                                onSignOut()
                            }
                        }
                    )
                },
                dismissButton = {
                    TulsiTextButton(
                        text = "Cancel",
                        onClick = { showLogoutDialog = false }
                    )
                }
            )
        }

        // Pre-reminder Lead Time Dialog
        if (showPreReminderDialog) {
            AlertDialog(
                onDismissRequest = { showPreReminderDialog = false },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Pre-reminder lead time",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to "Off",
                            5 to "5 minutes before",
                            10 to "10 minutes before (Default)",
                            15 to "15 minutes before",
                            30 to "30 minutes before"
                        ).forEach { (mins, label) ->
                            val isSelected = preReminderLeadMinutes == mins
                            TulsiListRow(
                                title = label,
                                onClick = {
                                    onSetPreReminderLeadMinutes(mins)
                                    showPreReminderDialog = false
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Text("✓", color = colors.buttonPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TulsiTextButton(
                        text = "Close",
                        onClick = { showPreReminderDialog = false }
                    )
                }
            )
        }

        // Edit Name Dialog
        if (showEditNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Edit name",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            singleLine = true,
                            placeholder = { Text("Enter your name", color = colors.textSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.buttonPrimary,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.buttonPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TulsiButton(
                        text = "Save",
                        onClick = {
                            val clean = editedName.trim()
                            if (clean.isNotBlank()) {
                                onUpdateDisplayName(clean)
                                showEditNameDialog = false
                            }
                        }
                    )
                },
                dismissButton = {
                    TulsiTextButton(
                        text = "Cancel",
                        onClick = { showEditNameDialog = false }
                    )
                }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Previews: Light, Dark, 1.5x Font Scale
// -----------------------------------------------------------------------------

@Preview(name = "Settings - Light", showBackground = true)
@Composable
fun SettingsScreenPreviewLight() {
    TulsiTheme(darkTheme = false) {
        SettingsScreen(
            currentUser = User(id = "1", displayName = "Asha Sharma", email = "asha@example.com"),
            isSimpleMode = false,
            onToggleSimpleMode = {},
            onNavigateToReliability = {},
            onTriggerTestAlarm = {},
            onSignOut = {}
        )
    }
}

@Preview(name = "Settings - Dark", showBackground = true)
@Composable
fun SettingsScreenPreviewDark() {
    TulsiTheme(darkTheme = true) {
        SettingsScreen(
            currentUser = User(id = "1", displayName = "Asha Sharma", email = "asha@example.com"),
            isSimpleMode = false,
            onToggleSimpleMode = {},
            onNavigateToReliability = {},
            onTriggerTestAlarm = {},
            onSignOut = {}
        )
    }
}

@Preview(name = "Settings - 1.5x Font Scale", fontScale = 1.5f, showBackground = true)
@Composable
fun SettingsScreenPreviewLargeFont() {
    TulsiTheme(darkTheme = false) {
        SettingsScreen(
            currentUser = User(id = "1", displayName = "Asha Sharma", email = "asha@example.com"),
            isSimpleMode = false,
            onToggleSimpleMode = {},
            onNavigateToReliability = {},
            onTriggerTestAlarm = {},
            onSignOut = {}
        )
    }
}
