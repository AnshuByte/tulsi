package com.tulsicare.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tulsicare.app.core.model.Schedule
import com.tulsicare.app.core.model.ScheduleType

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val medicineId: String,
    val type: ScheduleType,
    val daysOfWeek: List<Int>,
    val intervalHours: Int?,
    val timesOfDay: List<String>,
    val startDate: Long,
    val endDate: Long?,
    val isActive: Boolean
) {
    fun toDomainModel(): Schedule = Schedule(
        id = id,
        groupId = groupId,
        medicineId = medicineId,
        type = type,
        daysOfWeek = daysOfWeek,
        intervalHours = intervalHours,
        timesOfDay = timesOfDay,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive
    )
}

fun Schedule.toEntity(): ScheduleEntity = ScheduleEntity(
    id = id,
    groupId = groupId,
    medicineId = medicineId,
    type = type,
    daysOfWeek = daysOfWeek,
    intervalHours = intervalHours,
    timesOfDay = timesOfDay,
    startDate = startDate,
    endDate = endDate,
    isActive = isActive
)
