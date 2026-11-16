package com.tulsicare.app.core.messaging

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {

    suspend fun registerCurrentToken(userId: String) {
        try {
            val token = messaging.token.await()
            saveToken(userId, token)
        } catch (_: Exception) {
            // Firestore offline persistence handles queued writes
        }
    }

    suspend fun clearToken(userId: String) {
        try {
            val token = messaging.token.await()
            firestore.collection("users")
                .document(userId)
                .collection("fcmTokens")
                .document(token)
                .delete()
                .await()
        } catch (_: Exception) {
        }
    }

    private suspend fun saveToken(userId: String, token: String) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val tokenData = mapOf(
            "token" to token,
            "device" to deviceName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document(token)
            .set(tokenData)
            .await()
    }

    companion object {
        suspend fun updateToken(context: Context, token: String) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val firestore = FirebaseFirestore.getInstance()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val tokenData = mapOf(
                "token" to token,
                "device" to deviceName,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            try {
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("fcmTokens")
                    .document(token)
                    .set(tokenData)
                    .await()
            } catch (_: Exception) {
            }
        }

        suspend fun removeStaleToken(token: String) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val firestore = FirebaseFirestore.getInstance()

            try {
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("fcmTokens")
                    .document(token)
                    .delete()
                    .await()
            } catch (_: Exception) {
            }
        }
    }
}
