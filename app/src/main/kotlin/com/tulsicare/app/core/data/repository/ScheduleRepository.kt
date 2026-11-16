package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeActiveSchedules(groupId: String): Flow<List<Schedule>>
    suspend fun getActiveSchedules(groupId: String): List<Schedule>
    suspend fun getSchedulesForMedicine(medicineId: String): List<Schedule>
    suspend fun addSchedule(schedule: Schedule): Result<String>
    suspend fun updateSchedule(schedule: Schedule): Result<Unit>
    suspend fun deleteSchedule(groupId: String, scheduleId: String): Result<Unit>
    fun syncSchedulesRealtime(groupId: String): Flow<List<Schedule>>
}
