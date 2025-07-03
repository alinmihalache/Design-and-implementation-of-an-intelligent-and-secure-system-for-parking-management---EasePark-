package com.example.test.models

enum class ParkingSpotStatus {
    AVAILABLE,
    OCCUPIED,
    RESERVED,
    MAINTENANCE;

    companion object {
        fun fromString(status: String?): ParkingSpotStatus {
            return when (status?.lowercase()) {
                "available" -> AVAILABLE
                "occupied" -> OCCUPIED
                "reserved" -> RESERVED
                "maintenance" -> MAINTENANCE
                else -> AVAILABLE // Default to AVAILABLE for any unknown status to be safe
            }
        }
    }
} 