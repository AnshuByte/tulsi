package com.tulsicare.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_registry")
data class AlarmRecordEntity(
    @PrimaryKey
    val id: String, // deterministic: "${doseId}_${type}"
    val userId: String,
    val groupId: String,
    val medicineId: String,
    val doseId: String,
    val type: String, // PRE_REMINDER, DOSE_ALARM, SNOOZE, ESCALATION_CHECK, MISSED_NOTIFICATION
    val triggerAt: Long,
    val requestCode: Int
)
