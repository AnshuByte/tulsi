package com.tulsicare.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

    private fun validateSignUp(email: String, password: String, displayName: String): Result<Unit> {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()
        if (trimmedEmail.isBlank()) return Result.failure(IllegalArgumentException("Email cannot be empty"))
        if (!emailRegex.matches(trimmedEmail)) return Result.failure(IllegalArgumentException("Invalid email format"))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Password cannot be empty"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        if (password.length > 128) return Result.failure(IllegalArgumentException("Password exceeds maximum length of 128 characters"))
        if (trimmedName.isBlank()) return Result.failure(IllegalArgumentException("Display name cannot be empty"))
        if (trimmedName.length > 100) return Result.failure(IllegalArgumentException("Display name exceeds maximum length of 100 characters"))
        return Result.success(Unit)
    }

    private fun validatePasswordReset(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) return Result.failure(IllegalArgumentException("Email cannot be empty"))
        if (!emailRegex.matches(trimmedEmail)) return Result.failure(IllegalArgumentException("Invalid email format"))
        return Result.success(Unit)
    }

    @Test
    fun testValidRegistration() {
        val res = validateSignUp("test.admin@example.com", "SecurePass123!", "Asha")
        assertTrue(res.isSuccess)
    }

    @Test
    fun testEmptyEmailFails() {
        val res = validateSignUp("", "SecurePass123!", "Asha")
        assertTrue(res.isFailure)
        assertEquals("Email cannot be empty", res.exceptionOrNull()?.message)
    }

    @Test
    fun testWhitespaceEmailFails() {
        val res = validateSignUp("   ", "SecurePass123!", "Asha")
        assertTrue(res.isFailure)
        assertEquals("Email cannot be empty", res.exceptionOrNull()?.message)
    }

    @Test
    fun testInvalidEmailFormats() {
        val invalidEmails = listOf(
            "notanemail",
            "missing@domain",
            "@missinguser.com",
            "spaces in@email.com",
            "double@@email.com"
        )
        for (email in invalidEmails) {
            val res = validateSignUp(email, "SecurePass123!", "Asha")
            assertTrue("Expected failure for email: $email", res.isFailure)
            assertEquals("Invalid email format", res.exceptionOrNull()?.message)
        }
    }

    @Test
    fun testWeakPasswordFails() {
        val res = validateSignUp("test.admin@example.com", "12345", "Asha")
        assertTrue(res.isFailure)
        assertEquals("Password must be at least 6 characters", res.exceptionOrNull()?.message)
    }

    @Test
    fun testEmptyPasswordFails() {
        val res = validateSignUp("test.admin@example.com", "", "Asha")
        assertTrue(res.isFailure)
        assertEquals("Password cannot be empty", res.exceptionOrNull()?.message)
    }

    @Test
    fun testExcessivelyLongPasswordFails() {
        val longPassword = "A".repeat(129)
        val res = validateSignUp("test.admin@example.com", longPassword, "Asha")
        assertTrue(res.isFailure)
        assertEquals("Password exceeds maximum length of 128 characters", res.exceptionOrNull()?.message)
    }

    @Test
    fun testEmptyDisplayNameFails() {
        val res = validateSignUp("test.admin@example.com", "SecurePass123!", "   ")
        assertTrue(res.isFailure)
        assertEquals("Display name cannot be empty", res.exceptionOrNull()?.message)
    }

    @Test
    fun testExcessivelyLongDisplayNameFails() {
        val longName = "A".repeat(101)
        val res = validateSignUp("test.admin@example.com", "SecurePass123!", longName)
        assertTrue(res.isFailure)
        assertEquals("Display name exceeds maximum length of 100 characters", res.exceptionOrNull()?.message)
    }

    @Test
    fun testPasswordResetValidEmail() {
        val res = validatePasswordReset("test.admin@example.com")
        assertTrue(res.isSuccess)
    }

    @Test
    fun testPasswordResetInvalidEmailFails() {
        val res = validatePasswordReset("invalid-email")
        assertTrue(res.isFailure)
        assertEquals("Invalid email format", res.exceptionOrNull()?.message)
    }

    @Test
    fun testPasswordResetEmptyEmailFails() {
        val res = validatePasswordReset("")
        assertTrue(res.isFailure)
        assertEquals("Email cannot be empty", res.exceptionOrNull()?.message)
    }
}
