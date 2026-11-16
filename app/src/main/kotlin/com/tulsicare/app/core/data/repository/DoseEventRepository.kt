package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import kotlinx.coroutines.flow.Flow

interface DoseEventRepository {
    fun observeEventsForDateRange(groupId: String, startMillis: Long, endMillis: Long): Flow<List<DoseEvent>>
    suspend fun getEventsForDateRange(groupId: String, startMillis: Long, endMillis: Long): List<DoseEvent>
    suspend fun recordDose(
        medicineId: String,
        scheduledAt: Long,
        status: DoseStatus,
        actedBy: String? = null,
        snoozeMinutes: Int? = null
    ): Result<DoseEvent>
    suspend fun recordDoseAction(groupId: String, eventId: String, status: DoseStatus, actedBy: String): Result<Unit>
    suspend fun snoozeDose(groupId: String, eventId: String, snoozeMinutes: Int): Result<Unit>
    suspend fun generateTodayDosesIfMissing(groupId: String, targetDateMillis: Long = System.currentTimeMillis()): Result<List<DoseEvent>>
    fun syncDoseEventsRealtime(groupId: String, startMillis: Long, endMillis: Long): Flow<List<DoseEvent>>
}
