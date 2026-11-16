package com.tulsicare.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DoseStateTransitionTest {

    @Test
    fun testDeterministicDoseIdGeneration() {
        val medId = "med_metformin_500"
        val scheduledAt = 1763308800000L

        val id1 = generateDoseId(medId, scheduledAt)
        val id2 = generateDoseId(medId, scheduledAt)

        assertEquals("dose_med_metformin_500_1763308800000", id1)
        assertEquals(id1, id2)
    }

    @Test
    fun testTransitionFromPendingToTaken() {
        val now = 1763308800000L
        val dose = DoseEvent(
            id = generateDoseId("m1", now),
            groupId = "g1",
            medicineId = "m1",
            scheduledAt = now,
            status = DoseStatus.PENDING
        )

        assertEquals(DoseStatus.PENDING, dose.status)
        assertNull(dose.actedAt)
        assertNull(dose.actedBy)

        val actedTime = now + 120000L
        val taken = dose.copy(
            status = DoseStatus.TAKEN,
            actedAt = actedTime,
            actedBy = "user_123"
        )

        assertEquals(DoseStatus.TAKEN, taken.status)
        assertEquals(actedTime, taken.actedAt)
        assertEquals("user_123", taken.actedBy)
    }

    @Test
    fun testTransitionFromPendingToSkipped() {
        val now = 1763308800000L
        val dose = DoseEvent(
            id = generateDoseId("m1", now),
            groupId = "g1",
            medicineId = "m1",
            scheduledAt = now,
            status = DoseStatus.PENDING
        )

        val actedTime = now + 60000L
        val skipped = dose.copy(
            status = DoseStatus.SKIPPED,
            actedAt = actedTime,
            actedBy = "user_admin"
        )

        assertEquals(DoseStatus.SKIPPED, skipped.status)
        assertEquals(actedTime, skipped.actedAt)
        assertEquals("user_admin", skipped.actedBy)
    }

    @Test
    fun testTransitionFromPendingToSnoozedIncrementsCount() {
        val now = 1763308800000L
        val dose = DoseEvent(
            id = generateDoseId("m1", now),
            groupId = "g1",
            medicineId = "m1",
            scheduledAt = now,
            status = DoseStatus.PENDING,
            snoozeCount = 0
        )

        val snoozeMinutes = 10
        val snoozeUntil = now + (snoozeMinutes * 60 * 1000L)
        val snoozed = dose.copy(
            status = DoseStatus.SNOOZED,
            snoozeUntil = snoozeUntil,
            snoozeCount = dose.snoozeCount + 1,
            actedAt = now,
            actedBy = "user_1"
        )

        assertEquals(DoseStatus.SNOOZED, snoozed.status)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(snoozeUntil, snoozed.snoozeUntil)

        // Second snooze (e.g. 15 min later)
        val secondSnoozeMinutes = 15
        val secondSnoozeUntil = snoozeUntil + (secondSnoozeMinutes * 60 * 1000L)
        val snoozedAgain = snoozed.copy(
            status = DoseStatus.SNOOZED,
            snoozeUntil = secondSnoozeUntil,
            snoozeCount = snoozed.snoozeCount + 1
        )

        assertEquals(2, snoozedAgain.snoozeCount)
        assertEquals(secondSnoozeUntil, snoozedAgain.snoozeUntil)
    }

    @Test
    fun testTransitionFromSnoozedToTaken() {
        val now = 1763308800000L
        val snoozed = DoseEvent(
            id = generateDoseId("m1", now),
            groupId = "g1",
            medicineId = "m1",
            scheduledAt = now,
            status = DoseStatus.SNOOZED,
            snoozeUntil = now + 600000L,
            snoozeCount = 1
        )

        val taken = snoozed.copy(
            status = DoseStatus.TAKEN,
            actedAt = now + 300000L,
            actedBy = "user_1"
        )

        assertEquals(DoseStatus.TAKEN, taken.status)
        assertEquals(1, taken.snoozeCount)
        assertNotNull(taken.actedAt)
    }
}
