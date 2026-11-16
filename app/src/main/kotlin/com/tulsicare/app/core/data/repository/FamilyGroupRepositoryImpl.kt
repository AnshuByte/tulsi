package com.tulsicare.app.core.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.sync.FirestoreListenerManager
import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.FamilyGroupDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.User
import com.tulsicare.app.core.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val familyGroupDao: FamilyGroupDao,
    private val medicineDao: MedicineDao,
    private val scheduleDao: ScheduleDao,
    private val doseEventDao: DoseEventDao,
    private val firestore: FirebaseFirestore,
    private val firestoreListenerManager: FirestoreListenerManager,
    private val userPreferencesRepository: UserPreferencesRepository
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

        // 1. Prepare admin member record
        val adminMember = GroupMember(
            userId = creatorUser.id,
            displayName = creatorUser.displayName.ifBlank { "Admin" },
            role = MemberRole.ADMIN,
            isPatient = true,
            phoneNumber = creatorUser.phoneNumber,
            joinedAt = System.currentTimeMillis()
        )

        // 2. Atomic batch creation in Firestore (group, member, inviteCode, and user profile)
        val batch = firestore.batch()
        batch.set(firestore.collection("groups").document(groupId), group)
        batch.set(
            firestore.collection("groups").document(groupId).collection("members").document(creatorUser.id),
            adminMember
        )
        batch.set(
            firestore.collection("inviteCodes").document(inviteCode),
            mapOf(
                "code" to inviteCode,
                "groupId" to groupId,
                "groupName" to name,
                "createdBy" to creatorUser.id,
                "createdAt" to System.currentTimeMillis()
            )
        )
        batch.set(
            firestore.collection("users").document(creatorUser.id),
            mapOf("currentGroupId" to groupId),
            SetOptions.merge()
        )
        batch.commit().await()
        userPreferencesRepository.setCurrentGroupId(groupId)

        // 5. Cache in Room
        familyGroupDao.insertGroup(group.toEntity())
        familyGroupDao.insertMember(adminMember.toEntity(groupId))

        // 6. Migrate pre-group local medicines, schedules, and dose events
        medicineDao.reassignGroup(listOf("default_group", "my_family"), groupId)
        scheduleDao.reassignGroup(listOf("default_group", "my_family"), groupId)
        doseEventDao.reassignGroup(listOf("default_group", "my_family"), groupId)

        group
    }

    override suspend fun joinGroupByCode(inviteCode: String, user: User): Result<FamilyGroup> = runCatching {
        val cleanCode = inviteCode.trim().uppercase()
        require(cleanCode.isNotBlank()) { "Please enter an invite code" }
        require(cleanCode.length == 6) { "Invite code must be 6 characters" }
        require(cleanCode.all { it.isLetterOrDigit() }) { "Invite code must contain letters and numbers only" }

        // 1. Resolve invite code to target groupId
        var targetGroupId: String? = null
        var lookupException: Exception? = null
        try {
            val inviteDoc = firestore.collection("inviteCodes").document(cleanCode).get().await()
            if (inviteDoc.exists()) {
                val expiresAt = inviteDoc.getLong("expiresAt")
                if (expiresAt != null && expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
                    throw IllegalArgumentException("This invite code has expired. Please ask your family admin for a new one.")
                }
                targetGroupId = inviteDoc.getString("groupId")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            lookupException = e
            Log.w("FamilyGroupRepo", "inviteCodes lookup failed for $cleanCode: ${e.message}")
        }

        // Fallback: If not found in inviteCodes, query groups directly by inviteCode
        if (targetGroupId.isNullOrBlank()) {
            try {
                val groupQuery = firestore.collection("groups")
                    .whereEqualTo("inviteCode", cleanCode)
                    .limit(1)
                    .get()
                    .await()
                if (!groupQuery.isEmpty) {
                    targetGroupId = groupQuery.documents.first().id
                }
            } catch (e: Exception) {
                Log.w("FamilyGroupRepo", "groups query fallback failed for $cleanCode: ${e.message}")
            }
        }

        if (targetGroupId.isNullOrBlank()) {
            if (lookupException != null && lookupException is FirebaseFirestoreException && lookupException.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw IllegalStateException("Permission denied. Please ensure you are signed in and try again.")
            }
            throw IllegalArgumentException("Invite code '$cleanCode' not found. Please verify the code with your family admin.")
        }

        // 2. Add user as member with role MEMBER
        val member = GroupMember(
            userId = user.id,
            displayName = user.displayName.ifBlank { "Member" },
            role = MemberRole.MEMBER,
            isPatient = true,
            phoneNumber = user.phoneNumber,
            joinedAt = System.currentTimeMillis()
        )

        try {
            firestore.collection("groups")
                .document(targetGroupId)
                .collection("members")
                .document(user.id)
                .set(member)
                .await()
        } catch (e: Exception) {
            Log.e("FamilyGroupRepo", "Failed to join group $targetGroupId", e)
            throw IllegalStateException(
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    "Permission denied when joining family circle."
                else
                    "Unable to join family group: ${e.localizedMessage ?: "Unknown error"}"
            )
        }

        // 3. Now that membership is recorded, retrieve group details
        val groupDoc = firestore.collection("groups").document(targetGroupId).get().await()
        val group = groupDoc.toObject(FamilyGroup::class.java)?.copy(id = targetGroupId)
            ?: throw IllegalStateException("Family group details could not be loaded.")

        // 4. Update user's currentGroupId
        firestore.collection("users").document(user.id)
            .set(mapOf("currentGroupId" to group.id), SetOptions.merge())
            .await()
        userPreferencesRepository.setCurrentGroupId(group.id)

        // 5. Cache in Room DB
        familyGroupDao.insertGroup(group.toEntity())
        familyGroupDao.insertMember(member.toEntity(group.id))

        // 6. Migrate pre-group local medicines, schedules, and dose events
        medicineDao.reassignGroup(listOf("default_group", "my_family"), group.id)
        scheduleDao.reassignGroup(listOf("default_group", "my_family"), group.id)
        doseEventDao.reassignGroup(listOf("default_group", "my_family"), group.id)

        group
    }

    override suspend fun deleteGroup(groupId: String): Result<Unit> = runCatching {
        val currentUid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

        // 1. Fetch group doc for inviteCode and creator verification
        val groupDoc = firestore.collection("groups").document(groupId).get().await()
        val isCreator = groupDoc.getString("createdBy") == currentUid
        val inviteCode = groupDoc.getString("inviteCode")

        // 2. Verify admin role or creator
        val memberDoc = runCatching {
            firestore.collection("groups")
                .document(groupId)
                .collection("members")
                .document(currentUid)
                .get()
                .await()
        }.getOrNull()
        val role = memberDoc?.getString("role")?.uppercase()
        require(role == "ADMIN" || isCreator) { "Only a family admin can delete the family" }

        // 3. Fetch all members and subcollections
        val memberDocs = firestore.collection("groups").document(groupId).collection("members").get().await()
        val medicineDocs = firestore.collection("groups").document(groupId).collection("medicines").get().await()
        val scheduleDocs = firestore.collection("groups").document(groupId).collection("schedules").get().await()
        val doseEventDocs = firestore.collection("groups").document(groupId).collection("doseEvents").get().await()

        // 4. Atomic batch deletion in Firestore
        val batch = firestore.batch()
        batch.delete(firestore.collection("groups").document(groupId))

        if (!inviteCode.isNullOrBlank()) {
            batch.delete(firestore.collection("inviteCodes").document(inviteCode))
        }

        for (doc in memberDocs.documents) {
            batch.delete(doc.reference)
            // Reset currentGroupId for all members in Firestore
            batch.set(
                firestore.collection("users").document(doc.id),
                mapOf("currentGroupId" to null),
                SetOptions.merge()
            )
        }
        for (doc in medicineDocs.documents) {
            batch.delete(doc.reference)
        }
        for (doc in scheduleDocs.documents) {
            batch.delete(doc.reference)
        }
        for (doc in doseEventDocs.documents) {
            batch.delete(doc.reference)
        }

        // Reset current user's currentGroupId in user doc
        batch.set(
            firestore.collection("users").document(currentUid),
            mapOf("currentGroupId" to null),
            SetOptions.merge()
        )

        batch.commit().await()

        // 5. Cleanup listeners, alarms, and local cache
        firestoreListenerManager.removeListenersForGroup(groupId)
        runCatching { ExactAlarmScheduler.cancelAlarmsForGroup(context, groupId) }

        familyGroupDao.deleteGroup(groupId)
        familyGroupDao.clearMembers(groupId)
        medicineDao.clearMedicinesForGroup(groupId)
        scheduleDao.clearSchedulesForGroup(groupId)
        doseEventDao.clearEventsForGroup(groupId)
        userPreferencesRepository.clearGroupId()
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
            // Sole member leaving: completely delete group atomically
            deleteGroup(groupId).getOrThrow()
            return@runCatching
        }

        val batch = firestore.batch()

        // 1. If only admin leaving, promote next member in the same batch
        if (isLeavingAdmin) {
            val hasOtherAdmin = remainingDocs.any { it.getString("role")?.uppercase() == "ADMIN" }
            if (!hasOtherAdmin) {
                val nextAdminId = remainingDocs.first().id
                batch.update(
                    firestore.collection("groups")
                        .document(groupId)
                        .collection("members")
                        .document(nextAdminId),
                    "role", "ADMIN"
                )
            }
        }

        // 2. Remove member document in the batch
        batch.delete(
            firestore.collection("groups")
                .document(groupId)
                .collection("members")
                .document(userId)
        )

        // 3. Clear currentGroupId on user document in the same batch
        batch.set(
            firestore.collection("users").document(userId),
            mapOf("currentGroupId" to null),
            SetOptions.merge()
        )

        batch.commit().await()

        // 4. Local cleanup:
        // Detach every listener for the old groupId
        firestoreListenerManager.removeListenersForGroup(groupId)

        // Cancel local alarms for that group's medicines
        runCatching { ExactAlarmScheduler.cancelAlarmsForGroup(context, groupId) }

        // Immediately purge local caches for this group to prevent data persistence
        familyGroupDao.deleteMember(userId, groupId)
        familyGroupDao.deleteGroup(groupId)
        familyGroupDao.clearMembers(groupId)
        medicineDao.clearMedicinesForGroup(groupId)
        scheduleDao.clearSchedulesForGroup(groupId)
        doseEventDao.clearEventsForGroup(groupId)

        // Clear groupId from DataStore
        userPreferencesRepository.clearGroupId()
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
        val currentUid = auth.currentUser?.uid.orEmpty()
        val registration = firestore.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val isNotFoundOrDenied = error.code == FirebaseFirestoreException.Code.NOT_FOUND ||
                            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    if (isNotFoundOrDenied) {
                        firestoreListenerManager.removeListenersForGroup(groupId)
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            familyGroupDao.deleteGroup(groupId)
                            familyGroupDao.clearMembers(groupId)
                            medicineDao.clearMedicinesForGroup(groupId)
                            scheduleDao.clearSchedulesForGroup(groupId)
                            doseEventDao.clearEventsForGroup(groupId)
                            ExactAlarmScheduler.cancelAlarmsForGroup(context, groupId)
                            if (currentUid.isNotBlank()) {
                                firestore.collection("users").document(currentUid)
                                    .set(mapOf("currentGroupId" to null), SetOptions.merge())
                            }
                        }
                        trySend(null)
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    firestoreListenerManager.removeListenersForGroup(groupId)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        familyGroupDao.deleteGroup(groupId)
                        familyGroupDao.clearMembers(groupId)
                        medicineDao.clearMedicinesForGroup(groupId)
                        scheduleDao.clearSchedulesForGroup(groupId)
                        doseEventDao.clearEventsForGroup(groupId)
                        ExactAlarmScheduler.cancelAlarmsForGroup(context, groupId)
                        if (currentUid.isNotBlank()) {
                            firestore.collection("users").document(currentUid)
                                .set(mapOf("currentGroupId" to null), SetOptions.merge())
                        }
                    }
                    trySend(null)
                    return@addSnapshotListener
                }

                val group = snapshot.toObject(FamilyGroup::class.java)?.copy(id = snapshot.id)
                if (group != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        familyGroupDao.insertGroup(group.toEntity())
                    }
                }
                trySend(group)
            }

        if (currentUid.isNotBlank()) {
            firestoreListenerManager.register(currentUid, groupId, "group", registration)
        }

        awaitClose {
            if (currentUid.isNotBlank()) {
                firestoreListenerManager.removeListener(currentUid, groupId, "group")
            }
            registration.remove()
        }
    }

    override fun syncMembersRealtime(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val currentUid = auth.currentUser?.uid.orEmpty()
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val isNotFoundOrDenied = error.code == FirebaseFirestoreException.Code.NOT_FOUND ||
                            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    if (isNotFoundOrDenied) {
                        trySend(emptyList())
                    }
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

        if (currentUid.isNotBlank()) {
            firestoreListenerManager.register(currentUid, groupId, "members", registration)
        }

        awaitClose {
            if (currentUid.isNotBlank()) {
                firestoreListenerManager.removeListener(currentUid, groupId, "members")
            }
            registration.remove()
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excludes ambiguous O, 0, 1, I
        return (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
