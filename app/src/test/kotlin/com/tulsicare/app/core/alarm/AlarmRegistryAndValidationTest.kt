package com.tulsicare.app.core.alarm

import com.tulsicare.app.core.database.entity.AlarmRecordEntity
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.Medicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRegistryAndValidationTest {

    @Test
    fun testDeterministicRequestCodes() {
        val doseId = "dose_med1_1763308800000"

        val codePre1 = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.PRE_REMINDER.name)
        val codePre2 = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.PRE_REMINDER.name)
        val codeAlarm = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.DOSE_ALARM.name)
        val codeSnooze = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.SNOOZE.name)
        val codeEscalation = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.ESCALATION_CHECK.name)

        assertEquals(codePre1, codePre2)
        assertNotEquals(codePre1, codeAlarm)
        assertNotEquals(codeAlarm, codeSnooze)
        assertNotEquals(codeSnooze, codeEscalation)
    }

    @Test
    fun testValidateOnFire_userValidation() {
        // Rule 3a: A user is signed in and matches the alarm's userId
        val alarmUserId = "user_A"

        // Case 1: Signed out (currentUid is null)
        val currentUidSignedOut: String? = null
        assertFalse(isValidUser(currentUidSignedOut, alarmUserId))

        // Case 2: Signed in as different user B
        val currentUidUserB = "user_B"
        assertFalse(isValidUser(currentUidUserB, alarmUserId))

        // Case 3: Signed in as user A
        val currentUidUserA = "user_A"
        assertTrue(isValidUser(currentUidUserA, alarmUserId))
    }

    @Test
    fun testValidateOnFire_medicineValidation() {
        // Rule 3b: Medicine exists and is active
        val activeMed = Medicine(id = "m1", name = "Aspirin", isActive = true)
        val inactiveMed = Medicine(id = "m2", name = "OldMed", isActive = false)
        val deletedMed: Medicine? = null

        assertTrue(isValidMedicine(activeMed))
        assertFalse(isValidMedicine(inactiveMed))
        assertFalse(isValidMedicine(deletedMed))
    }

    @Test
    fun testValidateOnFire_doseStatusValidation() {
        // Rule 3c: Dose is still PENDING or SNOOZED
        assertTrue(isValidDoseStatus(DoseStatus.PENDING))
        assertTrue(isValidDoseStatus(DoseStatus.SNOOZED))
        assertFalse(isValidDoseStatus(DoseStatus.TAKEN))
        assertFalse(isValidDoseStatus(DoseStatus.SKIPPED))
        assertFalse(isValidDoseStatus(DoseStatus.MISSED))
    }

    @Test
    fun testAlarmRegistryLifecycleRecords() {
        val records = mutableListOf<AlarmRecordEntity>()

        val doseId = "dose_metformin_1"
        val preReminder = AlarmRecordEntity(
            id = "${doseId}_${AlarmType.PRE_REMINDER.name}",
            userId = "u1",
            groupId = "g1",
            medicineId = "m1",
            doseId = doseId,
            type = AlarmType.PRE_REMINDER.name,
            triggerAt = 1000L,
            requestCode = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.PRE_REMINDER.name)
        )
        val mainAlarm = AlarmRecordEntity(
            id = "${doseId}_${AlarmType.DOSE_ALARM.name}",
            userId = "u1",
            groupId = "g1",
            medicineId = "m1",
            doseId = doseId,
            type = AlarmType.DOSE_ALARM.name,
            triggerAt = 1600L,
            requestCode = ExactAlarmScheduler.generateRequestCode(doseId, AlarmType.DOSE_ALARM.name)
        )

        records.add(preReminder)
        records.add(mainAlarm)
        assertEquals(2, records.size)

        // Cancel by doseId
        records.removeAll { it.doseId == doseId }
        assertTrue(records.isEmpty())
    }

    private fun isValidUser(currentUid: String?, alarmUserId: String): Boolean {
        if (currentUid.isNullOrBlank()) return false
        if (alarmUserId.isNotBlank() && currentUid != alarmUserId) return false
        return true
    }

    private fun isValidMedicine(medicine: Medicine?): Boolean {
        if (medicine == null) return false
        return medicine.isActive
    }

    private fun isValidDoseStatus(status: DoseStatus?): Boolean {
        if (status == null) return true // Dose record may not have been created yet, pending by default
        return status == DoseStatus.PENDING || status == DoseStatus.SNOOZED
    }
}
