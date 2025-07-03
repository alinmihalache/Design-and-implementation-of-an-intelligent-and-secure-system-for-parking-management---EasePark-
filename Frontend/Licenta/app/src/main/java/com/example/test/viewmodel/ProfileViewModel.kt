package com.example.test.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.test.auth.AuthManager
import com.example.test.models.User
import com.example.test.models.Vehicle
import com.example.test.models.VehicleType
import com.example.test.repository.ParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.test.models.OperationStatus
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.test.network.Enable2FAResponse
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

sealed class Enable2FAState {
    object Idle : Enable2FAState()
    object Loading : Enable2FAState()
    data class Success(val response: Enable2FAResponse) : Enable2FAState()
    data class Error(val message: String) : Enable2FAState()
}

sealed class Verify2FAState {
    object Idle : Verify2FAState()
    object Loading : Verify2FAState()
    object Success : Verify2FAState()
    data class Error(val message: String) : Verify2FAState()
}

class ProfileViewModel : ViewModel() {
    private val authManager = AuthManager.getInstance()
    private val repository = ParkingRepository()

    val userProfile: StateFlow<User?> = authManager.currentUser.asFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, authManager.currentUser.value)

    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles

    private val _updateStatus = MutableStateFlow<OperationStatus>(OperationStatus.Initial)
    val updateStatus: StateFlow<OperationStatus> = _updateStatus.asStateFlow()

    private val _enable2FAState = MutableStateFlow<Enable2FAState>(Enable2FAState.Idle)
    val enable2FAState: StateFlow<Enable2FAState> = _enable2FAState.asStateFlow()

    private val _verify2FAState = MutableStateFlow<Verify2FAState>(Verify2FAState.Idle)
    val verify2FAState: StateFlow<Verify2FAState> = _verify2FAState.asStateFlow()

    private val _disable2FAStatus = MutableStateFlow<OperationStatus>(OperationStatus.Initial)
    val disable2FAStatus: StateFlow<OperationStatus> = _disable2FAStatus.asStateFlow()

    init {
        // Observe the user profile to load vehicles when the user is available
        viewModelScope.launch {
            userProfile.collect { user ->
                user?.let {
                    loadUserVehicles(it.id)
                }
            }
        }
    }

    fun loadUserVehicles(userId: Long) {
        viewModelScope.launch {
            try {
                repository.getUserVehicles(userId).collect { vehicles ->
                    _userVehicles.value = vehicles
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                    android.util.Log.w("ProfileViewModel", "Authentication error loading vehicles: ${e.message}")
                    authManager.handleAuthError()
                } else {
                    _updateStatus.value = OperationStatus.Error(e.message ?: "Nu s-au putut încărca vehiculele")
                }
            }
        }
    }

    fun updateProfile(
        firstName: String? = null,
        lastName: String? = null,
        phone: String? = null
    ) {
        viewModelScope.launch {
            try {
                val userId = userProfile.value?.id
                if (userId == null) {
                    _updateStatus.value = OperationStatus.Error("Utilizatorul nu este încărcat")
                    return@launch
                }
                if (phone != null && !phone.matches(Regex("^\\+?[0-9]{10,}$"))) {
                    _updateStatus.value = OperationStatus.Error("Numărul de telefon nu este valid")
                    return@launch
                }
                _updateStatus.value = OperationStatus.Loading
                repository.updateUserProfile(userId, null, firstName, lastName, phone)
                _updateStatus.value = OperationStatus.Success("Profil actualizat cu succes!")
            } catch (e: Exception) {
                _updateStatus.value = OperationStatus.Error(e.message ?: "Nu s-a putut actualiza profilul")
            }
        }
    }

    fun addVehicle(plateNumber: String, make: String, model: String, year: Int, type: String) {
        viewModelScope.launch {
            try {
                val userId = userProfile.value?.id
                if (userId == null) {
                    _updateStatus.value = OperationStatus.Error("Utilizatorul nu este încărcat")
                    return@launch
                }
                val vehicleType = try {
                    VehicleType.valueOf(type.uppercase())
                } catch (e: IllegalArgumentException) {
                    _updateStatus.value = OperationStatus.Error("Tip de vehicul invalid.")
                    return@launch
                }

                val newVehicle = repository.addVehicle(userId, plateNumber, make, model, year, vehicleType)

                val currentVehicles = _userVehicles.value.toMutableList()
                currentVehicles.add(newVehicle)
                _userVehicles.value = currentVehicles

                _updateStatus.value = OperationStatus.Success("Vehicul adăugat cu succes")

            } catch (e: Exception) {
                _updateStatus.value = OperationStatus.Error("Eroare la adăugarea vehiculului: ${e.message}")
            }
        }
    }

    fun deleteVehicle(vehicleId: Long) {
        viewModelScope.launch {
            try {
                val userId = userProfile.value?.id
                if (userId == null) {
                    _updateStatus.value = OperationStatus.Error("Utilizatorul nu este încărcat")
                    return@launch
                }
                repository.deleteVehicle(userId, vehicleId)
                _userVehicles.value = _userVehicles.value.filter { it.id != vehicleId }
                _updateStatus.value = OperationStatus.Success("Vehicul șters cu succes!")
            } catch (e: Exception) {
                _updateStatus.value = OperationStatus.Error(e.message ?: "Nu s-a putut șterge vehiculul")
            }
        }
    }

    fun enable2FA() {
        viewModelScope.launch {
            _enable2FAState.value = Enable2FAState.Loading
            val result = authManager.enable2fa()
            result.onSuccess { response ->
                _enable2FAState.value = Enable2FAState.Success(response)
            }.onFailure { error ->
                _enable2FAState.value = Enable2FAState.Error(error.message ?: "A eșuat activarea 2FA.")
            }
        }
    }

    fun disable2FA() {
        viewModelScope.launch {
            _disable2FAStatus.value = OperationStatus.Loading
            val result = authManager.disable2fa()
            result.onSuccess {
                _disable2FAStatus.value = OperationStatus.Success("2FA Dezactivat cu succes.")
                // No need to manually refresh, AuthManager will update and the flow will emit the new user state.
            }.onFailure { error ->
                _disable2FAStatus.value = OperationStatus.Error(error.message ?: "A eșuat dezactivarea 2FA.")
            }
        }
    }

    fun verify2FA(token: String) {
        if (token.length != 6) {
            _verify2FAState.value = Verify2FAState.Error("Codul trebuie să conțină 6 cifre.")
            return
        }
        viewModelScope.launch {
            _verify2FAState.value = Verify2FAState.Loading
            val result = authManager.verify2fa(token)
            result.onSuccess {
                _verify2FAState.value = Verify2FAState.Success
                // No need to manually refresh, AuthManager will update and the flow will emit the new user state.
            }.onFailure { error ->
                _verify2FAState.value = Verify2FAState.Error(error.message ?: "Verificarea a eșuat.")
            }
        }
    }

    fun resetEnable2FAState() {
        _enable2FAState.value = Enable2FAState.Idle
    }

    fun resetVerify2FAState() {
        _verify2FAState.value = Verify2FAState.Idle
    }

    fun resetDisable2FAState() {
        _disable2FAStatus.value = OperationStatus.Initial
    }

    fun resetUpdateStatus() {
        _updateStatus.value = OperationStatus.Initial
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches(Regex("^[0-9]{10}$"))
    }
} 

