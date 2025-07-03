package com.example.test.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.test.R
import com.example.test.models.ParkingSpotStatus

/**
 * Utility object for resolving colors from resource IDs in a context-safe manner.
 */
object ColorUtils {

    /**
     * Returns the appropriate color resource ID for a given parking spot status.
     * This function acts as the single source of truth for status-color mapping.
     *
     * @param context The context used to resolve the color.
     * @param status The status of the parking spot.
     * @return The integer value of the resolved color.
     */
    fun getStatusColor(context: Context, status: ParkingSpotStatus): Int {
        val colorRes = when (status) {
            ParkingSpotStatus.AVAILABLE -> R.color.status_available
            ParkingSpotStatus.OCCUPIED -> R.color.status_occupied
            ParkingSpotStatus.RESERVED -> R.color.status_reserved
            ParkingSpotStatus.MAINTENANCE -> R.color.status_maintenance
        }
        return ContextCompat.getColor(context, colorRes)
    }
} 