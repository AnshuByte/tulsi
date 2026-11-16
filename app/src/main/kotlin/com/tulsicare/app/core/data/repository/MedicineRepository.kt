package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.model.Medicine
import kotlinx.coroutines.flow.Flow

interface MedicineRepository {
    fun observeActiveMedicines(groupId: String): Flow<List<Medicine>>
    suspend fun getActiveMedicines(groupId: String): List<Medicine>
    suspend fun getMedicineById(id: String): Medicine?
    suspend fun addMedicine(medicine: Medicine): Result<String>
    suspend fun updateMedicine(medicine: Medicine): Result<Unit>
    suspend fun deleteMedicine(groupId: String, medicineId: String): Result<Unit>
    fun syncMedicinesRealtime(groupId: String): Flow<List<Medicine>>
}
