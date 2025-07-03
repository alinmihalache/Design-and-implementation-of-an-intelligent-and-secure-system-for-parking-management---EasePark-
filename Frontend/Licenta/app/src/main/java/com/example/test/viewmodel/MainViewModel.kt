package com.example.test.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.test.models.ParkingSpot
import com.example.test.network.ParkingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.test.models.OperationStatus
import com.example.test.models.Vehicle
import com.example.test.network.ReservationRequest
import java.text.SimpleDateFormat
import java.util.*
import com.example.test.auth.AuthManager

class MainViewModel : ViewModel() {
    private val parkingService = ParkingService.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val _parkingSpots = MutableStateFlow<List<ParkingSpot>>(emptyList())
    val parkingSpots: StateFlow<List<ParkingSpot>> = _parkingSpots

    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _reservationStatus = MutableStateFlow<OperationStatus>(OperationStatus.Initial)
    val reservationStatus: StateFlow<OperationStatus> = _reservationStatus

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadParkingSpots()
        val userId = AuthManager.getInstance().currentUser.value?.id
        if (userId != null) {
            loadUserVehicles(userId)
        }
    }

    private fun loadUserVehicles(userId: Long) {
        viewModelScope.launch {
            try {
                val response = parkingService.getVehicles(userId)
                if (response.isSuccessful) {
                    _userVehicles.value = response.body() ?: emptyList()
                } else {
                    _error.value = "Error loading vehicles: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error loading vehicles"
            }
        }
    }

    fun searchParkingSpots(query: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val response = parkingService.getParkingSpots()
                if (response.isSuccessful) {
                    val parkingSpotsResponse = response.body()
                    if (parkingSpotsResponse != null) {
                        _parkingSpots.value = parkingSpotsResponse.spots.filter { spot ->
                            spot.address.contains(query, ignoreCase = true)
                        }
                    } else {
                        _parkingSpots.value = emptyList()
                    }
                } else {
                    _error.value = "Error searching parking spots: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error searching parking spots"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadParkingSpots() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val response = parkingService.getParkingSpots()
                if (response.isSuccessful) {
                    val parkingSpotsResponse = response.body()
                    _parkingSpots.value = parkingSpotsResponse?.spots ?: emptyList()
                } else {
                    _error.value = "Error loading parking spots: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error loading parking spots"
            } finally {
                _loading.value = false
            }
        }
    }

    fun makeReservation(
        spotId: Long,
        startTime: String,
        endTime: String
    ) {
        viewModelScope.launch {
            _reservationStatus.value = OperationStatus.Loading
            try {
                val selectedVehicle = _userVehicles.value.firstOrNull()
                if (selectedVehicle == null) {
                    throw Exception("No vehicle selected")
                }
                val userId = AuthManager.getInstance().currentUser.value?.id
                if (userId == null) {
                    throw Exception("User not authenticated")
                }
                val startDate = try { dateFormat.parse(startTime) } catch (e: Exception) { throw Exception("Invalid start time format") }
                val endDate = try { dateFormat.parse(endTime) } catch (e: Exception) { throw Exception("Invalid end time format") }
                if (startDate.after(endDate)) { throw Exception("Start time must be before end time") }
                val response = parkingService.createReservation(
                    ReservationRequest(
                        userId = userId,
                        vehicleId = selectedVehicle.id,
                        parkingSpotId = spotId,
                        startTime = startTime,
                        endTime = endTime
                    )
                )
                if (response.isSuccessful) {
                    _reservationStatus.value = OperationStatus.Success("Reservation created successfully")
                    loadParkingSpots()
                } else {
                    _reservationStatus.value = OperationStatus.Error("Failed to create reservation: ${response.code()}")
                }
            } catch (e: Exception) {
                _reservationStatus.value = OperationStatus.Error(e.message ?: "Error making reservation")
            }
        }
    }
}

class MainViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 