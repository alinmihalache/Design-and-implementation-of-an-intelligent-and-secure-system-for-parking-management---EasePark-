package com.example.test.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.test.auth.AuthManager
import com.example.test.repository.ParkingRepository

class BookingsViewModelFactory(
    private val repository: ParkingRepository,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookingsViewModel(repository, authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 