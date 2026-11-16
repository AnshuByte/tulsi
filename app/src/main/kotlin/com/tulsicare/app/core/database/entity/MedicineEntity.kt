package com.tulsicare.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tulsicare.app.core.model.Medicine
import com.tulsicare.app.core.model.MedicineForm

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val assignedMemberId: String,
    val name: String,
    val dosage: String,
    val form: MedicineForm,
    val instructions: String,
    val colorHex: String?,
    val escalationWindowMinutes: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): Medicine = Medicine(
        id = id,
        groupId = groupId,
        assignedMemberId = assignedMemberId,
        name = name,
        dosage = dosage,
        form = form,
        instructions = instructions,
        colorHex = colorHex,
        escalationWindowMinutes = escalationWindowMinutes,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Medicine.toEntity(): MedicineEntity = MedicineEntity(
    id = id,
    groupId = groupId,
    assignedMemberId = assignedMemberId,
    name = name,
    dosage = dosage,
    form = form,
    instructions = instructions,
    colorHex = colorHex,
    escalationWindowMinutes = escalationWindowMinutes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)
