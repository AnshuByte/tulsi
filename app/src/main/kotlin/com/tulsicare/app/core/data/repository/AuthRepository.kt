package com.tulsicare.app.core.data.repository

import com.tulsicare.app.core.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun getCurrentUser(): User?
    suspend fun signInAnonymously(): Result<User>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun signOut()
    suspend fun updateUserProfile(user: User): Result<Unit>
    suspend fun updateCurrentGroupId(groupId: String): Result<Unit>
}
