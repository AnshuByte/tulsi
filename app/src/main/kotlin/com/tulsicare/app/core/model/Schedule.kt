package com.tulsicare.app.core.model

enum class ScheduleType {
    DAILY,
    SPECIFIC_DAYS,
    INTERVAL,
    AS_NEEDED
}

data class Schedule(
    val id: String = "",
    val groupId: String = "",
    val medicineId: String = "",
    val type: ScheduleType = ScheduleType.DAILY,
    val daysOfWeek: List<Int> = emptyList(), // 1 = Monday ... 7 = Sunday
    val intervalHours: Int? = null,
    val timesOfDay: List<String> = emptyList(), // "HH:mm" strings, e.g. ["08:00", "20:00"]
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val isActive: Boolean = true
)
