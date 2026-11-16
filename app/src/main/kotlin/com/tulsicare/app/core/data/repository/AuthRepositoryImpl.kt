package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.messaging.FcmTokenManager
import com.tulsicare.app.core.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val fcmTokenManager: FcmTokenManager
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                // Fetch profile or provide basic user info immediately
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
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
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
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("User is null after sign in")
        val user = getCurrentUser() ?: User(id = firebaseUser.uid, email = email)
        fcmTokenManager.registerCurrentToken(firebaseUser.uid)
        user
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("User is null after sign up")
        val user = User(
            id = firebaseUser.uid,
            displayName = displayName,
            email = email,
            phoneNumber = ""
        )
        firestore.collection("users").document(firebaseUser.uid).set(user).await()
        fcmTokenManager.registerCurrentToken(firebaseUser.uid)
        user
    }

    override suspend fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            fcmTokenManager.clearToken(uid)
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
}
