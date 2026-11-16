package com.tulsicare.app.core.model

enum class DoseStatus {
    PENDING,
    TAKEN,
    SKIPPED,
    SNOOZED,
    MISSED
}

data class DoseEvent(
    val id: String = "",
    val groupId: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val dosage: String = "",
    val assignedMemberId: String = "",
    val scheduleId: String = "",
    val scheduledAt: Long = 0L, // Epoch millis
    val status: DoseStatus = DoseStatus.PENDING,
    val actedAt: Long? = null,
    val actedBy: String? = null,
    val snoozeUntil: Long? = null,
    val snoozeCount: Int = 0,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

fun generateDoseId(medicineId: String, scheduledAt: Long): String {
    return "dose_${medicineId}_${scheduledAt}"
}
