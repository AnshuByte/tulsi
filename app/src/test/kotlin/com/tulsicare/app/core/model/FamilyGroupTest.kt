package com.tulsicare.app.core.model

import com.tulsicare.app.core.database.entity.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyGroupTest {

    @Test
    fun familyGroupEntityMapping() {
        val group = FamilyGroup(
            id = "grp_test_1",
            name = "Verma Family",
            inviteCode = "DOSE42",
            createdBy = "usr_1",
            createdAt = 1731600000000L
        )

        val entity = group.toEntity()
        val domain = entity.toDomainModel()

        assertEquals(group, domain)
    }

    @Test
    fun groupMemberEntityMapping() {
        val member = GroupMember(
            userId = "usr_1",
            displayName = "Grandma",
            role = MemberRole.ADMIN,
            isPatient = true,
            phoneNumber = "+1234567890",
            joinedAt = 1731600000000L
        )

        val entity = member.toEntity(groupId = "grp_test_1")
        val domain = entity.toDomainModel()

        assertEquals(member, domain)
        assertEquals("grp_test_1", entity.groupId)
    }

    @Test
    fun memberRoleEnumValidity() {
        assertEquals(MemberRole.ADMIN, MemberRole.valueOf("ADMIN"))
        assertEquals(MemberRole.MEMBER, MemberRole.valueOf("MEMBER"))
    }
}
