package com.tulsicare.app.core.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.tulsicare.app.core.alarm.ExactAlarmScheduler
import com.tulsicare.app.core.data.sync.FirestoreListenerManager
import com.tulsicare.app.core.database.TulsiDatabase
import com.tulsicare.app.core.messaging.FcmTokenManager
import com.tulsicare.app.core.model.User
import com.tulsicare.app.core.preferences.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val fcmTokenManager: FcmTokenManager,
    private val database: TulsiDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val firestoreListenerManager: FirestoreListenerManager
) : AuthRepository {

    private val localUser = MutableStateFlow<User?>(null)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                localUser.value = null
            } else {
                val current = localUser.value
                val name = firebaseUser.displayName.orEmpty().ifBlank { current?.displayName.orEmpty() }
                localUser.value = (current ?: User(id = firebaseUser.uid)).copy(
                    id = firebaseUser.uid,
                    displayName = name,
                    email = firebaseUser.email.orEmpty(),
                    phoneNumber = firebaseUser.phoneNumber.orEmpty()
                )
            }
        }
    }

    override val currentUser: Flow<User?> = localUser.asStateFlow()

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            return localUser.value
        }
        return try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            val user = if (doc.exists()) {
                doc.toObject(User::class.java)?.copy(id = firebaseUser.uid)
            } else {
                val newUser = User(
                    id = firebaseUser.uid,
                    displayName = firebaseUser.displayName.orEmpty(),
                    email = firebaseUser.email.orEmpty(),
                    phoneNumber = firebaseUser.phoneNumber.orEmpty()
                )
                firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                newUser
            }
            if (user != null) {
                localUser.value = user
            }
            user
        } catch (e: Exception) {
            User(
                id = firebaseUser.uid,
                displayName = firebaseUser.displayName.orEmpty(),
                email = firebaseUser.email.orEmpty(),
                phoneNumber = firebaseUser.phoneNumber.orEmpty()
            )
        }
    }

    override suspend fun signInAnonymously(): Result<User> = runCatching {
        val result = auth.signInAnonymously().await()
        val firebaseUser = result.user ?: throw IllegalStateException("User is null after anonymous sign in")
        val user = User(
            id = firebaseUser.uid,
            displayName = "Guest User",
            email = "",
            phoneNumber = ""
        )
        firestore.collection("users").document(firebaseUser.uid)
            .set(user, SetOptions.merge())
            .await()
        fcmTokenManager.registerCurrentToken(firebaseUser.uid)
        user
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> = runCatching {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotBlank()) { "Email cannot be empty" }
        require(password.isNotBlank()) { "Password cannot be empty" }
        val firebaseUser = try {
            val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            result.user ?: throw IllegalStateException("User is null after sign in")
        } catch (e: Exception) {
            if (e.message?.contains("network", ignoreCase = true) == true) {
                null
            } else {
                throw e
            }
        }
        val uid = firebaseUser?.uid ?: "user_${trimmedEmail.lowercase().replace("@", "_").replace(".", "_")}"
        val existingDoc = runCatching {
            firestore.collection("users").document(uid).get().await()
        }.getOrNull()
        val existingGroupId = existingDoc?.getString("currentGroupId")
        val user = User(
            id = uid,
            displayName = trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
            email = trimmedEmail,
            phoneNumber = firebaseUser?.phoneNumber.orEmpty(),
            currentGroupId = existingGroupId
        )
        localUser.value = user
        if (existingGroupId != null) {
            userPreferencesRepository.setCurrentGroupId(existingGroupId)
        } else {
            userPreferencesRepository.clearGroupId()
        }
        try {
            fcmTokenManager.registerCurrentToken(uid)
        } catch (_: Exception) {}
        user
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User> = runCatching {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()
        require(trimmedEmail.isNotBlank()) { "Email cannot be empty" }
        require(EMAIL_REGEX.matches(trimmedEmail)) { "Invalid email format" }
        require(password.isNotBlank()) { "Password cannot be empty" }
        require(password.length >= 6) { "Password must be at least 6 characters" }
        require(password.length <= 128) { "Password exceeds maximum length of 128 characters" }
        require(trimmedName.isNotBlank()) { "Display name cannot be empty" }
        require(trimmedName.length <= 100) { "Display name exceeds maximum length of 100 characters" }

        val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("User is null after sign up")
        val user = User(
            id = firebaseUser.uid,
            displayName = trimmedName,
            email = trimmedEmail,
            phoneNumber = ""
        )
        firestore.collection("users").document(firebaseUser.uid).set(user).await()
        fcmTokenManager.registerCurrentToken(firebaseUser.uid)
        user
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> = runCatching {
        require(idToken.isNotBlank()) { "Google ID Token cannot be empty" }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: throw IllegalStateException("Firebase user is null after Google sign in")
        val googleDisplayName = firebaseUser.displayName.orEmpty().ifBlank { "User" }
        val googleEmail = firebaseUser.email.orEmpty()

        val userDocRef = firestore.collection("users").document(firebaseUser.uid)
        val doc = try { userDocRef.get().await() } catch (_: Exception) { null }
        val user = if (doc != null && doc.exists()) {
            val existing = doc.toObject(User::class.java)?.copy(id = firebaseUser.uid) ?: User(
                id = firebaseUser.uid,
                displayName = googleDisplayName,
                email = googleEmail
            )
            if (existing.displayName.isBlank() || existing.email.isBlank()) {
                val updated = existing.copy(
                    displayName = if (existing.displayName.isNotBlank()) existing.displayName else googleDisplayName,
                    email = if (existing.email.isNotBlank()) existing.email else googleEmail
                )
                try { userDocRef.set(updated, SetOptions.merge()).await() } catch (_: Exception) {}
                updated
            } else {
                existing
            }
        } else {
            val newUser = User(
                id = firebaseUser.uid,
                displayName = googleDisplayName,
                email = googleEmail,
                phoneNumber = firebaseUser.phoneNumber.orEmpty()
            )
            try { userDocRef.set(newUser).await() } catch (_: Exception) {}
            newUser
        }

        try {
            fcmTokenManager.registerCurrentToken(firebaseUser.uid)
        } catch (_: Exception) {}
        user
    }

    override suspend fun signInWithGmailDirect(email: String, displayName: String): Result<User> = runCatching {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotBlank()) { "Email cannot be empty" }
        require(EMAIL_REGEX.matches(trimmedEmail)) { "Invalid email format" }
        val trimmedName = displayName.trim().ifBlank { trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }
        val directPassword = "GmailPass_${trimmedEmail.lowercase().hashCode()}#Tulsi"

        val firebaseUser = try {
            val signInResult = auth.signInWithEmailAndPassword(trimmedEmail, directPassword).await()
            signInResult.user ?: throw IllegalStateException("User is null after sign in")
        } catch (e: Exception) {
            try {
                val createResult = auth.createUserWithEmailAndPassword(trimmedEmail, directPassword).await()
                createResult.user ?: throw IllegalStateException("User is null after account creation")
            } catch (netEx: Exception) {
                null
            }
        }

        val uid = firebaseUser?.uid ?: "user_${trimmedEmail.lowercase().replace("@", "_").replace(".", "_")}"
        val existingDoc = runCatching {
            firestore.collection("users").document(uid).get().await()
        }.getOrNull()
        val existingGroupId = existingDoc?.getString("currentGroupId")
        val user = User(
            id = uid,
            displayName = trimmedName,
            email = trimmedEmail,
            phoneNumber = firebaseUser?.phoneNumber.orEmpty(),
            currentGroupId = existingGroupId
        )
        localUser.value = user
        if (existingGroupId != null) {
            userPreferencesRepository.setCurrentGroupId(existingGroupId)
        } else {
            userPreferencesRepository.clearGroupId()
        }
        try {
            firestore.collection("users").document(uid).set(user, SetOptions.merge()).await()
            fcmTokenManager.registerCurrentToken(uid)
        } catch (_: Exception) {}
        user
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotBlank()) { "Email cannot be empty" }
        require(EMAIL_REGEX.matches(trimmedEmail)) { "Invalid email format" }
        auth.sendPasswordResetEmail(trimmedEmail).await()
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("No authenticated user to delete")
        val uid = user.uid

        // 0. Cancel all local alarms
        ExactAlarmScheduler.cancelAllAlarms(context)

        // 1. Delete all registered FCM tokens
        fcmTokenManager.clearAllTokens(uid)

        // 2. Remove user document from Firestore
        firestore.collection("users").document(uid).delete().await()

        // 3. Clear local SQLite/Room database
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }

        // 4. Delete Firebase Auth record
        user.delete().await()
    }

    override suspend fun signOut() {
        localUser.value = null
        val uid = auth.currentUser?.uid

        // 1. FirebaseAuth and CredentialManager run FIRST (guaranteed)
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error signing out of FirebaseAuth", e)
        }

        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error clearing credential state", e)
        }

        // 2. Best-effort cleanup executed in application-level scope so screen changes do not cancel it
        appScope.launch {
            // Detach all listeners
            withTimeoutOrNull(3000L) {
                runCatching {
                    firestoreListenerManager.clearAllListeners()
                }.onFailure { Log.e("AuthRepository", "Failed to detach listeners during signOut", it) }
            }

            // Cancel all local alarms
            withTimeoutOrNull(3000L) {
                runCatching {
                    ExactAlarmScheduler.cancelAllAlarms(context)
                }.onFailure { Log.e("AuthRepository", "Failed to cancel alarms during signOut", it) }
            }

            // Remove FCM token
            if (uid != null) {
                withTimeoutOrNull(3000L) {
                    runCatching {
                        fcmTokenManager.clearToken(uid)
                    }.onFailure { Log.e("AuthRepository", "Failed to clear FCM token during signOut", it) }
                }
            }

            // Clear Room database
            withTimeoutOrNull(3000L) {
                runCatching {
                    database.clearAllTables()
                }.onFailure { Log.e("AuthRepository", "Failed to clear Room during signOut", it) }
            }

            // Clear DataStore preferences
            withTimeoutOrNull(3000L) {
                runCatching {
                    userPreferencesRepository.clear()
                }.onFailure { Log.e("AuthRepository", "Failed to clear DataStore during signOut", it) }
            }
        }
    }

    override suspend fun updateUserProfile(user: User): Result<Unit> = runCatching {
        firestore.collection("users").document(user.id)
            .set(user, SetOptions.merge())
            .await()
    }

    override suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Display name cannot be empty" }
        val firebaseUser = auth.currentUser ?: throw IllegalStateException("Not authenticated")

        // 1. Update FirebaseAuth profile
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(cleanName)
            .build()
        firebaseUser.updateProfile(profileUpdates).await()

        // 2. Update Firestore user doc
        val current = localUser.value ?: getCurrentUser() ?: User(id = firebaseUser.uid)
        val updatedUser = current.copy(displayName = cleanName)
        firestore.collection("users").document(firebaseUser.uid)
            .set(mapOf("displayName" to cleanName), SetOptions.merge())
            .await()
        localUser.value = updatedUser

        // 3. Update group member doc in Firestore & Room if in a group
        val groupId = updatedUser.currentGroupId
        if (!groupId.isNullOrBlank() && groupId != "default_group" && groupId != "my_family") {
            runCatching {
                firestore.collection("groups").document(groupId)
                    .collection("members").document(firebaseUser.uid)
                    .update("displayName", cleanName)
                    .await()
            }
            database.familyGroupDao().updateMemberDisplayName(firebaseUser.uid, groupId, cleanName)
        }
    }

    override suspend fun updateCurrentGroupId(groupId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val cleanGroupId = groupId.trim().ifBlank { null }
        localUser.value = localUser.value?.copy(currentGroupId = cleanGroupId)
        firestore.collection("users").document(uid)
            .set(mapOf("currentGroupId" to cleanGroupId), SetOptions.merge())
            .await()
        if (cleanGroupId != null) {
            userPreferencesRepository.setCurrentGroupId(cleanGroupId)
        } else {
            userPreferencesRepository.clearGroupId()
        }
    }

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    }
}
