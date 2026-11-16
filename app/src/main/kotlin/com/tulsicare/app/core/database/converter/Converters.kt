package com.tulsicare.app.core.database.converter

import androidx.room.TypeConverter
import com.tulsicare.app.core.model.DoseStatus
import com.tulsicare.app.core.model.MedicineForm
import com.tulsicare.app.core.model.MemberRole
import com.tulsicare.app.core.model.ScheduleType

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    @TypeConverter
    fun fromMedicineForm(form: MedicineForm): String = form.name

    @TypeConverter
    fun toMedicineForm(value: String): MedicineForm {
        return runCatching { MedicineForm.valueOf(value) }.getOrDefault(MedicineForm.PILL)
    }

    @TypeConverter
    fun fromScheduleType(type: ScheduleType): String = type.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType {
        return runCatching { ScheduleType.valueOf(value) }.getOrDefault(ScheduleType.DAILY)
    }

    @TypeConverter
    fun fromDoseStatus(status: DoseStatus): String = status.name

    @TypeConverter
    fun toDoseStatus(value: String): DoseStatus {
        return runCatching { DoseStatus.valueOf(value) }.getOrDefault(DoseStatus.PENDING)
    }

    @TypeConverter
    fun fromMemberRole(role: MemberRole): String = role.name

    @TypeConverter
    fun toMemberRole(value: String): MemberRole {
        return runCatching { MemberRole.valueOf(value) }.getOrDefault(MemberRole.MEMBER)
    }
}
