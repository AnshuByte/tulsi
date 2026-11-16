package com.tulsicare.app.core.model

data class FamilyGroup(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class MemberRole {
    ADMIN,
    MEMBER
}

data class GroupMember(
    val userId: String = "",
    val displayName: String = "",
    val role: MemberRole = MemberRole.MEMBER,
    val isPatient: Boolean = true,
    val phoneNumber: String = "",
    val joinedAt: Long = System.currentTimeMillis()
)
