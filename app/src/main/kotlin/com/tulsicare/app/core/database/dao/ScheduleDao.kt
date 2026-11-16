package com.tulsicare.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tulsicare.app.core.database.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: String)

    @Query("DELETE FROM schedules WHERE medicineId = :medicineId")
    suspend fun deleteSchedulesByMedicineId(medicineId: String)

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: String): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE medicineId = :medicineId AND isActive = 1")
    suspend fun getSchedulesForMedicine(medicineId: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE groupId = :groupId AND isActive = 1")
    fun observeActiveSchedules(groupId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE groupId = :groupId AND isActive = 1")
    suspend fun getActiveSchedules(groupId: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    suspend fun getAllActiveSchedules(): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE groupId = :groupId")
    suspend fun clearSchedulesForGroup(groupId: String)
}
