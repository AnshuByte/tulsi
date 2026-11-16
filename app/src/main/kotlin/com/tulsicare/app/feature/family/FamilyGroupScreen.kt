package com.tulsicare.app.feature.family

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiButton
import com.tulsicare.app.core.designsystem.component.TulsiEmptyState
import com.tulsicare.app.core.designsystem.component.TulsiListRow
import com.tulsicare.app.core.designsystem.component.TulsiOutlinedButton
import com.tulsicare.app.core.designsystem.component.TulsiSectionHeader
import com.tulsicare.app.core.designsystem.component.TulsiTextButton
import com.tulsicare.app.core.designsystem.component.TulsiTextField
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.User

@Composable
fun FamilyGroupScreen(
    viewModel: FamilyGroupViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    FamilyGroupScreenContent(
        uiState = uiState,
        onCreateGroup = viewModel::createGroup,
        onJoinGroup = viewModel::joinGroup,
        onLeaveGroup = viewModel::leaveGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onShowCreateDialog = viewModel::setShowCreateDialog,
        onShowJoinDialog = viewModel::setShowJoinDialog,
        onShowLeaveDialog = viewModel::setShowLeaveDialog,
        onShowDeleteDialog = viewModel::setShowDeleteDialog,
        onCopyInviteCode = { code ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Tulsi Invite Code", code))
            Toast.makeText(context, "Invite code copied", Toast.LENGTH_SHORT).show()
        },
        onShareInviteCode = { groupName, code ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Join our family '$groupName' on Tulsi medicine reminder app! Use invite code: $code"
                )
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Invite Code"))
        },
        modifier = modifier
    )
}

