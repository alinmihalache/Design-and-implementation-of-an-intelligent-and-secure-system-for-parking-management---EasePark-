package com.example.test.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.test.repository.ParkingRepository

class ParkingViewModelFactory(private val repository: ParkingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParkingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ParkingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 