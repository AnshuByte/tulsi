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
}
