package com.tulsicare.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentUser: User? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
                if (user == null) {
                    resetForm()
                }
            }
        }
    }

    fun setEmail(email: String) = _uiState.update { it.copy(email = email, errorMessage = null) }
    fun setPassword(password: String) = _uiState.update { it.copy(password = password, errorMessage = null) }
    fun setDisplayName(name: String) = _uiState.update { it.copy(displayName = name, errorMessage = null) }
    fun toggleAuthMode() = _uiState.update { it.copy(isSignUp = !it.isSignUp, errorMessage = null) }
    fun setErrorMessage(message: String?) = _uiState.update { it.copy(errorMessage = message) }

    fun resetForm() {
        _uiState.update {
            it.copy(
                email = "",
                password = "",
                displayName = "",
                isSignUp = false,
                errorMessage = null,
                isLoading = false
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (state.isSignUp) {
                authRepository.signUpWithEmail(
                    email = state.email.trim(),
                    password = state.password,
                    displayName = state.displayName.trim().ifBlank { "User" }
                )
            } else {
                authRepository.signInWithEmail(
                    email = state.email.trim(),
                    password = state.password
                )
            }

            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            email = "",
                            password = "",
                            displayName = "",
                            isSignUp = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Authentication failed"
                        )
                    }
                }
            )
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            email = "",
                            password = "",
                            displayName = "",
                            isSignUp = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Google sign in failed"
                        )
                    }
                }
            )
        }
    }

    fun quickSignIn(role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (email, password, name) = when (role) {
                "ADMIN" -> Triple("test.asha@example.com", "TestPass123!", "Asha")
                "PATIENT" -> Triple("test.ravi@example.com", "TestPass123!", "Ravi")
                else -> Triple("test.meera@example.com", "TestPass123!", "Meera")
            }

            val signInResult = authRepository.signInWithEmail(email, password)
            if (signInResult.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUser = signInResult.getOrNull(),
                        email = "",
                        password = "",
                        displayName = "",
                        isSignUp = false,
                        errorMessage = null
                    )
                }
            } else {
                val signUpResult = authRepository.signUpWithEmail(email, password, name)
                signUpResult.fold(
                    onSuccess = { user ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentUser = user,
                                email = "",
                                password = "",
                                displayName = "",
                                isSignUp = false,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Quick sign-in failed"
                            )
                        }
                    }
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState()
        }
    }
}
