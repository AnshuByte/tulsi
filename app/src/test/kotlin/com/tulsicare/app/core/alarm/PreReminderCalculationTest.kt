package com.tulsicare.app.core.alarm

import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.model.ScheduleType
import com.tulsicare.app.core.model.generateDoseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PreReminderCalculationTest {

    @Test
    fun testPreReminderLeadTimeCalculation() {
        // Dose at 10:00 AM
        val doseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val doseTime = doseCal.timeInMillis

        // Test lead times: 5, 10, 15, 30
        val lead5 = doseTime - (5 * 60 * 1000L)
        val lead10 = doseTime - (10 * 60 * 1000L)
        val lead15 = doseTime - (15 * 60 * 1000L)
        val lead30 = doseTime - (30 * 60 * 1000L)

        val cal = Calendar.getInstance()

        cal.timeInMillis = lead5
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(55, cal.get(Calendar.MINUTE))

        cal.timeInMillis = lead10
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(50, cal.get(Calendar.MINUTE))

        cal.timeInMillis = lead15
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, cal.get(Calendar.MINUTE))

        cal.timeInMillis = lead30
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testMidnightCrossingPreReminder() {
        // Dose at 00:05 AM on Nov 17
        val doseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 0, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val doseTime = doseCal.timeInMillis

        // 10-minute pre-reminder should be at 23:55 on Nov 16
        val preReminderTime = doseTime - (10 * 60 * 1000L)
        val preCal = Calendar.getInstance().apply { timeInMillis = preReminderTime }

        assertEquals(16, preCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, preCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(55, preCal.get(Calendar.MINUTE))
    }

    @Test
    fun testSkipPreReminderIfCreatedLessThanLeadTimeBeforeDue() {
        // Dose is in 6 minutes
        val now = System.currentTimeMillis()
        val doseTime = now + (6 * 60 * 1000L)
        val leadTime = 10 * 60 * 1000L

        val preReminderTime = doseTime - leadTime
        // Pre-reminder time is in the past!
        assertTrue("Pre-reminder time is before now", preReminderTime < now)
        // Rule: if preReminderTime <= now, skip pre-reminder and only schedule alarm
        val shouldSchedulePreReminder = preReminderTime > now
        assertFalse(shouldSchedulePreReminder)
    }

    @Test
    fun testTwoDosesWithin10MinutesHaveUniqueIdsAndRequestCodes() {
        val now = 1700000000000L
        val dose1Time = now + (30 * 60 * 1000L)
        val dose2Time = now + (35 * 60 * 1000L) // 5 minutes apart

        val dose1Id = generateDoseId("med_1", dose1Time)
        val dose2Id = generateDoseId("med_1", dose2Time)

        assertNotEquals(dose1Id, dose2Id)

        val code1Pre = ExactAlarmScheduler.generateRequestCode(dose1Id, AlarmType.PRE_REMINDER.name)
        val code2Pre = ExactAlarmScheduler.generateRequestCode(dose2Id, AlarmType.PRE_REMINDER.name)
        val code1Alarm = ExactAlarmScheduler.generateRequestCode(dose1Id, AlarmType.DOSE_ALARM.name)

        assertNotEquals(code1Pre, code2Pre)
        assertNotEquals(code1Pre, code1Alarm)
    }

    @Test
    fun testTimezoneDifferenceHandling() {
        val utcTz = TimeZone.getTimeZone("UTC")
        val istTz = TimeZone.getTimeZone("Asia/Kolkata") // UTC + 5:30

        val utcCal = Calendar.getInstance(utcTz).apply {
            set(2026, Calendar.NOVEMBER, 16, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val istCal = Calendar.getInstance(istTz).apply {
            timeInMillis = utcCal.timeInMillis
        }

        // 08:00 UTC = 13:30 IST
        assertEquals(8, utcCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(13, istCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, istCal.get(Calendar.MINUTE))

        // Epoch millis is invariant under timezone change
        assertEquals(utcCal.timeInMillis, istCal.timeInMillis)
    }

    @Test
    fun testDaylightSavingTimeTransition() {
        // In US Eastern Time (America/New_York), fall transition occurs in November
        val nyTz = TimeZone.getTimeZone("America/New_York")
        val schedule = Schedule(
            id = "s_dst",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.DAILY,
            timesOfDay = listOf("08:00"),
            startDate = 0L,
            isActive = true
        )

        val cal = Calendar.getInstance(nyTz).apply {
            set(2026, Calendar.OCTOBER, 31, 20, 0, 0)
        }
        val upcoming = ExactAlarmScheduler.computeUpcomingTriggerTimes(
            schedule = schedule,
            now = cal.timeInMillis,
            lookaheadDays = 3,
            timeZone = nyTz
        )
        assertTrue(upcoming.isNotEmpty())

        for (time in upcoming) {
            val checkCal = Calendar.getInstance(nyTz).apply { timeInMillis = time }
            assertEquals(8, checkCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, checkCal.get(Calendar.MINUTE))
        }
    }
}
