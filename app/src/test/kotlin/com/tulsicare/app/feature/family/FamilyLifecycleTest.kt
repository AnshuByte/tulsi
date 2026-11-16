package com.tulsicare.app.feature.family

import com.tulsicare.app.core.model.FamilyGroup
import com.tulsicare.app.core.model.GroupMember
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.Medicine
import com.tulsicare.app.core.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FamilyLifecycleTest {

    // Simulated app environment with Firestore, Room, Alarms, Listeners, and DataStore
    class SimulatedAppEnv {
        // Cloud Firestore
        val firestoreGroups = mutableMapOf<String, FamilyGroup>()
        val firestoreMembers = mutableMapOf<String, MutableMap<String, GroupMember>>() // groupId -> (userId -> Member)
        val firestoreInviteCodes = mutableMapOf<String, String>() // code -> groupId
        val firestoreUsers = mutableMapOf<String, User>()
        val firestoreMedicines = mutableMapOf<String, MutableMap<String, Medicine>>() // groupId -> (medId -> Medicine)

        // Local Room Caches
        val roomGroups = mutableMapOf<String, FamilyGroup>()
        val roomMembers = mutableMapOf<String, MutableMap<String, GroupMember>>()
        val roomMedicines = mutableMapOf<String, MutableMap<String, Medicine>>()

        // Local Alarms & Listeners & DataStore
        val activeAlarms = mutableMapOf<String, MutableSet<String>>() // groupId -> Set<medicineId>
        val activeListeners = mutableMapOf<String, MutableSet<String>>() // key (uid:groupId) -> Set<tag>
        var dataStoreGroupId: String? = null
        var currentUser: User? = null
        var currentNavDestination: String = "auth"

        fun signIn(user: User) {
            val existing = firestoreUsers[user.id]
            val effectiveGroupId = existing?.currentGroupId ?: user.currentGroupId
            val userToSave = (existing ?: user).copy(currentGroupId = effectiveGroupId)
            firestoreUsers[user.id] = userToSave
            currentUser = userToSave
            dataStoreGroupId = effectiveGroupId
            currentNavDestination = if (effectiveGroupId != null) "home" else "family"
        }

        fun createFamily(name: String): FamilyGroup {
            val user = currentUser ?: throw IllegalStateException("Not logged in")
            val newGroupId = "group_" + UUID.randomUUID().toString().take(8)
            val inviteCode = "CD" + UUID.randomUUID().toString().take(4).uppercase()

            val group = FamilyGroup(
                id = newGroupId,
                name = name,
                inviteCode = inviteCode,
                createdBy = user.id,
                createdAt = System.currentTimeMillis()
            )
            val adminMember = GroupMember(
                userId = user.id,
                displayName = user.displayName,
                role = MemberRole.ADMIN,
                isPatient = true
            )

            // Firestore write
            firestoreGroups[newGroupId] = group
            firestoreMembers.getOrPut(newGroupId) { mutableMapOf() }[user.id] = adminMember
            firestoreInviteCodes[inviteCode] = newGroupId
            val updatedUser = user.copy(currentGroupId = newGroupId)
            firestoreUsers[user.id] = updatedUser
            currentUser = updatedUser

            // Local cache
            roomGroups[newGroupId] = group
            roomMembers.getOrPut(newGroupId) { mutableMapOf() }[user.id] = adminMember
            dataStoreGroupId = newGroupId

            // Register listeners
            val listenerKey = "${user.id}:$newGroupId"
            activeListeners.getOrPut(listenerKey) { mutableSetOf() }.addAll(listOf("group", "members", "medicines"))

            currentNavDestination = "home"
            return group
        }

        fun addMedicine(name: String, dosage: String): Medicine {
            val user = currentUser ?: throw IllegalStateException("Not logged in")
            val groupId = user.currentGroupId ?: throw IllegalStateException("No active family group")
            val medId = "med_" + UUID.randomUUID().toString().take(8)
            val med = Medicine(
                id = medId,
                groupId = groupId,
                name = name,
                dosage = dosage
            )
            // Firestore write
            firestoreMedicines.getOrPut(groupId) { mutableMapOf() }[medId] = med
            // Room write
            roomMedicines.getOrPut(groupId) { mutableMapOf() }[medId] = med
            // Schedule local alarm
            activeAlarms.getOrPut(groupId) { mutableSetOf() }.add(medId)
            return med
        }

        fun leaveFamily(groupId: String, isOffline: Boolean = false) {
            val user = currentUser ?: throw IllegalStateException("Not logged in")
            val members = firestoreMembers[groupId] ?: mutableMapOf()
            val remainingMembers = members.filterKeys { it != user.id }

            if (!isOffline) {
                if (remainingMembers.isEmpty()) {
                    // Sole member leaving -> delete group and invite code atomically
                    deleteFamily(groupId)
                    return
                } else {
                    val leavingMember = members[user.id]
                    val isLeavingAdmin = leavingMember?.role == MemberRole.ADMIN
                    val hasOtherAdmin = remainingMembers.values.any { it.role == MemberRole.ADMIN }

                    if (isLeavingAdmin && !hasOtherAdmin) {
                        // Promote next member in the batch
                        val nextMember = remainingMembers.values.first()
                        members[nextMember.userId] = nextMember.copy(role = MemberRole.ADMIN)
                    }

                    // Remove leaving member and reset user document currentGroupId
                    members.remove(user.id)
                    firestoreUsers[user.id] = user.copy(currentGroupId = null)
                }
            }

            // Local cleanup
            val listenerKey = "${user.id}:$groupId"
            activeListeners.remove(listenerKey)
            activeAlarms.remove(groupId)

            roomGroups.remove(groupId)
            roomMembers.remove(groupId)
            roomMedicines.remove(groupId)
            dataStoreGroupId = null
            currentUser = currentUser?.copy(currentGroupId = null)
            currentNavDestination = "family" // Create / Join family screen
        }

        fun deleteFamily(groupId: String) {
            val user = currentUser ?: throw IllegalStateException("Not logged in")

            // Atomic batch deletion in Firestore
            val group = firestoreGroups.remove(groupId)
            if (group?.inviteCode != null) {
                firestoreInviteCodes.remove(group.inviteCode)
            }
            val members = firestoreMembers.remove(groupId) ?: emptyMap()
            firestoreMedicines.remove(groupId)

            for ((memberId, _) in members) {
                firestoreUsers[memberId]?.let {
                    firestoreUsers[memberId] = it.copy(currentGroupId = null)
                }
            }

            // Local cleanup
            val listenerKey = "${user.id}:$groupId"
            activeListeners.remove(listenerKey)
            activeAlarms.remove(groupId)

            roomGroups.remove(groupId)
            roomMembers.remove(groupId)
            roomMedicines.remove(groupId)
            dataStoreGroupId = null
            currentUser = currentUser?.copy(currentGroupId = null)
            currentNavDestination = "family" // Create / Join family screen
        }

        fun signOut() {
            // Guaranteed auth sign out FIRST
            currentUser = null
            currentNavDestination = "auth"

            // Local cleanups wrapped in runCatching
            runCatching { activeListeners.clear() }
            runCatching { activeAlarms.clear() }
            runCatching { roomGroups.clear() }
            runCatching { roomMembers.clear() }
            runCatching { roomMedicines.clear() }
            runCatching { dataStoreGroupId = null }
        }

        fun simulateSnapshotListenerError(groupId: String, errorCode: String) {
            if (errorCode == "PERMISSION_DENIED" || errorCode == "NOT_FOUND") {
                // Clearing local state and route to Create / Join Family
                val user = currentUser
                if (user != null) {
                    val listenerKey = "${user.id}:$groupId"
                    activeListeners.remove(listenerKey)
                }
                activeAlarms.remove(groupId)
                roomGroups.remove(groupId)
                roomMembers.remove(groupId)
                roomMedicines.remove(groupId)
                dataStoreGroupId = null
                currentUser = currentUser?.copy(currentGroupId = null)
                currentNavDestination = "family"
            }
        }
    }

    @Test
    fun testLifecycleSequenceRun5TimesInARow() {
        val env = SimulatedAppEnv()

        for (iteration in 1..5) {
            val user = User(id = "user_loop", displayName = "LoopUser", email = "loop@example.com")
            env.signIn(user)

            // 1. Create family
            val group = env.createFamily("Family Iteration $iteration")
            assertNotNull(group.id)
            assertEquals("home", env.currentNavDestination)
            assertEquals(group.id, env.currentUser?.currentGroupId)
            assertEquals(group.id, env.dataStoreGroupId)

            // 2. Add medicine to this family
            val med = env.addMedicine("Med $iteration", "10mg")
            assertEquals(1, env.roomMedicines[group.id]?.size)
            assertTrue(env.activeAlarms[group.id]?.contains(med.id) == true)

            // 3. Leave family
            env.leaveFamily(group.id)
            assertNull("Current user's groupId must be cleared after leaving", env.currentUser?.currentGroupId)
            assertNull("DataStore groupId must be cleared after leaving", env.dataStoreGroupId)
            assertEquals("family", env.currentNavDestination)
            assertNull("Room medicines for old group must be cleared", env.roomMedicines[group.id])
            assertNull("Alarms for old group must be cleared", env.activeAlarms[group.id])
            assertFalse("Listeners for old group must be removed", env.activeListeners.containsKey("user_loop:${group.id}"))

            // 4. Create a NEW family
            val newGroup = env.createFamily("Brand New Family $iteration")
            assertNotEquals(group.id, newGroup.id)
            assertEquals(newGroup.id, env.currentUser?.currentGroupId)
            assertEquals(newGroup.id, env.dataStoreGroupId)
            assertEquals("home", env.currentNavDestination)

            // 5. Add a medicine to the new family
            val newMed = env.addMedicine("New Med $iteration", "500mg")
            assertEquals(1, env.roomMedicines[newGroup.id]?.size)
            assertNull("Old group's medicines must not exist", env.roomMedicines[group.id])

            // 6. Sign out
            env.signOut()
            assertNull("User must be null after sign out", env.currentUser)
            assertEquals("Navigation destination must be 'auth'", "auth", env.currentNavDestination)
            assertTrue("Alarms must be cleared on sign out", env.activeAlarms.isEmpty())
            assertTrue("Room caches must be cleared on sign out", env.roomMedicines.isEmpty())
            assertTrue("Listeners must be cleared on sign out", env.activeListeners.isEmpty())

            // 7. Sign in again
            env.signIn(user)
            assertEquals(newGroup.id, env.currentUser?.currentGroupId)
            assertEquals(newGroup.id, env.dataStoreGroupId)
            assertEquals("home", env.currentNavDestination)
        }
    }

    @Test
    fun testLeaveAndSignOutImmediatelyReachesLogin() {
        val env = SimulatedAppEnv()
        val user = User(id = "user_1", displayName = "Test", email = "test@example.com")
        env.signIn(user)
        val group = env.createFamily("Quick Family")

        // Leave then sign out immediately
        env.leaveFamily(group.id)
        env.signOut()

        assertNull(env.currentUser)
        assertEquals("auth", env.currentNavDestination)
    }

    @Test
    fun testLeaveWhileOfflineCleansUpLocallyAndReachesCreateJoinFamily() {
        val env = SimulatedAppEnv()
        val user = User(id = "user_offline", displayName = "OfflineUser")
        env.signIn(user)
        val group = env.createFamily("Offline Family")
        env.addMedicine("Aspirin", "100mg")

        // Leave while offline
        env.leaveFamily(group.id, isOffline = true)

        assertNull(env.currentUser?.currentGroupId)
        assertNull(env.dataStoreGroupId)
        assertEquals("family", env.currentNavDestination)
        assertNull(env.roomMedicines[group.id])
        assertNull(env.activeAlarms[group.id])
    }

    @Test
    fun testSoleMemberLeavingDeletesGroupAndInviteCode() {
        val env = SimulatedAppEnv()
        val user = User(id = "sole_admin", displayName = "Sole Admin")
        env.signIn(user)
        val group = env.createFamily("Sole Family")
        val inviteCode = group.inviteCode

        assertTrue(env.firestoreGroups.containsKey(group.id))
        assertTrue(env.firestoreInviteCodes.containsKey(inviteCode))

        // Sole member leaves
        env.leaveFamily(group.id)

        assertFalse("Group doc must be deleted in Firestore when sole member leaves", env.firestoreGroups.containsKey(group.id))
        assertFalse("Invite code doc must be deleted when group deleted", env.firestoreInviteCodes.containsKey(inviteCode))
        assertNull(env.currentUser?.currentGroupId)
        assertEquals("family", env.currentNavDestination)
    }

    @Test
    fun testOnlyAdminLeavesPromotesNextMember() {
        val env = SimulatedAppEnv()
        val admin = User(id = "admin_1", displayName = "Admin 1")
        env.signIn(admin)
        val group = env.createFamily("Multi Member Family")

        // Add a second member
        val member2 = GroupMember(userId = "member_2", displayName = "Member 2", role = MemberRole.MEMBER)
        env.firestoreMembers[group.id]!!["member_2"] = member2

        // Admin leaves
        env.leaveFamily(group.id)

        // Member 2 is now promoted to ADMIN
        val promoted = env.firestoreMembers[group.id]!!["member_2"]
        assertEquals(MemberRole.ADMIN, promoted?.role)
        assertFalse("Leaving admin must be removed from members", env.firestoreMembers[group.id]!!.containsKey("admin_1"))
    }

    @Test
    fun testListenerErrorHandledByClearingLocalGroupStateInsteadOfFreezing() {
        val env = SimulatedAppEnv()
        val user = User(id = "member_other", displayName = "Other Member")
        env.signIn(user)
        val group = env.createFamily("Group To Disappear")
        env.addMedicine("Thyroxine", "25mcg")

        assertEquals("home", env.currentNavDestination)
        assertEquals(group.id, env.currentUser?.currentGroupId)

        // Simulate admin deleting group from another device, triggering PERMISSION_DENIED on this device's listener
        env.simulateSnapshotListenerError(group.id, "PERMISSION_DENIED")

        // Must clear local group state and route to "family" (Create / Join Family screen)
        assertNull(env.currentUser?.currentGroupId)
        assertNull(env.dataStoreGroupId)
        assertEquals("family", env.currentNavDestination)
        assertNull(env.roomMedicines[group.id])
        assertNull(env.activeAlarms[group.id])
    }

    @Test
    fun testDeleteFamilyCleansUpEverythingAtomically() {
        val env = SimulatedAppEnv()
        val admin = User(id = "admin_del", displayName = "Admin Del")
        env.signIn(admin)
        val group = env.createFamily("Delete Group")
        env.addMedicine("Paracetamol", "500mg")

        env.deleteFamily(group.id)

        assertFalse(env.firestoreGroups.containsKey(group.id))
        assertFalse(env.firestoreInviteCodes.containsKey(group.inviteCode))
        assertFalse(env.firestoreMedicines.containsKey(group.id))
        assertNull(env.currentUser?.currentGroupId)
        assertEquals("family", env.currentNavDestination)
    }
}
