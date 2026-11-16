package com.tulsicare.app.core.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRelayClient @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    // Configured with live Cloudflare Worker URL
    var workerBaseUrl: String = "https://tulsi-worker.tulsi-health.workers.dev"

    suspend fun relayDoseStatus(
        groupId: String,
        memberName: String,
        medicineName: String,
        dosage: String,
        status: String,
        time: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (workerBaseUrl.isBlank()) return@runCatching

            val currentUser = auth.currentUser ?: return@runCatching
            val idToken = currentUser.getIdToken(false).await()?.token ?: return@runCatching

            val tokens = getGroupRecipientTokens(groupId, excludeUserId = currentUser.uid)
            if (tokens.isEmpty()) return@runCatching

            val type = if (status.equals("TAKEN", ignoreCase = true)) "dose_taken" else "dose_skipped"
            val tokensJson = tokens.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            val body = """
                {
                  "type": "$type",
                  "tokens": $tokensJson,
                  "data": {
                    "memberName": "${escapeJson(memberName)}",
                    "medicineName": "${escapeJson(medicineName)}",
                    "dosage": "${escapeJson(dosage)}",
                    "status": "$status",
                    "time": "$time",
                    "groupId": "$groupId"
                  }
                }
            """.trimIndent()

            postToWorker(idToken, body)
        }
    }

    suspend fun relayDoseStatusToAdmin(
        groupId: String,
        actorUserId: String,
        actorName: String,
        medicineName: String,
        dosage: String,
        status: String,
        time: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (workerBaseUrl.isBlank()) return@runCatching
            val currentUser = auth.currentUser ?: return@runCatching
            val idToken = currentUser.getIdToken(false).await()?.token ?: return@runCatching

            // 1. Get group members and find admin
            val membersSnapshot = firestore.collection("groups")
                .document(groupId)
                .collection("members")
                .get()
                .await()

            var adminUserId: String? = null
            for (doc in membersSnapshot.documents) {
                val role = doc.getString("role")
                if (role.equals("ADMIN", ignoreCase = true)) {
                    adminUserId = doc.id
                    break
                }
            }

            // If no admin or if the actor IS the admin: skip push!
            if (adminUserId == null || adminUserId == actorUserId) {
                return@runCatching
            }

            // 2. Get tokens for the admin
            val tokensSnapshot = firestore.collection("users")
                .document(adminUserId)
                .collection("fcmTokens")
                .get()
                .await()

            val tokens = mutableListOf<String>()
            for (tokenDoc in tokensSnapshot.documents) {
                val token = tokenDoc.getString("token")
                if (!token.isNullOrBlank()) {
                    tokens.add(token)
                }
            }

            if (tokens.isEmpty()) return@runCatching

            val type = if (status.equals("TAKEN", ignoreCase = true)) "dose_taken" else "dose_skipped"
            val tokensJson = tokens.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            val body = """
                {
                  "type": "$type",
                  "tokens": $tokensJson,
                  "data": {
                    "memberName": "${escapeJson(actorName)}",
                    "medicineName": "${escapeJson(medicineName)}",
                    "dosage": "${escapeJson(dosage)}",
                    "status": "$status",
                    "time": "$time",
                    "groupId": "$groupId"
                  }
                }
            """.trimIndent()

            postToWorker(idToken, body)
        }
    }

    suspend fun relayScheduleSync(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (workerBaseUrl.isBlank()) return@runCatching

            val currentUser = auth.currentUser ?: return@runCatching
            val idToken = currentUser.getIdToken(false).await()?.token ?: return@runCatching

            val tokens = getGroupRecipientTokens(groupId, excludeUserId = currentUser.uid)
            if (tokens.isEmpty()) return@runCatching

            val tokensJson = tokens.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            val body = """
                {
                  "type": "schedule_sync",
                  "tokens": $tokensJson,
                  "data": {
                    "type": "schedule_sync",
                    "groupId": "$groupId"
                  }
                }
            """.trimIndent()

            postToWorker(idToken, body)
        }
    }

    private suspend fun getGroupRecipientTokens(groupId: String, excludeUserId: String): List<String> {
        val tokens = mutableListOf<String>()
        try {
            val membersSnapshot = firestore.collection("groups")
                .document(groupId)
                .collection("members")
                .get()
                .await()

            for (doc in membersSnapshot.documents) {
                val memberId = doc.id
                if (memberId == excludeUserId) continue

                val tokensSnapshot = firestore.collection("users")
                    .document(memberId)
                    .collection("fcmTokens")
                    .get()
                    .await()

                for (tokenDoc in tokensSnapshot.documents) {
                    val token = tokenDoc.getString("token")
                    if (!token.isNullOrBlank()) {
                        tokens.add(token)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return tokens
    }

    private fun postToWorker(idToken: String, jsonBody: String) {
        val url = URL(workerBaseUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }

        try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun escapeJson(text: String): String {
            return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        }

        fun buildDosePayload(
            tokens: List<String>,
            type: String,
            memberName: String,
            medicineName: String,
            dosage: String,
            status: String,
            time: String,
            groupId: String
        ): String {
            val tokensJson = tokens.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            return """
                {
                  "type": "$type",
                  "tokens": $tokensJson,
                  "data": {
                    "memberName": "${escapeJson(memberName)}",
                    "medicineName": "${escapeJson(medicineName)}",
                    "dosage": "${escapeJson(dosage)}",
                    "status": "$status",
                    "time": "$time",
                    "groupId": "$groupId"
                  }
                }
            """.trimIndent()
        }
    }
}
