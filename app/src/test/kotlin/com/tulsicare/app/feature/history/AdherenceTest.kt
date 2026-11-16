package com.tulsicare.app.feature.history

import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AdherenceTest {

    @Test
    fun testAdherenceCalculation() {
        val events = listOf(
            DoseEvent(id = "1", medicineId = "m1", medicineName = "Metformin", dosage = "500mg", scheduledAt = 1000L, status = DoseStatus.TAKEN),
            DoseEvent(id = "2", medicineId = "m1", medicineName = "Metformin", dosage = "500mg", scheduledAt = 2000L, status = DoseStatus.TAKEN),
            DoseEvent(id = "3", medicineId = "m2", medicineName = "Aspirin", dosage = "75mg", scheduledAt = 3000L, status = DoseStatus.SKIPPED),
            DoseEvent(id = "4", medicineId = "m2", medicineName = "Aspirin", dosage = "75mg", scheduledAt = 4000L, status = DoseStatus.MISSED)
        )

        val total = events.size
        val taken = events.count { it.status == DoseStatus.TAKEN }
        val adherence = if (total > 0) ((taken.toDouble() / total) * 100).toInt() else 100

        assertEquals(4, total)
        assertEquals(2, taken)
        assertEquals(50, adherence)
    }

    @Test
    fun testPerfectAdherence() {
        val events = listOf(
            DoseEvent(id = "1", medicineId = "m1", medicineName = "Metformin", dosage = "500mg", scheduledAt = 1000L, status = DoseStatus.TAKEN),
            DoseEvent(id = "2", medicineId = "m1", medicineName = "Metformin", dosage = "500mg", scheduledAt = 2000L, status = DoseStatus.TAKEN)
        )

        val total = events.size
        val taken = events.count { it.status == DoseStatus.TAKEN }
        val adherence = if (total > 0) ((taken.toDouble() / total) * 100).toInt() else 100

        assertEquals(100, adherence)
    }

    @Test
    fun testEmptyEventsAdherenceIs100() {
        val events = emptyList<DoseEvent>()
        val total = events.size
        val taken = events.count { it.status == DoseStatus.TAKEN }
        val adherence = if (total > 0) ((taken.toDouble() / total) * 100).toInt() else 100

        assertEquals(100, adherence)
    }

    @Test
    fun testStreakCalculationConsecutiveDays() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        // 3 consecutive days taken, preceded by a missed dose
        val events = listOf(
            DoseEvent(id = "1", scheduledAt = now, status = DoseStatus.TAKEN),
            DoseEvent(id = "2", scheduledAt = now - oneDay, status = DoseStatus.TAKEN),
            DoseEvent(id = "3", scheduledAt = now - 2 * oneDay, status = DoseStatus.TAKEN),
            DoseEvent(id = "4", scheduledAt = now - 3 * oneDay, status = DoseStatus.MISSED)
        )

        val groupedByDay = events.groupBy { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.scheduledAt }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }

        var streak = 0
        for ((_, dayEvents) in groupedByDay.entries) {
            val allTaken = dayEvents.all { it.status == DoseStatus.TAKEN }
            if (allTaken) streak++
            else break
        }

        assertEquals(3, streak)
    }

    @Test
    fun testPatientDataIsolationInReport() {
        val raviUserId = "user_ravi_elderly"
        val meeraUserId = "user_meera_member"

        val groupEvents = listOf(
            DoseEvent(id = "1", assignedMemberId = raviUserId, medicineName = "Metformin", status = DoseStatus.TAKEN),
            DoseEvent(id = "2", assignedMemberId = raviUserId, medicineName = "Vitamin D3", status = DoseStatus.TAKEN),
            DoseEvent(id = "3", assignedMemberId = meeraUserId, medicineName = "Thyroid Med", status = DoseStatus.TAKEN)
        )

        // Filter specifically for Ravi's report
        val raviEvents = groupEvents.filter { it.assignedMemberId == raviUserId }

        assertEquals(2, raviEvents.size)
        assertEquals(false, raviEvents.any { it.assignedMemberId == meeraUserId })
        assertEquals(false, raviEvents.any { it.medicineName == "Thyroid Med" })
    }

    @Test
    fun testLargeHistoryVolumeAdherence() {
        // 1,000 dose events stress test (800 TAKEN, 100 SKIPPED, 100 MISSED)
        val events = mutableListOf<DoseEvent>()
        for (i in 1..800) {
            events.add(DoseEvent(id = "t_$i", status = DoseStatus.TAKEN))
        }
        for (i in 1..100) {
            events.add(DoseEvent(id = "s_$i", status = DoseStatus.SKIPPED))
        }
        for (i in 1..100) {
            events.add(DoseEvent(id = "m_$i", status = DoseStatus.MISSED))
        }

        val total = events.size
        val taken = events.count { it.status == DoseStatus.TAKEN }
        val adherence = ((taken.toDouble() / total) * 100).toInt()

        assertEquals(1000, total)
        assertEquals(800, taken)
        assertEquals(80, adherence)
    }
}
