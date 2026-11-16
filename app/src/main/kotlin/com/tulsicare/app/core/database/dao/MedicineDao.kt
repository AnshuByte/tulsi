package com.tulsicare.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tulsicare.app.core.database.entity.MedicineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicines(medicines: List<MedicineEntity>)

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteMedicineById(id: String)

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getMedicineById(id: String): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE groupId = :groupId AND isActive = 1 ORDER BY name ASC")
    fun observeActiveMedicines(groupId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE groupId = :groupId AND isActive = 1")
    suspend fun getActiveMedicines(groupId: String): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id IN (:ids) AND isActive = 1")
    suspend fun getMedicinesByIds(ids: List<String>): List<MedicineEntity>

    @Query("DELETE FROM medicines WHERE groupId = :groupId")
    suspend fun clearMedicinesForGroup(groupId: String)
}
