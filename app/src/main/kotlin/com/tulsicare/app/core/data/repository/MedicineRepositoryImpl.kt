package com.tulsicare.app.core.data.repository

import android.content.Context
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.entity.toEntity
import com.tulsicare.app.core.model.Medicine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tulsicare.app.core.data.sync.FirestoreListenerManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val medicineDao: MedicineDao,
    private val firestore: FirebaseFirestore,
    private val firestoreListenerManager: FirestoreListenerManager
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

        // Cache in Room immediately for instant local UI display
        medicineDao.insertMedicine(toSave.toEntity())

        // Confirm written to Firestore before returning (only if real group)
        if (toSave.groupId.isNotBlank() && toSave.groupId != "default_group" && toSave.groupId != "my_family") {
            firestore.collection("groups")
                .document(toSave.groupId)
                .collection("medicines")
                .document(id)
                .set(toSave)
                .await()
        }

        id
    }

    override suspend fun updateMedicine(medicine: Medicine): Result<Unit> = runCatching {
        val updated = medicine.copy(updatedAt = System.currentTimeMillis())
        medicineDao.updateMedicine(updated.toEntity())

        if (updated.groupId.isNotBlank() && updated.groupId != "default_group" && updated.groupId != "my_family") {
            firestore.collection("groups")
                .document(updated.groupId)
                .collection("medicines")
                .document(updated.id)
                .set(updated)
                .await()
        }
    }

    override suspend fun deleteMedicine(groupId: String, medicineId: String): Result<Unit> = runCatching {
        // Cancel all alarms, pre-reminders, snoozes via registry
        ExactAlarmScheduler.cancelAlarmsForMedicine(context, medicineId)

        if (groupId.isNotBlank() && groupId != "default_group" && groupId != "my_family") {
            firestore.collection("groups")
                .document(groupId)
                .collection("medicines")
                .document(medicineId)
                .update("isActive", false)
                .await()
        }

        medicineDao.deleteMedicineById(medicineId)
    }

    override fun syncMedicinesRealtime(groupId: String): Flow<List<Medicine>> = callbackFlow {
        if (groupId.isBlank() || groupId == "default_group" || groupId == "my_family") {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val currentUid = auth.currentUser?.uid.orEmpty()
        val registration = firestore.collection("groups")
            .document(groupId)
            .collection("medicines")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val isNotFoundOrDenied = error.code == FirebaseFirestoreException.Code.NOT_FOUND ||
                            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    if (isNotFoundOrDenied) {
                        trySend(emptyList())
                    }
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

        if (currentUid.isNotBlank()) {
            firestoreListenerManager.register(currentUid, groupId, "medicines", registration)
        }

        awaitClose {
            if (currentUid.isNotBlank()) {
                firestoreListenerManager.removeListener(currentUid, groupId, "medicines")
            }
            registration.remove()
        }
    }
}
