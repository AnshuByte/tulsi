package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.database.TulsiDatabase
import com.tulsicare.app.core.messaging.FcmTokenManager
import com.tulsicare.app.core.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val fcmTokenManager: FcmTokenManager,
    private val database: TulsiDatabase
) : AuthRepository {

    private val localUser = MutableStateFlow<User?>(null)

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(localUser.value)
            } else {
                val basicUser = User(
                    id = firebaseUser.uid,
                    displayName = firebaseUser.displayName.orEmpty(),
                    email = firebaseUser.email.orEmpty(),
                    phoneNumber = firebaseUser.phoneNumber.orEmpty()
                )
                trySend(basicUser)
            }
        }
        auth.addAuthStateListener(listener)
        val job = CoroutineScope(Dispatchers.Default).launch {
            localUser.collect { user ->
                if (auth.currentUser == null) {
                    trySend(user)
                }
            }
        }
        awaitClose {
            auth.removeAuthStateListener(listener)
            job.cancel()
        }
    }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            return localUser.value
        }
        return try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (doc.exists()) {
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
        val user = User(
            id = uid,
            displayName = trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
            email = trimmedEmail,
            phoneNumber = firebaseUser?.phoneNumber.orEmpty(),
            currentGroupId = "my_family"
        )
        localUser.value = user
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
        val user = User(
            id = uid,
            displayName = trimmedName,
            email = trimmedEmail,
            phoneNumber = firebaseUser?.phoneNumber.orEmpty(),
            currentGroupId = "my_family"
        )
        localUser.value = user
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
        if (uid != null) {
            fcmTokenManager.clearToken(uid)
        }
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        auth.signOut()
    }

    override suspend fun updateUserProfile(user: User): Result<Unit> = runCatching {
        firestore.collection("users").document(user.id)
            .set(user, SetOptions.merge())
            .await()
    }

    override suspend fun updateCurrentGroupId(groupId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        firestore.collection("users").document(uid)
            .update("currentGroupId", groupId)
            .await()
    }

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    }
}
