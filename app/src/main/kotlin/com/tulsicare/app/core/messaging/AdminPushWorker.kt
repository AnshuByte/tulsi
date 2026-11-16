package com.tulsicare.app.core.messaging

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tulsicare.app.core.network.PushRelayClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class AdminPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AdminPushEntryPoint {
        fun pushRelayClient(): PushRelayClient
    }

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()
        val actorUserId = inputData.getString(KEY_ACTOR_USER_ID) ?: return Result.failure()
        val actorName = inputData.getString(KEY_ACTOR_NAME) ?: "Family Member"
        val medicineName = inputData.getString(KEY_MEDICINE_NAME) ?: "Medicine"
        val dosage = inputData.getString(KEY_DOSAGE).orEmpty()
        val status = inputData.getString(KEY_STATUS) ?: return Result.failure()
        val time = inputData.getString(KEY_TIME).orEmpty()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AdminPushEntryPoint::class.java
        )

        val result = entryPoint.pushRelayClient().relayDoseStatusToAdmin(
            groupId = groupId,
            actorUserId = actorUserId,
            actorName = actorName,
            medicineName = medicineName,
            dosage = dosage,
            status = status,
            time = time
        )

        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_GROUP_ID = "key_group_id"
        const val KEY_ACTOR_USER_ID = "key_actor_user_id"
        const val KEY_ACTOR_NAME = "key_actor_name"
        const val KEY_MEDICINE_NAME = "key_medicine_name"
        const val KEY_DOSAGE = "key_dosage"
        const val KEY_STATUS = "key_status"
        const val KEY_TIME = "key_time"

        fun enqueue(
            context: Context,
            groupId: String,
            actorUserId: String,
            actorName: String,
            medicineName: String,
            dosage: String,
            status: String,
            time: String,
            doseId: String
        ) {
            if (groupId.isBlank() || actorUserId.isBlank()) return

            val data = Data.Builder()
                .putString(KEY_GROUP_ID, groupId)
                .putString(KEY_ACTOR_USER_ID, actorUserId)
                .putString(KEY_ACTOR_NAME, actorName)
                .putString(KEY_MEDICINE_NAME, medicineName)
                .putString(KEY_DOSAGE, dosage)
                .putString(KEY_STATUS, status)
                .putString(KEY_TIME, time)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AdminPushWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "push_${doseId}_${status}",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
