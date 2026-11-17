package com.tulsicare.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGroupLogicTest {

    private fun validateInviteCode(code: String): Result<String> {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Invite code cannot be empty"))
        if (clean.length != 6) return Result.failure(IllegalArgumentException("Invite code must be exactly 6 characters"))
        if (!clean.all { it.isLetterOrDigit() }) return Result.failure(IllegalArgumentException("Invite code contains invalid characters"))
        return Result.success(clean)
    }

    private fun resolveGroupAdminOnLeave(
        leavingUserId: String,
        members: List<GroupMember>
    ): Pair<List<GroupMember>, String?> {
        val leavingMember = members.find { it.userId == leavingUserId }
        val remaining = members.filter { it.userId != leavingUserId }

        if (remaining.isEmpty()) {
            return Pair(emptyList(), "DELETE_GROUP")
        }

        val wasAdmin = leavingMember?.role == MemberRole.ADMIN
        val hasRemainingAdmin = remaining.any { it.role == MemberRole.ADMIN }

        if (wasAdmin && !hasRemainingAdmin) {
            // Promote oldest/first remaining member to ADMIN
            val promoted = remaining.mapIndexed { index, m ->
                if (index == 0) m.copy(role = MemberRole.ADMIN) else m
            }
            return Pair(promoted, "PROMOTED_ADMIN")
        }

        return Pair(remaining, "MEMBER_LEFT")
    }

    @Test
    fun testValidInviteCode() {
        val res = validateInviteCode("TULSI1")
        assertTrue(res.isSuccess)
        assertEquals("TULSI1", res.getOrNull())
    }

    @Test
    fun testInviteCodeCaseInsensitive() {
        val res = validateInviteCode("tulsi1")
        assertTrue(res.isSuccess)
        assertEquals("TULSI1", res.getOrNull())
    }

    @Test
    fun testMalformedInviteCodes() {
        assertTrue(validateInviteCode("").isFailure)
        assertTrue(validateInviteCode("TULS").isFailure) // Too short
        assertTrue(validateInviteCode("TULSI123").isFailure) // Too long
        assertTrue(validateInviteCode("TUL-01").isFailure) // Invalid symbols
        assertTrue(validateInviteCode("TUL SI").isFailure) // Space
    }

    @Test
    fun testNonAdminLeavesGroup() {
        val members = listOf(
            GroupMember(userId = "admin1", role = MemberRole.ADMIN),
            GroupMember(userId = "member1", role = MemberRole.MEMBER),
            GroupMember(userId = "member2", role = MemberRole.MEMBER)
        )
        val (remaining, action) = resolveGroupAdminOnLeave("member1", members)
        assertEquals(2, remaining.size)
        assertEquals("MEMBER_LEFT", action)
        assertTrue(remaining.any { it.userId == "admin1" && it.role == MemberRole.ADMIN })
    }

    @Test
    fun testAdminLeavesWithAnotherAdminRemaining() {
        val members = listOf(
            GroupMember(userId = "admin1", role = MemberRole.ADMIN),
            GroupMember(userId = "admin2", role = MemberRole.ADMIN),
            GroupMember(userId = "member1", role = MemberRole.MEMBER)
        )
        val (remaining, action) = resolveGroupAdminOnLeave("admin1", members)
        assertEquals(2, remaining.size)
        assertEquals("MEMBER_LEFT", action)
        assertTrue(remaining.any { it.userId == "admin2" && it.role == MemberRole.ADMIN })
    }

    @Test
    fun testSoleAdminLeavesPromotesNextMember() {
        val members = listOf(
            GroupMember(userId = "admin1", role = MemberRole.ADMIN),
            GroupMember(userId = "member1", role = MemberRole.MEMBER),
            GroupMember(userId = "member2", role = MemberRole.MEMBER)
        )
        val (remaining, action) = resolveGroupAdminOnLeave("admin1", members)
        assertEquals(2, remaining.size)
        assertEquals("PROMOTED_ADMIN", action)
        val newAdmin = remaining.find { it.userId == "member1" }
        assertEquals(MemberRole.ADMIN, newAdmin?.role)
    }

    @Test
    fun testLastMemberLeavesTriggersGroupDeletion() {
        val members = listOf(
            GroupMember(userId = "admin1", role = MemberRole.ADMIN)
        )
        val (remaining, action) = resolveGroupAdminOnLeave("admin1", members)
        assertTrue(remaining.isEmpty())
        assertEquals("DELETE_GROUP", action)
    }

    @Test
    fun testRolePermissions() {
        val admin = GroupMember(userId = "admin", role = MemberRole.ADMIN)
        val member = GroupMember(userId = "member", role = MemberRole.MEMBER)

        fun canManageMedicines(user: GroupMember): Boolean = user.role == MemberRole.ADMIN
        fun canActOnDose(user: GroupMember, assignedToId: String): Boolean {
            return user.role == MemberRole.ADMIN || user.userId == assignedToId
        }

        assertTrue(canManageMedicines(admin))
        assertFalse(canManageMedicines(member))

        assertTrue(canActOnDose(member, "member"))
        assertFalse(canActOnDose(member, "other_member"))
        assertTrue(canActOnDose(admin, "other_member"))
    }
}
