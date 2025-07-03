package com.example.test.models

import com.google.gson.annotations.SerializedName

data class User(
    val id: Long,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    val email: String,
    val phone: String,
    val role: String,
    @SerializedName("is_2fa_enabled") val is2faEnabled: Boolean = false,
    val createdAt: String,
    val updatedAt: String
) {
    val fullName: String
        get() = listOfNotNull(firstName, lastName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "(fără nume)" }
}

enum class UserRole {
    USER,
    ADMIN;

    override fun toString(): String = name.lowercase()
} 