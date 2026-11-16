package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.User
import kotlinx.coroutines.flow.Flow

interface FamilyGroupRepository {
    fun observeGroup(groupId: String): Flow<FamilyGroup?>
    fun observeMembers(groupId: String): Flow<List<GroupMember>>
    suspend fun getGroup(groupId: String): FamilyGroup?
    suspend fun createGroup(name: String, creatorUser: User): Result<FamilyGroup>
    suspend fun joinGroupByCode(inviteCode: String, user: User): Result<FamilyGroup>
    suspend fun leaveGroup(groupId: String, userId: String): Result<Unit>
    suspend fun removeMember(groupId: String, targetUserId: String): Result<Unit>
    fun syncGroupRealtime(groupId: String): Flow<FamilyGroup?>
    fun syncMembersRealtime(groupId: String): Flow<List<GroupMember>>
}
