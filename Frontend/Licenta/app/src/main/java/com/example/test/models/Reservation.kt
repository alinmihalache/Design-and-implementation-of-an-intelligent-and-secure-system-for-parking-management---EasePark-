package com.example.test.models

import com.google.gson.annotations.SerializedName
import com.example.test.utils.ParkingSpotUtils
import java.util.Date

enum class ReservationStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    override fun toString(): String = name.lowercase()

    companion object {
        fun fromString(status: String): ReservationStatus {
            return when (status.lowercase()) {
                "pending" -> PENDING
                "active" -> ACTIVE
                "completed" -> COMPLETED
                "cancelled" -> CANCELLED
                else -> COMPLETED
            }
        }
    }
}

data class Reservation(
    val id: Long,
    @SerializedName("user_id")
    val userId: Long,
    @SerializedName("vehicle_id")
    val vehicleId: Long,
    @SerializedName("parking_spot_id")
    val parkingSpotId: Long,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    val status: String,
    @SerializedName("total_price")
    val totalPrice: String,
    @SerializedName("price_per_hour")
    val pricePerHour: String,
    val address: String,
    @SerializedName("plate_number")
    val plateNumber: String,
    @SerializedName("created_at")
    val createdAt: String,
    val vehicle: Vehicle? = null,
    val payment: Payment? = null
) {
    /**
     * Returns the reservation status based solely on the state provided by the server.
     * This is the single source of truth for the reservation's state.
     */
    fun getDisplayStatus(): ReservationStatus {
        // The server, via the scheduler, is the single source of truth.
        // The client should not perform its own time-based logic.
        return ReservationStatus.fromString(status)
    }
} 