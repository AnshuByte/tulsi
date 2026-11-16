package com.tulsicare.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tulsicare.app.core.database.entity.DoseEventEntity
import com.tulsicare.app.core.model.DoseStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseEvent(event: DoseEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseEvents(events: List<DoseEventEntity>)

    @Update
    suspend fun updateDoseEvent(event: DoseEventEntity)

    @Query("""
        UPDATE dose_events 
        SET status = :status, actedAt = :actedAt, actedBy = :actedBy 
        WHERE id = :id
    """)
    suspend fun updateDoseStatus(
        id: String,
        status: DoseStatus,
        actedAt: Long,
        actedBy: String?
    )

    @Query("""
        UPDATE dose_events 
        SET status = :status, snoozeUntil = :snoozeUntil 
        WHERE id = :id
    """)
    suspend fun snoozeDose(
        id: String,
        status: DoseStatus,
        snoozeUntil: Long
    )

    @Query("SELECT * FROM dose_events WHERE id = :id")
    suspend fun getDoseEventById(id: String): DoseEventEntity?

    @Query("""
        SELECT * FROM dose_events 
        WHERE groupId = :groupId AND scheduledAt >= :startMillis AND scheduledAt <= :endMillis 
        ORDER BY scheduledAt ASC
    """)
    fun observeEventsForDateRange(
        groupId: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<DoseEventEntity>>

    @Query("""
        SELECT * FROM dose_events 
        WHERE groupId = :groupId AND scheduledAt >= :startMillis AND scheduledAt <= :endMillis 
        ORDER BY scheduledAt ASC
    """)
    suspend fun getEventsForDateRange(
        groupId: String,
        startMillis: Long,
        endMillis: Long
    ): List<DoseEventEntity>

    @Query("""
        SELECT * FROM dose_events 
        WHERE scheduleId = :scheduleId AND scheduledAt = :scheduledAt 
        LIMIT 1
    """)
    suspend fun getEventForScheduleAndSlot(scheduleId: String, scheduledAt: Long): DoseEventEntity?

    @Query("DELETE FROM dose_events WHERE groupId = :groupId")
    suspend fun clearEventsForGroup(groupId: String)
}
