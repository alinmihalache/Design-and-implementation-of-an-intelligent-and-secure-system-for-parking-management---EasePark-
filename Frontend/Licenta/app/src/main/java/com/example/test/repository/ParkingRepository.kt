package com.example.test.repository

import android.util.Log
import com.example.test.auth.AuthManager
import com.example.test.models.*
import com.example.test.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date

class ParkingRepository {

    private val service: ParkingService = ParkingService.getInstance()
    private var sseClient: ParkingSpotSSEClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _parkingSpots = MutableStateFlow<List<ParkingSpot>>(emptyList())
    private val _userReservations = MutableStateFlow<List<Reservation>>(emptyList())

    // Store the last known location to allow SSE-triggered refreshes.
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    init {
        startSSE()
    }

    private fun startSSE() {
        sseClient?.disconnect()
        sseClient = ParkingSpotSSEClient(
            baseUrl = RetrofitClient.SSE_BASE_URL,
            tokenProvider = { RetrofitClient.getStoredToken() }
        ).apply {
            addUpdateListener { event ->
                Log.d("ParkingRepository", "SSE: Full update with ${event.spots.size} spots.")
                _parkingSpots.value = event.spots
            }
            addStatusListener { event ->
                Log.d("ParkingRepository", "SSE: Status update for spot ${event.spotId}. Triggering full refresh.")
                coroutineScope.launch {
                    loadParkingSpotsIfLocationKnown()
                }
            }
            addReservationListener { event ->
                Log.d("ParkingRepository", "SSE: Reservation update: ${event.type}. Triggering full refresh.")
                val userId = AuthManager.getInstance().currentUser.value?.id
                if (userId != null && event.userId == userId) {
                    coroutineScope.launch {
                        loadUserReservations(userId)
                        loadParkingSpotsIfLocationKnown() // Also refresh spots for consistency
                    }
                }
            }
            connect()
        }
    }

    fun initializeUserSession(userId: Long) {
        coroutineScope.launch {
            loadUserReservations(userId)
        }
    }

    fun getParkingSpotsFlow(): StateFlow<List<ParkingSpot>> = _parkingSpots.asStateFlow()
    fun getUserReservationsFlow(): StateFlow<List<Reservation>> = _userReservations.asStateFlow()

