package com.tulsicare.app.core.model

import com.tulsicare.app.core.database.converter.Converters
import com.tulsicare.app.core.database.entity.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelTest {

    private val converters = Converters()

    @Test
    fun testStringListConverter() {
        val original = listOf("08:00", "14:00", "20:00")
        val serialized = converters.fromStringList(original)
        val deserialized = converters.toStringList(serialized)
        assertEquals(original, deserialized)

        // Empty string handling
        assertTrue(converters.toStringList("").isEmpty())
        assertTrue(converters.toStringList(null).isEmpty())
    }

    @Test
    fun testIntListConverter() {
        val original = listOf(1, 2, 3, 4, 5)
        val serialized = converters.fromIntList(original)
        val deserialized = converters.toIntList(serialized)
        assertEquals(original, deserialized)

        assertTrue(converters.toIntList("").isEmpty())
    }

    @Test
    fun testEnumConverters() {
        assertEquals(MedicineForm.INHALER, converters.toMedicineForm("INHALER"))
        assertEquals(ScheduleType.SPECIFIC_DAYS, converters.toScheduleType("SPECIFIC_DAYS"))
        assertEquals(DoseStatus.TAKEN, converters.toDoseStatus("TAKEN"))
        assertEquals(MemberRole.ADMIN, converters.toMemberRole("ADMIN"))

        // Graceful fallback on unexpected values
        assertEquals(MedicineForm.PILL, converters.toMedicineForm("UNKNOWN_FORM"))
        assertEquals(ScheduleType.DAILY, converters.toScheduleType("UNKNOWN_TYPE"))
        assertEquals(DoseStatus.PENDING, converters.toDoseStatus("UNKNOWN_STATUS"))
        assertEquals(MemberRole.MEMBER, converters.toMemberRole("UNKNOWN_ROLE"))
    }

    @Test
    fun testMedicineEntityMapping() {
        val medicine = Medicine(
            id = "med-1",
            groupId = "grp-1",
            assignedMemberId = "usr-1",
            name = "Metformin",
            dosage = "500mg",
            form = MedicineForm.PILL,
            instructions = "With food",
            colorHex = "#0F9D8A",
            escalationWindowMinutes = 45,
            isActive = true
        )

        val entity = medicine.toEntity()
        val restored = entity.toDomainModel()

        assertEquals(medicine, restored)
    }

    @Test
    fun testScheduleEntityMapping() {
        val schedule = Schedule(
            id = "sch-1",
            groupId = "grp-1",
            medicineId = "med-1",
            type = ScheduleType.SPECIFIC_DAYS,
            daysOfWeek = listOf(1, 3, 5),
            timesOfDay = listOf("09:00", "21:00"),
            isActive = true
        )

        val entity = schedule.toEntity()
        val restored = entity.toDomainModel()

        assertEquals(schedule, restored)
    }

    @Test
    fun testDoseEventEntityMapping() {
        val event = DoseEvent(
            id = "evt-1",
            groupId = "grp-1",
            medicineId = "med-1",
            medicineName = "Metformin",
            dosage = "500mg",
            assignedMemberId = "usr-1",
            scheduleId = "sch-1",
            scheduledAt = 1731600000000L,
            status = DoseStatus.PENDING
        )

        val entity = event.toEntity()
        val restored = entity.toDomainModel()

        assertEquals(event, restored)
    }
}
