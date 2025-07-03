package com.example.test.models

import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import com.example.test.utils.ParkingSpotUtils

// Model pentru status-ul de la backend
data class ParkingSpotStatusResponse(
    @SerializedName("current")
    val current: String,
    @SerializedName("occupiedSince")
    val occupiedSince: String?,
    @SerializedName("occupiedUntil")
    val occupiedUntil: String?
)

data class OccupiedInterval(
    @SerializedName("start") val start: String,
    @SerializedName("end") val end: String,
    @SerializedName("status") val status: String
)

data class ParkingSpot(
    val id: Long,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("address")
    val address: String,
    @SerializedName("isOccupied")
    val isOccupied: Boolean = false,
    @SerializedName("status")
    val statusResponse: ParkingSpotStatusResponse? = null,
    val pricePerHour: Double,
    @SerializedName("type")
    val type: ParkingSpotType,
    @SerializedName("distance")
    val distance: Double? = null,
    @SerializedName("currentReservation")
    val currentReservation: Reservation? = null,
    @SerializedName("occupiedIntervals")
    val occupiedIntervals: List<OccupiedInterval>? = null
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
    
    // Helper method to determine if spot is available for reservation
    fun isAvailableForReservation(): Boolean {
        return getDisplayStatus() == ParkingSpotStatus.AVAILABLE
    }
    
    /**
     * Determines the status to be displayed in the UI.
     * This acts as a translator, prioritizing reservation status over physical sensor status.
     *
     * The hierarchy of truth is:
     * 1. Physical Sensor Status (Occupied, Maintenance) - This is the absolute truth.
     * 2. Reservation Status (Pending, Active) - If not physically occupied, this dictates the state.
     * 3. Default to Available.
     */
    fun getDisplayStatus(): ParkingSpotStatus {
        val now = java.util.Date()
        // The buffer in minutes before a reservation starts, during which the spot is considered 'RESERVED'.
        val preReservationBufferMinutes = 15

        // Sort intervals by start time to process the soonest one first.
        val sortedIntervals = occupiedIntervals?.sortedBy { it.start } ?: emptyList()

        for (interval in sortedIntervals) {
            val startTime = ParkingSpotUtils.parseISO8601ToDate(interval.start) ?: continue
            val endTime = ParkingSpotUtils.parseISO8601ToDate(interval.end) ?: continue

            // 1. Check if the spot is currently ACTIVE within a reservation period.
            if (now.after(startTime) && now.before(endTime)) {
                return if (interval.status.equals("active", ignoreCase = true)) {
                    ParkingSpotStatus.OCCUPIED
                } else {
                    ParkingSpotStatus.RESERVED
                }
            }

            // 2. Check if the spot is in the pre-reservation buffer zone (e.g., 15 minutes before start).
            val bufferStartTime = java.util.Calendar.getInstance().apply {
                time = startTime
                add(java.util.Calendar.MINUTE, -preReservationBufferMinutes)
            }.time

            if (now.after(bufferStartTime) && now.before(startTime)) {
                // If it's about to be reserved, mark it as such to prevent last-minute bookings.
                return ParkingSpotStatus.RESERVED
            }
        }

        // 3. If no current or upcoming reservation affects the spot, it's available.
        return ParkingSpotStatus.AVAILABLE
    }

    // Returnează intervalul activ curent, dacă există
    fun getCurrentActiveInterval(): OccupiedInterval? {
        val now = java.util.Date()
        return occupiedIntervals?.firstOrNull { interval ->
            val start = ParkingSpotUtils.parseISO8601ToDate(interval.start)
            val end = ParkingSpotUtils.parseISO8601ToDate(interval.end)
            start != null && end != null && now >= start && now < end && interval.status == "active"
        }
    }

    private fun isReservationExpired(reservation: Reservation): Boolean {
        val endTime = ParkingSpotUtils.parseISO8601ToDate(reservation.endTime)
        val currentTime = java.util.Date()
        return endTime?.before(currentTime) ?: true
    }
}

data class ParkingSpotsResponse(
    @SerializedName("spots")
    val spots: List<ParkingSpot>,
    @SerializedName("meta")
    val meta: ParkingSpotsMeta
)

data class ParkingSpotsMeta(
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("total")
    val total: Int
)

enum class ParkingSpotType {
    @SerializedName("standard")
    STANDARD,
    @SerializedName("handicap")
    HANDICAP,
    @SerializedName("electric")
    ELECTRIC
} 