package com.tulsicare.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tulsicare.app.core.database.entity.AlarmRecordEntity

@Dao
interface AlarmRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmRecord(record: AlarmRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmRecords(records: List<AlarmRecordEntity>)

    @Query("SELECT * FROM alarm_registry WHERE id = :id")
    suspend fun getAlarmRecord(id: String): AlarmRecordEntity?

    @Query("SELECT * FROM alarm_registry WHERE userId = :userId")
    suspend fun getRecordsForUser(userId: String): List<AlarmRecordEntity>

    @Query("SELECT * FROM alarm_registry WHERE groupId = :groupId")
    suspend fun getRecordsForGroup(groupId: String): List<AlarmRecordEntity>

    @Query("SELECT * FROM alarm_registry WHERE medicineId = :medicineId")
    suspend fun getRecordsForMedicine(medicineId: String): List<AlarmRecordEntity>

    @Query("SELECT * FROM alarm_registry WHERE doseId = :doseId")
    suspend fun getRecordsForDose(doseId: String): List<AlarmRecordEntity>

    @Query("SELECT * FROM alarm_registry WHERE doseId = :doseId AND type = :type")
    suspend fun getRecordForDoseAndType(doseId: String, type: String): AlarmRecordEntity?

    @Query("SELECT * FROM alarm_registry")
    suspend fun getAllRecords(): List<AlarmRecordEntity>

    @Query("DELETE FROM alarm_registry WHERE id = :id")
    suspend fun deleteRecord(id: String)

    @Query("DELETE FROM alarm_registry WHERE doseId = :doseId")
    suspend fun deleteRecordsForDose(doseId: String)

    @Query("DELETE FROM alarm_registry WHERE doseId = :doseId AND type = :type")
    suspend fun deleteRecordForDoseAndType(doseId: String, type: String)

    @Query("DELETE FROM alarm_registry WHERE medicineId = :medicineId")
    suspend fun deleteRecordsForMedicine(medicineId: String)

    @Query("DELETE FROM alarm_registry WHERE groupId = :groupId")
    suspend fun deleteRecordsForGroup(groupId: String)

    @Query("DELETE FROM alarm_registry WHERE userId = :userId")
    suspend fun deleteRecordsForUser(userId: String)

    @Query("DELETE FROM alarm_registry")
    suspend fun clearAll()
}
