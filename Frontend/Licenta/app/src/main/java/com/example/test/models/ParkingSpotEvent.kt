package com.example.test.models

import com.google.gson.annotations.SerializedName

data class ParkingSpotEvent(
    @SerializedName("type")
    val type: String,
    @SerializedName("spotId")
    val spotId: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("timestamp")
    val timestamp: String? = null
)

data class ParkingSpotUpdateEvent(
    @SerializedName("type")
    val type: String = "parking_spot_updated",
    @SerializedName("spots")
    val spots: List<ParkingSpot>
)

enum class ParkingSpotEventType {
    PARKING_SPOT_UPDATED,
    PARKING_SPOT_STATUS_CHANGED,
    RESERVATION_CREATED,
    RESERVATION_CANCELLED,
    RESERVATION_EXPIRED
} 