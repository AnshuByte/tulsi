package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.Medicine
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicineRepositoryImpl @Inject constructor(
    private val medicineDao: MedicineDao,
    private val firestore: FirebaseFirestore
) : MedicineRepository {

    override fun observeActiveMedicines(groupId: String): Flow<List<Medicine>> {
        return medicineDao.observeActiveMedicines(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getActiveMedicines(groupId: String): List<Medicine> {
        return medicineDao.getActiveMedicines(groupId).map { it.toDomainModel() }
    }

    override suspend fun getMedicineById(id: String): Medicine? {
        return medicineDao.getMedicineById(id)?.toDomainModel()
    }

    override suspend fun addMedicine(medicine: Medicine): Result<String> = runCatching {
        val id = if (medicine.id.isBlank()) UUID.randomUUID().toString() else medicine.id
        val now = System.currentTimeMillis()
        val toSave = medicine.copy(id = id, createdAt = now, updatedAt = now)

        // Write to Firestore first
        firestore.collection("groups")
            .document(toSave.groupId)
            .collection("medicines")
            .document(id)
            .set(toSave)
            .await()

        // Cache in Room
        medicineDao.insertMedicine(toSave.toEntity())
        id
    }

    override suspend fun updateMedicine(medicine: Medicine): Result<Unit> = runCatching {
        val updated = medicine.copy(updatedAt = System.currentTimeMillis())
        firestore.collection("groups")
            .document(updated.groupId)
            .collection("medicines")
            .document(updated.id)
            .set(updated)
            .await()

        medicineDao.updateMedicine(updated.toEntity())
    }

    override suspend fun deleteMedicine(groupId: String, medicineId: String): Result<Unit> = runCatching {
        // Soft delete in Firestore (set isActive = false) or hard delete
        firestore.collection("groups")
            .document(groupId)
            .collection("medicines")
            .document(medicineId)
            .update("isActive", false)
            .await()

        medicineDao.deleteMedicineById(medicineId)
    }

    override fun syncMedicinesRealtime(groupId: String): Flow<List<Medicine>> = callbackFlow {
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("medicines")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val medicines = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Medicine::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                if (medicines.isNotEmpty()) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        medicineDao.insertMedicines(medicines.map { it.toEntity() })
                    }
                }

                trySend(medicines)
            }

        awaitClose { registration.remove() }
    }
}
