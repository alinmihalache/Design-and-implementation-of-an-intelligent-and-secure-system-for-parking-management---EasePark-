package com.example.test.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.auth.AuthManager
import com.example.test.models.OperationStatus
import com.example.test.models.Reservation
import com.example.test.repository.ParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import android.util.Log

class BookingsViewModel(
    private val repository: ParkingRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _activeReservations = MutableStateFlow<List<Reservation>>(emptyList())
    val activeReservations: StateFlow<List<Reservation>> = _activeReservations.asStateFlow()

    private val _pastReservations = MutableStateFlow<List<Reservation>>(emptyList())
    val pastReservations: StateFlow<List<Reservation>> = _pastReservations.asStateFlow()

    private val _operationStatus = MutableStateFlow<OperationStatus>(OperationStatus.Initial)
    val operationStatus: StateFlow<OperationStatus> = _operationStatus.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getUserReservationsFlow().collect { allReservations ->
                _activeReservations.value = allReservations.filter {
                    val status = it.getDisplayStatus()
                    status == com.example.test.models.ReservationStatus.ACTIVE || status == com.example.test.models.ReservationStatus.PENDING
                }.sortedBy { it.startTime }

                _pastReservations.value = allReservations.filter {
                    val status = it.getDisplayStatus()
                    status == com.example.test.models.ReservationStatus.COMPLETED || status == com.example.test.models.ReservationStatus.CANCELLED
                }.sortedByDescending { it.startTime }
            }
        }
    }

    fun loadReservations() {
        authManager.currentUser.value?.id?.let { userId ->
            viewModelScope.launch {
                try {
                    repository.loadUserReservations(userId)
                } catch (e: Exception) {
                    _operationStatus.value = OperationStatus.Error("Failed to load reservations: ${e.message}")
                }
            }
        }
    }
    
    fun extendReservation(reservationId: Long, newEndTime: Date) {
        authManager.currentUser.value?.id?.let { userId ->
            viewModelScope.launch {
                try {
                    repository.extendReservation(userId, reservationId, newEndTime)
                    _operationStatus.value = OperationStatus.Success("Reservation extended successfully")
                    loadReservations() // Refresh list
                } catch (e: Exception) {
                    _operationStatus.value = OperationStatus.Error("Failed to extend reservation: ${e.message}")
                }
            }
        }
    }

    fun cancelReservation(reservationId: Long) {
        authManager.currentUser.value?.id?.let { userId ->
            viewModelScope.launch {
                try {
                    repository.cancelReservation(userId, reservationId)
                    _operationStatus.value = OperationStatus.Success("Reservation cancelled successfully")
                    loadReservations() // Refresh list
                } catch (e: Exception) {
                    _operationStatus.value = OperationStatus.Error("Failed to cancel reservation: ${e.message}")
                }
            }
        }
    }

    fun resetOperationStatus() {
        _operationStatus.value = OperationStatus.Initial
    }
} 