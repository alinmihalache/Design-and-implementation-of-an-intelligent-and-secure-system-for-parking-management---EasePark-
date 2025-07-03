package com.example.test.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.models.*
import com.example.test.repository.ParkingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import com.example.test.auth.AuthManager

class ParkingViewModel(private val repository: ParkingRepository) : ViewModel() {

    private val authManager = AuthManager.getInstance()

    // Raw data flows from the repository
    private val parkingSpotsFromRepo: StateFlow<List<ParkingSpot>> = repository.getParkingSpotsFlow()
    private val userReservationsFromRepo: StateFlow<List<Reservation>> = repository.getUserReservationsFlow()

    // The single source of truth for the map UI.
    // This flow combines spots and reservations to create a unified, consistent state.
    val unifiedParkingSpots: StateFlow<List<ParkingSpot>> =
        parkingSpotsFromRepo.combine(userReservationsFromRepo) { spots, reservations ->
            // Create a map of spotId to its most relevant reservation (active or pending)
            val reservationMap = reservations
                .filter { it.status == "active" || it.status == "pending" }
                .associateBy { it.parkingSpotId }

            // Create a new list of spots, "hydrating" them with their current reservation.
            spots.map { spot ->
                spot.copy(currentReservation = reservationMap[spot.id])
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val userReservations: StateFlow<List<Reservation>> = userReservationsFromRepo

    private val _selectedParkingSpot = MutableStateFlow<ParkingSpot?>(null)
    val selectedParkingSpot: StateFlow<ParkingSpot?> = _selectedParkingSpot.asStateFlow()

    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles.asStateFlow()

    private val _reservationStatus = MutableStateFlow<OperationStatus>(OperationStatus.Initial)
    val reservationStatus: StateFlow<OperationStatus> = _reservationStatus.asStateFlow()
    
    // This now simply combines the repository's flow with the selected spot.
    val combinedSpotsAndSelected: StateFlow<Pair<List<ParkingSpot>, ParkingSpot?>> =
        unifiedParkingSpots.combine(selectedParkingSpot) { spots, selected ->
            Pair(spots, selected)
        }.stateIn(viewModelScope, SharingStarted.Lazily, Pair(emptyList(), null))


    init {
        loadUserVehicles()
        // The repository handles initialization of spots and reservations
        authManager.currentUser.value?.id?.let {
            repository.initializeUserSession(it)
        }
    }

    private fun loadUserVehicles() {
        viewModelScope.launch {
            try {
                authManager.currentUser.value?.id?.let { userId ->
                    repository.getUserVehicles(userId).collect { vehicles ->
                        _userVehicles.value = vehicles
                    }
                }
            } catch (e: Exception) {
                // Handle error
                Log.e("ParkingViewModel", "Error loading vehicles", e)
            }
        }
    }
    
    fun selectParkingSpot(spot: ParkingSpot?) {
        _selectedParkingSpot.value = spot
    }

    fun createReservation(vehicleId: Long, parkingSpotId: Long, startTime: String, endTime: String) {
        viewModelScope.launch {
            _reservationStatus.value = OperationStatus.Loading
            try {
                val userId = authManager.currentUser.value?.id
                if (userId == null) {
                    _reservationStatus.value = OperationStatus.Error("User not logged in")
                    return@launch
                }
                
                repository.createReservation(parkingSpotId, userId, vehicleId, startTime, endTime)
                _reservationStatus.value = OperationStatus.Success("Rezervare creată cu succes!")
                
            } catch (e: Exception) {
                _reservationStatus.value = OperationStatus.Error(e.message ?: "A apărut o eroare la crearea rezervării")
                Log.e("ParkingViewModel", "Failed to create reservation", e)
            }
        }
    }

    fun cancelReservation(reservationId: Long) {
        viewModelScope.launch {
            _reservationStatus.value = OperationStatus.Loading
            try {
                val userId = authManager.currentUser.value?.id
                if (userId == null) {
                    _reservationStatus.value = OperationStatus.Error("User not logged in")
                    return@launch
                }
                repository.cancelReservation(userId, reservationId)
                _reservationStatus.value = OperationStatus.Success("Rezervarea a fost anulată cu succes!")
            } catch (e: Exception) {
                _reservationStatus.value = OperationStatus.Error(e.message ?: "A apărut o eroare la anularea rezervării")
            }
        }
    }

    fun loadParkingSpots(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                repository.loadParkingSpots(latitude, longitude)
            } catch (e: Exception) {
                Log.e("ParkingViewModel", "Failed to load parking spots", e)
                // Optionally, you can expose an error state to the UI here.
            }
        }
    }
    
    fun resetReservationStatus() {
        _reservationStatus.value = OperationStatus.Initial
    }
    
    override fun onCleared() {
        super.onCleared()
        // Disconnect SSE client when ViewModel is destroyed
        // repository.disconnect() // Repository lives longer than ViewModel, so maybe not here.
    }
}