    suspend fun loadParkingSpots(latitude: Double, longitude: Double, radius: Double = 1000.0) {
        // Store the latest location so we can use it for SSE-triggered refreshes.
        this.lastLatitude = latitude
        this.lastLongitude = longitude
        
        try {
            val response = service.getParkingSpots(latitude, longitude, radius)
            if (response.isSuccessful) {
                _parkingSpots.value = response.body()?.spots ?: emptyList()
            } else {
                Log.e("ParkingRepository", "Failed to fetch parking spots: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("ParkingRepository", "Exception fetching parking spots", e)
        }
    }

    suspend fun loadUserReservations(userId: Long) {
        try {
            val response = service.getReservations(userId)
            if (response.isSuccessful) {
                val reservations = response.body() ?: emptyList()
                Log.d("ParkingRepository", "Fetched ${reservations.size} reservations")
                reservations.forEach { reservation ->
                    Log.d("ParkingRepository", "Reservation ${reservation.id}: totalPrice='${reservation.totalPrice}', pricePerHour='${reservation.pricePerHour}'")
                }
                _userReservations.value = reservations
            } else {
                Log.e("ParkingRepository", "Failed to fetch user reservations: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("ParkingRepository", "Exception fetching user reservations", e)
        }
    }

    private suspend fun loadParkingSpotsIfLocationKnown() {
        if (lastLatitude != null && lastLongitude != null) {
            loadParkingSpots(lastLatitude!!, lastLongitude!!)
        } else {
            Log.w("ParkingRepository", "SSE event received but location is unknown. Cannot refresh spots.")
        }
    }

    fun disconnect() {
        sseClient?.disconnect()
    }

    // User Management
    fun getCurrentUser(): Flow<User> = flow {
        val response = service.getCurrentUser()
        if (response.isSuccessful) {
            emit(response.body() ?: throw Exception("User profile not found"))
        } else {
            throw Exception("Failed to get user profile: ${response.code()}")
        }
    }

    suspend fun updateUserProfile(
        userId: Long, email: String?, firstName: String?, lastName: String?, phone: String?
    ): User = withContext(Dispatchers.IO) {
        val request = UpdateProfileRequest(email, firstName, lastName, phone)
        val response = service.updateProfile(userId, request)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Failed to update user profile")
        } else {
            throw Exception("Failed to update user profile: ${response.code()}")
        }
    }

    // Vehicle Management
    fun getUserVehicles(userId: Long): Flow<List<Vehicle>> = flow {
        val response = service.getVehicles(userId)
        if (response.isSuccessful) {
            emit(response.body() ?: emptyList())
        } else {
            throw Exception("Failed to get user vehicles: ${response.code()}")
        }
    }

    suspend fun addVehicle(
        userId: Long,
        plateNumber: String,
        make: String,
        model: String,
        year: Int,
        type: VehicleType
    ): Vehicle {
        val request = VehicleRequest(plateNumber, make, model, year, type.name.lowercase())
        val response = service.addVehicle(userId, request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Failed to add vehicle: Empty response")
        } else {
            throw Exception("Failed to add vehicle: ${response.code()}")
        }
    }

    suspend fun deleteVehicle(userId: Long, vehicleId: Long) = withContext(Dispatchers.IO) {
        val response = service.deleteVehicle(userId, vehicleId)
        if (!response.isSuccessful) {
            throw Exception("Failed to delete vehicle: ${response.code()}")
        }
    }

    // Reservations
    suspend fun createReservation(
        spotId: Long, userId: Long, vehicleId: Long, startTime: String, endTime: String
    ): Reservation = withContext(Dispatchers.IO) {
        val request = ReservationRequest(
            userId = userId,
            vehicleId = vehicleId,
            parkingSpotId = spotId,
            startTime = startTime,
            endTime = endTime
        )
        val response = service.createReservation(request)
        if (response.isSuccessful) {
            val newReservation = response.body() ?: throw Exception("Failed to create reservation")
            loadUserReservations(userId)
            newReservation
        } else {
            throw Exception("Failed to create reservation: ${response.errorBody()?.string()}")
        }
    }

    suspend fun cancelReservation(userId: Long, reservationId: Long) {
        withContext(Dispatchers.IO) {
            val response = service.cancelReservation(userId, reservationId)
            if (response.isSuccessful) {
                loadUserReservations(userId)
            } else {
                throw Exception("Failed to cancel reservation: ${response.code()}")
            }
        }
    }

    suspend fun extendReservation(userId: Long, reservationId: Long, newEndTime: Date): Reservation = withContext(Dispatchers.IO) {
        val request = ReservationUpdateRequest(endTime = newEndTime)
        val response = service.updateReservation(userId, reservationId, request)
        if (response.isSuccessful) {
            val updatedReservation = response.body() ?: throw Exception("Failed to extend reservation")
            
            Log.d("ParkingRepository", "Extended reservation ${updatedReservation.id}: totalPrice='${updatedReservation.totalPrice}', pricePerHour='${updatedReservation.pricePerHour}'")

            // Manually update the local state to avoid race conditions and extra network calls
            val currentReservations = _userReservations.value.toMutableList()
            val index = currentReservations.indexOfFirst { it.id == updatedReservation.id }
            if (index != -1) {
                currentReservations[index] = updatedReservation
                _userReservations.value = currentReservations
            } else {
                // If not found, fall back to fetching the whole list
                loadUserReservations(userId)
            }
            // Also refresh parking spots as a reservation change might affect a spot's status
            loadParkingSpotsIfLocationKnown()

            updatedReservation
        } else {
            throw Exception("Failed to extend reservation: ${response.code()}")
        }
    }
} 