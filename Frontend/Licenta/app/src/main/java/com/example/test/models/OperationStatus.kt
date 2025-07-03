package com.example.test.models

sealed class OperationStatus {
    object Initial : OperationStatus()
    object Loading : OperationStatus()
    data class Success(val message: String) : OperationStatus()
    data class Error(val message: String) : OperationStatus()
} 