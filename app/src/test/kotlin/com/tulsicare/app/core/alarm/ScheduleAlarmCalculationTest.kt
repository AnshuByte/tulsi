package com.tulsicare.app.core.alarm

import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleAlarmCalculationTest {

    @Test
    fun testDailyScheduleUpcomingSameDay() {
        // Set fixed now: 2026-11-16 07:00:00 (Monday)
        val fixedCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 7, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = fixedCal.timeInMillis

        val schedule = Schedule(
            id = "s_daily",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.DAILY,
            timesOfDay = listOf("08:00", "20:00"),
            startDate = now - 10000,
            isActive = true
        )

        val nextTrigger = ExactAlarmScheduler.computeNextTriggerTime(schedule, now)
        assertNotNull(nextTrigger)

        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger!! }
        assertEquals(8, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertEquals(16, triggerCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testDailyScheduleRollsToNextDay() {
        // Set fixed now: 2026-11-16 21:00:00 (After all today slots)
        val fixedCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 21, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = fixedCal.timeInMillis

        val schedule = Schedule(
            id = "s_daily_late",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.DAILY,
            timesOfDay = listOf("08:00", "20:00"),
            startDate = now - 100000,
            isActive = true
        )

        val nextTrigger = ExactAlarmScheduler.computeNextTriggerTime(schedule, now)
        assertNotNull(nextTrigger)

        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger!! }
        assertEquals(8, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertEquals(17, triggerCal.get(Calendar.DAY_OF_MONTH)) // Tomorrow
    }

    @Test
    fun testSpecificWeekdaysMultiDayLookahead() {
        // 2026-11-20 is a Friday. Suppose time is 10:00 AM (after 09:00 slot).
        // Schedule is Mon(1), Wed(3), Fri(5) at 09:00.
        // Next slot should be MONDAY Nov 23 at 09:00!
        val fixedCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 20, 10, 0, 0) // Friday 10:00 AM
            set(Calendar.MILLISECOND, 0)
        }
        val now = fixedCal.timeInMillis

        val schedule = Schedule(
            id = "s_specific",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.SPECIFIC_DAYS,
            daysOfWeek = listOf(1, 3, 5), // Mon, Wed, Fri
            timesOfDay = listOf("09:00"),
            startDate = now - 100000,
            isActive = true
        )

        val nextTrigger = ExactAlarmScheduler.computeNextTriggerTime(schedule, now)
        assertNotNull(nextTrigger)

        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger!! }
        assertEquals(9, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertEquals(Calendar.MONDAY, triggerCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(23, triggerCal.get(Calendar.DAY_OF_MONTH)) // Monday Nov 23
    }

    @Test
    fun testAsNeededReturnsNull() {
        val now = System.currentTimeMillis()
        val schedule = Schedule(
            id = "s_prn",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.AS_NEEDED,
            timesOfDay = emptyList(),
            isActive = true
        )
        assertNull(ExactAlarmScheduler.computeNextTriggerTime(schedule, now))
    }

    @Test
    fun testExpiredScheduleReturnsNull() {
        val now = System.currentTimeMillis()
        val schedule = Schedule(
            id = "s_expired",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.DAILY,
            timesOfDay = listOf("08:00"),
            startDate = now - (30L * 24 * 60 * 60 * 1000L),
            endDate = now - (10L * 24 * 60 * 60 * 1000L),
            isActive = false
        )
        assertNull(ExactAlarmScheduler.computeNextTriggerTime(schedule, now))
    }

    @Test
    fun testFutureStartDateRespected() {
        val now = System.currentTimeMillis()
        val futureStart = now + (3L * 24 * 60 * 60 * 1000L) // 3 days in future

        val schedule = Schedule(
            id = "s_future",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.DAILY,
            timesOfDay = listOf("12:00"),
            startDate = futureStart,
            isActive = true
        )

        val nextTrigger = ExactAlarmScheduler.computeNextTriggerTime(schedule, now)
        assertNotNull(nextTrigger)
        assertTrue(nextTrigger!! >= futureStart)
    }

    @Test
    fun testMidnightRollover() {
        // 2026-11-16 23:50:00 (10 minutes before midnight)
        val fixedCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 23, 50, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = fixedCal.timeInMillis

        val schedule = Schedule(
            id = "s_midnight",
            groupId = "g1",
            medicineId = "m1",
            type = ScheduleType.DAILY,
            timesOfDay = listOf("00:00", "23:59"),
            startDate = now - 100000,
            isActive = true
        )

        val nextTrigger = ExactAlarmScheduler.computeNextTriggerTime(schedule, now)
        assertNotNull(nextTrigger)

        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger!! }
        // Earliest upcoming should be 23:59 of same day!
        assertEquals(23, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, triggerCal.get(Calendar.MINUTE))
        assertEquals(16, triggerCal.get(Calendar.DAY_OF_MONTH))
    }
}
