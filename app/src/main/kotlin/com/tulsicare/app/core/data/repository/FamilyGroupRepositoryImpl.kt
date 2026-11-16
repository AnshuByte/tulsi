package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.FamilyGroupDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FamilyGroupRepositoryImpl @Inject constructor(
    private val familyGroupDao: FamilyGroupDao,
    private val medicineDao: MedicineDao,
    private val scheduleDao: ScheduleDao,
    private val doseEventDao: DoseEventDao,
    private val firestore: FirebaseFirestore
) : FamilyGroupRepository {

    override fun observeGroup(groupId: String): Flow<FamilyGroup?> {
        return familyGroupDao.observeGroup(groupId).map { it?.toDomainModel() }
    }

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> {
        return familyGroupDao.observeMembers(groupId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getGroup(groupId: String): FamilyGroup? {
        return familyGroupDao.getGroupById(groupId)?.toDomainModel()
    }

    override suspend fun createGroup(name: String, creatorUser: User): Result<FamilyGroup> = runCatching {
        val groupId = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        val group = FamilyGroup(
            id = groupId,
            name = name,
            inviteCode = inviteCode,
            createdBy = creatorUser.id,
            createdAt = System.currentTimeMillis()
        )

        // 1. Create group in Firestore
        firestore.collection("groups").document(groupId).set(group).await()

        // 2. Add creator as ADMIN member
        val adminMember = GroupMember(
            userId = creatorUser.id,
            displayName = creatorUser.displayName.ifBlank { "Admin" },
            role = MemberRole.ADMIN,
            isPatient = true,
            phoneNumber = creatorUser.phoneNumber,
            joinedAt = System.currentTimeMillis()
        )
        firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(creatorUser.id)
            .set(adminMember)
            .await()

        // 3. Update creator's currentGroupId in users collection
        firestore.collection("users").document(creatorUser.id)
            .set(mapOf("currentGroupId" to groupId), SetOptions.merge())
            .await()

        // 4. Cache in Room
        familyGroupDao.insertGroup(group.toEntity())
        familyGroupDao.insertMember(adminMember.toEntity(groupId))

        group
    }

    override suspend fun joinGroupByCode(inviteCode: String, user: User): Result<FamilyGroup> = runCatching {
        val cleanCode = inviteCode.trim().uppercase()
        require(cleanCode.isNotBlank()) { "Invite code cannot be empty" }
        require(cleanCode.length == 6) { "Invite code must be exactly 6 characters" }
        require(cleanCode.all { it.isLetterOrDigit() }) { "Invite code contains invalid characters" }

        val querySnapshot = firestore.collection("groups")
            .whereEqualTo("inviteCode", cleanCode)
            .limit(1)
            .get()
            .await()

        if (querySnapshot.isEmpty) {
            throw IllegalArgumentException("Invalid invite code. Group not found.")
        }

        val doc = querySnapshot.documents.first()
        val group = doc.toObject(FamilyGroup::class.java)?.copy(id = doc.id)
            ?: throw IllegalStateException("Failed to parse family group")

        // Add user as MEMBER
        val member = GroupMember(
            userId = user.id,
            displayName = user.displayName.ifBlank { "Member" },
            role = MemberRole.MEMBER,
            isPatient = true,
            phoneNumber = user.phoneNumber,
            joinedAt = System.currentTimeMillis()
        )
        firestore.collection("groups")
            .document(group.id)
            .collection("members")
            .document(user.id)
            .set(member)
            .await()

        // Update user's currentGroupId
        firestore.collection("users").document(user.id)
            .set(mapOf("currentGroupId" to group.id), SetOptions.merge())
            .await()

        // Cache in Room
        familyGroupDao.insertGroup(group.toEntity())
        familyGroupDao.insertMember(member.toEntity(group.id))

        group
    }

    override suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> = runCatching {
        // Prevent orphaned groups: inspect current members
        val memberDocs = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .get()
            .await()

        val leavingDoc = memberDocs.documents.find { it.id == userId }
        val isLeavingAdmin = leavingDoc?.getString("role")?.uppercase() == "ADMIN"
        val remainingDocs = memberDocs.documents.filter { it.id != userId }

        if (remainingDocs.isEmpty()) {
            // Last member leaving: cleanup group
            firestore.collection("groups").document(groupId).delete().await()
        } else if (isLeavingAdmin) {
            val hasOtherAdmin = remainingDocs.any { it.getString("role")?.uppercase() == "ADMIN" }
            if (!hasOtherAdmin) {
                // Promote next member to ADMIN to ensure group is never orphaned
                val nextAdminId = remainingDocs.first().id
                firestore.collection("groups")
                    .document(groupId)
                    .collection("members")
                    .document(nextAdminId)
                    .update("role", "ADMIN")
                    .await()
            }
        }

        firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(userId)
            .delete()
            .await()

        firestore.collection("users").document(userId)
            .set(mapOf("currentGroupId" to null), SetOptions.merge())
            .await()

        // Immediately purge local caches for this group to prevent data persistence
        familyGroupDao.deleteMember(userId, groupId)
        familyGroupDao.deleteGroup(groupId)
        familyGroupDao.clearMembers(groupId)
        medicineDao.clearMedicinesForGroup(groupId)
        scheduleDao.clearSchedulesForGroup(groupId)
        doseEventDao.clearEventsForGroup(groupId)
    }

    override suspend fun removeMember(groupId: String, targetUserId: String): Result<Unit> = runCatching {
        firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(targetUserId)
            .delete()
            .await()

        firestore.collection("users").document(targetUserId)
            .set(mapOf("currentGroupId" to null), SetOptions.merge())
            .await()

        familyGroupDao.deleteMember(targetUserId, groupId)
    }

    override fun syncGroupRealtime(groupId: String): Flow<FamilyGroup?> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val group = snapshot?.toObject(FamilyGroup::class.java)?.copy(id = snapshot.id)
                if (group != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        familyGroupDao.insertGroup(group.toEntity())
                    }
                }
                trySend(group)
            }

        awaitClose { registration.remove() }
    }

    override fun syncMembersRealtime(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val members = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(GroupMember::class.java)?.copy(userId = doc.id)
                } ?: emptyList()

                if (members.isNotEmpty()) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        familyGroupDao.insertMembers(members.map { it.toEntity(groupId) })
                    }
                }

                trySend(members)
            }

        awaitClose { registration.remove() }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excludes ambiguous O, 0, 1, I
        return (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
