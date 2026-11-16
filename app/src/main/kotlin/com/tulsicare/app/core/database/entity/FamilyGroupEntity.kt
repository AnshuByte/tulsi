package com.tulsicare.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole

@Entity(tableName = "family_groups")
data class FamilyGroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdBy: String,
    val createdAt: Long
) {
    fun toDomainModel(): FamilyGroup = FamilyGroup(
        id = id,
        name = name,
        inviteCode = inviteCode,
        createdBy = createdBy,
        createdAt = createdAt
    )
}

fun FamilyGroup.toEntity(): FamilyGroupEntity = FamilyGroupEntity(
    id = id,
    name = name,
    inviteCode = inviteCode,
    createdBy = createdBy,
    createdAt = createdAt
)

@Entity(tableName = "group_members", primaryKeys = ["userId", "groupId"])
data class GroupMemberEntity(
    val userId: String,
    val groupId: String,
    val displayName: String,
    val role: MemberRole,
    val isPatient: Boolean,
    val phoneNumber: String,
    val joinedAt: Long
) {
    fun toDomainModel(): GroupMember = GroupMember(
        userId = userId,
        displayName = displayName,
        role = role,
        isPatient = isPatient,
        phoneNumber = phoneNumber,
        joinedAt = joinedAt
    )
}

fun GroupMember.toEntity(groupId: String): GroupMemberEntity = GroupMemberEntity(
    userId = userId,
    groupId = groupId,
    displayName = displayName,
    role = role,
    isPatient = isPatient,
    phoneNumber = phoneNumber,
    joinedAt = joinedAt
)
