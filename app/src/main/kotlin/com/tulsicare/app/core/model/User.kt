package com.tulsicare.app.core.model

data class User(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val currentGroupId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