@Composable
fun FamilyGroupScreenContent(
    uiState: FamilyGroupUiState,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onShowCreateDialog: (Boolean) -> Unit,
    onShowJoinDialog: (Boolean) -> Unit,
    onShowLeaveDialog: (Boolean) -> Unit,
    onShowDeleteDialog: (Boolean) -> Unit,
    onCopyInviteCode: (String) -> Unit,
    onShareInviteCode: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors
    var newGroupName by remember { mutableStateOf("") }
    var joinInviteCode by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize().background(colors.background)
    ) {
        if (uiState.group == null) {
            // No family group yet: Calm empty state with options to Create or Join
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TulsiScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TulsiEmptyState(
                    title = "Family Circle",
                    message = "Coordinate medicine schedules, log doses together, and get caregiver peace of mind."
                )

                Spacer(modifier = Modifier.height(32.dp))

                TulsiButton(
                    text = "Create a family",
                    onClick = { onShowCreateDialog(true) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                TulsiOutlinedButton(
                    text = "Join with invite code",
                    onClick = { onShowJoinDialog(true) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!uiState.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.errorMessage,
                        fontSize = 14.sp,
                        color = colors.statusMissed,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Group exists: Monospace invite code + ONE members list
            val group = uiState.group
            val currentUserId = uiState.currentUser?.id.orEmpty()
            val deduplicatedMembers = uiState.members.distinctBy { it.userId }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                // Header: Group Name
                item(key = "group_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = TulsiScreenPadding, end = TulsiScreenPadding, top = 8.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = group.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${deduplicatedMembers.size} family members",
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                // Invite Code Box (High contrast, monospace, full-width side-by-side buttons)
                item(key = "invite_box") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TulsiScreenPadding)
                            .border(TulsiBorderWidth, colors.border, RoundedCornerShape(TulsiCornerRadius))
                            .background(colors.surface, RoundedCornerShape(TulsiCornerRadius))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "INVITE CODE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Large monospace letter-spaced code
                        Text(
                            text = group.inviteCode,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textPrimary,
                            letterSpacing = 4.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Two side-by-side full-width action buttons (NO vertical text)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TulsiOutlinedButton(
                                text = "Copy",
                                onClick = { onCopyInviteCode(group.inviteCode) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            TulsiOutlinedButton(
                                text = "Share",
                                onClick = { onShareInviteCode(group.name, group.inviteCode) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Error / Success message banner
                if (!uiState.errorMessage.isNullOrBlank()) {
                    item(key = "error_message") {
                        Text(
                            text = uiState.errorMessage,
                            fontSize = 14.sp,
                            color = colors.statusMissed,
                            modifier = Modifier.padding(horizontal = TulsiScreenPadding, vertical = 8.dp)
                        )
                    }
                }

                // Section: ONE Members List
                item(key = "members_header") {
                    Spacer(modifier = Modifier.height(16.dp))
                    TulsiSectionHeader(title = "Members")
                }

                items(deduplicatedMembers, key = { it.userId }) { member ->
                    val isYou = member.userId == currentUserId
                    val initial = member.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
                    val roleLabel = if (member.role == MemberRole.ADMIN) "Admin" else "Member"
                    val patientSubtitle = if (member.isPatient) "Patient" else "Caregiver"

                    TulsiListRow(
                        title = member.displayName,
                        subtitle = patientSubtitle,
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(colors.surface, CircleShape)
                                    .border(TulsiBorderWidth, colors.border, CircleShape),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isYou) {
                                    Text(
                                        text = "You",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = colors.textSecondary
                                    )
                                }
                                Text(
                                    text = roleLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (member.role == MemberRole.ADMIN) colors.textPrimary else colors.textSecondary
                                )
                            }
                        },
                        showDivider = true
                    )
                }

                // Leave or Delete Family button at the bottom
                if (uiState.isAdmin) {
                    item(key = "delete_button") {
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TulsiScreenPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            TulsiTextButton(
                                text = "Delete family",
                                onClick = { onShowDeleteDialog(true) },
                                textColor = colors.statusMissed
                            )
                        }
                    }
                } else {
                    item(key = "leave_button") {
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TulsiScreenPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            TulsiTextButton(
                                text = "Leave family",
                                onClick = { onShowLeaveDialog(true) },
                                textColor = colors.statusMissed
                            )
                        }
                    }
                }
            }
        }

        // Dialog: Create Family
        if (uiState.showCreateDialog) {
            AlertDialog(
                onDismissRequest = { onShowCreateDialog(false) },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "Create Family",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter a name for your family care group.",
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TulsiTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            placeholder = "e.g. Sharma Family",
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TulsiButton(
                        text = "Create",
                        onClick = {
                            onCreateGroup(newGroupName)
                            newGroupName = ""
                        },
                        isLoading = uiState.isLoading
                    )
                },
                dismissButton = {
                    TulsiTextButton(
                        text = "Cancel",
                        onClick = { onShowCreateDialog(false) }
                    )
                }
            )
        }

        // Dialog: Join with Invite Code
        if (uiState.showJoinDialog) {
            AlertDialog(
                onDismissRequest = { onShowJoinDialog(false) },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "Join Family",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter the 6-character code shared by your family admin.",
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TulsiTextField(
                            value = joinInviteCode,
                            onValueChange = { if (it.length <= 6) joinInviteCode = it.uppercase() },
                            placeholder = "e.g. A1B2C3",
                            singleLine = true
                        )

                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.errorMessage,
                                fontSize = 13.sp,
                                color = colors.statusMissed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    TulsiButton(
                        text = "Join",
                        onClick = {
                            onJoinGroup(joinInviteCode)
                        },
                        isLoading = uiState.isLoading
                    )
                },
                dismissButton = {
                    TulsiTextButton(
                        text = "Cancel",
                        onClick = { onShowJoinDialog(false) }
                    )
                }
            )
        }

        // Dialog: Confirmation for Leaving Family
        if (uiState.showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { onShowLeaveDialog(false) },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "Leave Family?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "You will no longer receive alerts or share medicine schedules with this circle.",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TulsiButton(
                        text = "Leave",
                        onClick = {
                            onShowLeaveDialog(false)
                            onLeaveGroup()
                        }
                    )
                },
                dismissButton = {
                    TulsiTextButton(
                        text = "Cancel",
                        onClick = { onShowLeaveDialog(false) }
                    )
                }
            )
        }

        // Dialog: Confirmation for Deleting Family (Admin only)
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { onShowDeleteDialog(false) },
                containerColor = colors.background,
                title = {
                    Text(
                        text = "Delete Family?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.statusMissed
                    )
                },
                text = {
                    Text(
                        text = "This will permanently delete the family group, all medicine schedules, and invite codes for all members. This action cannot be undone.",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TulsiButton(
                        text = "Delete family",
                        onClick = {
                            onShowDeleteDialog(false)
                            onDeleteGroup()
                        }
                    )
                },
                dismissButton = {
                    TulsiTextButton(
                        text = "Cancel",
                        onClick = { onShowDeleteDialog(false) }
                    )
                }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Previews: Light, Dark, 1.5x Font Scale
// -----------------------------------------------------------------------------

@Preview(name = "Family - Light", showBackground = true)
@Composable
fun FamilyGroupScreenPreviewLight() {
    TulsiTheme(darkTheme = false) {
        FamilyGroupScreenContent(
            uiState = getSampleFamilyUiState(),
            onCreateGroup = {},
            onJoinGroup = {},
            onLeaveGroup = {},
            onDeleteGroup = {},
            onShowCreateDialog = {},
            onShowJoinDialog = {},
            onShowLeaveDialog = {},
            onShowDeleteDialog = {},
            onCopyInviteCode = {},
            onShareInviteCode = { _, _ -> }
        )
    }
}

@Preview(name = "Family - Dark", showBackground = true)
@Composable
fun FamilyGroupScreenPreviewDark() {
    TulsiTheme(darkTheme = true) {
        FamilyGroupScreenContent(
            uiState = getSampleFamilyUiState(),
            onCreateGroup = {},
            onJoinGroup = {},
            onLeaveGroup = {},
            onDeleteGroup = {},
            onShowCreateDialog = {},
            onShowJoinDialog = {},
            onShowLeaveDialog = {},
            onShowDeleteDialog = {},
            onCopyInviteCode = {},
            onShareInviteCode = { _, _ -> }
        )
    }
}

@Preview(name = "Family - 1.5x Font Scale", fontScale = 1.5f, showBackground = true)
@Composable
fun FamilyGroupScreenPreviewLargeFont() {
    TulsiTheme(darkTheme = false) {
        FamilyGroupScreenContent(
            uiState = getSampleFamilyUiState(),
            onCreateGroup = {},
            onJoinGroup = {},
            onLeaveGroup = {},
            onDeleteGroup = {},
            onShowCreateDialog = {},
            onShowJoinDialog = {},
            onShowLeaveDialog = {},
            onShowDeleteDialog = {},
            onCopyInviteCode = {},
            onShareInviteCode = { _, _ -> }
        )
    }
}

private fun getSampleFamilyUiState(): FamilyGroupUiState {
    return FamilyGroupUiState(
        currentUser = User(id = "user_1", displayName = "Asha"),
        group = FamilyGroup(
            id = "grp_1",
            name = "Verma Family",
            inviteCode = "EV775Z"
        ),
        members = listOf(
            GroupMember(userId = "user_1", displayName = "Asha", role = MemberRole.ADMIN, isPatient = false),
            GroupMember(userId = "user_2", displayName = "Ravi", role = MemberRole.MEMBER, isPatient = true),
            GroupMember(userId = "user_3", displayName = "Meera", role = MemberRole.MEMBER, isPatient = false)
        ),
        isAdmin = true,
        isLoading = false
    )
}
