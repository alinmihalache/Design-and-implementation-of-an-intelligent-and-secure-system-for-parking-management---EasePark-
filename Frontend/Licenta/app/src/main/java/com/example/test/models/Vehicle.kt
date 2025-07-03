package com.example.test.models

import com.google.gson.annotations.SerializedName

data class Vehicle(
    val id: Long,
    @SerializedName("plate_number") val plateNumber: String,
    val make: String,
    val model: String,
    val year: Int,
    @SerializedName("user_id") val userId: Long? = null,
    val type: VehicleType,
    @SerializedName("created_at") val createdAt: String? = null
)

enum class VehicleType {
    CAR,
    MOTORCYCLE,
    VAN;

    override fun toString(): String = name.lowercase()
} 