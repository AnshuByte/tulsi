package com.tulsicare.app.core.model

enum class MedicineForm {
    PILL,
    CAPSULE,
    LIQUID,
    INJECTION,
    DROPS,
    INHALER,
    OTHER
}

data class Medicine(
    val id: String = "",
    val groupId: String = "",
    val assignedMemberId: String = "",
    val name: String = "",
    val dosage: String = "",
    val form: MedicineForm = MedicineForm.PILL,
    val instructions: String = "",
    val colorHex: String? = null,
    val escalationWindowMinutes: Int = 30,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
