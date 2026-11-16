package com.tulsicare.app.feature.family

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.FamilyGroupRepository
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyGroupUiState(
    val currentUser: User? = null,
    val group: FamilyGroup? = null,
    val members: List<GroupMember> = emptyList(),
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val showJoinDialog: Boolean = false,
    val showLeaveDialog: Boolean = false
)

@HiltViewModel
class FamilyGroupViewModel @Inject constructor(
    private val familyGroupRepository: FamilyGroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyGroupUiState())
    val uiState: StateFlow<FamilyGroupUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = authRepository.getCurrentUser() ?: authRepository.signInAnonymously().getOrNull()
            _uiState.update { it.copy(currentUser = user) }

            val groupId = user?.currentGroupId
            if (groupId.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, group = null, members = emptyList()) }
                return@launch
            }

            // Observe local Room cache
            launch {
                familyGroupRepository.observeGroup(groupId).collectLatest { group ->
                    _uiState.update { state ->
                        state.copy(
                            group = group,
                            isAdmin = state.members.any { it.userId == user.id && it.role == MemberRole.ADMIN }
                        )
                    }
                }
            }

            launch {
                familyGroupRepository.observeMembers(groupId).collectLatest { members ->
                    _uiState.update { state ->
                        state.copy(
                            members = members,
                            isAdmin = members.any { it.userId == user.id && it.role == MemberRole.ADMIN },
                            isLoading = false
                        )
                    }
                }
            }

            // Realtime Firestore listeners
            launch {
                familyGroupRepository.syncGroupRealtime(groupId).collectLatest { }
            }
            launch {
                familyGroupRepository.syncMembersRealtime(groupId).collectLatest { }
            }
        }
    }

    fun setShowCreateDialog(show: Boolean) = _uiState.update { it.copy(showCreateDialog = show, errorMessage = null) }
    fun setShowJoinDialog(show: Boolean) = _uiState.update { it.copy(showJoinDialog = show, errorMessage = null) }
    fun setShowLeaveDialog(show: Boolean) = _uiState.update { it.copy(showLeaveDialog = show) }
    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, successMessage = null) }

    fun createGroup(name: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a group name") }
            return
        }

        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            familyGroupRepository.createGroup(name.trim(), user)
                .onSuccess { group ->
                    _uiState.update {
                        it.copy(
                            group = group,
                            showCreateDialog = false,
                            isLoading = false,
                            successMessage = "Family group '${group.name}' created!"
                        )
                    }
                    loadData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to create group"
                        )
                    }
                }
        }
    }

    fun joinGroup(inviteCode: String) {
        if (inviteCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter an invite code") }
            return
        }

        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            familyGroupRepository.joinGroupByCode(inviteCode.trim().uppercase(), user)
                .onSuccess { group ->
                    _uiState.update {
                        it.copy(
                            group = group,
                            showJoinDialog = false,
                            isLoading = false,
                            successMessage = "Joined '${group.name}'!"
                        )
                    }
                    loadData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Invalid invite code"
                        )
                    }
                }
        }
    }

    fun leaveGroup() {
        val user = _uiState.value.currentUser ?: return
        val groupId = _uiState.value.group?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            familyGroupRepository.leaveGroup(groupId, user.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            group = null,
                            members = emptyList(),
                            showLeaveDialog = false,
                            isLoading = false,
                            successMessage = "You left the family group"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to leave group"
                        )
                    }
                }
        }
    }

    fun shareInvite(context: Context) {
        val group = _uiState.value.group ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Join our family on tulsi to take care of medicines together!\n\n" +
                        "Family: ${group.name}\n" +
                        "Invite Code: ${group.inviteCode}\n\n" +
                        "Open tulsi, go to Family, and enter this code."
            )
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Invite Code")
        context.startActivity(shareIntent)
    }

    fun callMember(context: Context, phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        runCatching { context.startActivity(dialIntent) }
    }
}
