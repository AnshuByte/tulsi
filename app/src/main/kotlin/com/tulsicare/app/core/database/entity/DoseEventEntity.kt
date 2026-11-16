package com.tulsicare.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus

@Entity(tableName = "dose_events")
data class DoseEventEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val medicineId: String,
    val medicineName: String,
    val dosage: String,
    val assignedMemberId: String,
    val scheduleId: String,
    val scheduledAt: Long,
    val status: DoseStatus,
    val actedAt: Long?,
    val actedBy: String?,
    val snoozeUntil: Long?,
    val notes: String?,
    val createdAt: Long
) {
    fun toDomainModel(): DoseEvent = DoseEvent(
        id = id,
        groupId = groupId,
        medicineId = medicineId,
        medicineName = medicineName,
        dosage = dosage,
        assignedMemberId = assignedMemberId,
        scheduleId = scheduleId,
        scheduledAt = scheduledAt,
        status = status,
        actedAt = actedAt,
        actedBy = actedBy,
        snoozeUntil = snoozeUntil,
        notes = notes,
        createdAt = createdAt
    )
}

fun DoseEvent.toEntity(): DoseEventEntity = DoseEventEntity(
    id = id,
    groupId = groupId,
    medicineId = medicineId,
    medicineName = medicineName,
    dosage = dosage,
    assignedMemberId = assignedMemberId,
    scheduleId = scheduleId,
    scheduledAt = scheduledAt,
    status = status,
    actedAt = actedAt,
    actedBy = actedBy,
    snoozeUntil = snoozeUntil,
    notes = notes,
    createdAt = createdAt
)
