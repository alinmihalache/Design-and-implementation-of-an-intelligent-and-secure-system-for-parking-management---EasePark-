package com.example.test

import android.app.Application
import com.example.test.auth.AuthManager
import com.example.test.network.RetrofitClient

class ParkingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("ParkingApplication", "Initializing application...")
        
        try {
            // Initialize RetrofitClient
            RetrofitClient.initialize(applicationContext)
            android.util.Log.d("ParkingApplication", "RetrofitClient initialized successfully")
            
            // Initialize AuthManager
            val authManager = AuthManager.getInstance(applicationContext)
            android.util.Log.d("ParkingApplication", "AuthManager initialized successfully")
            
            // Verificăm starea inițială de autentificare
            authManager.isAuthenticated.observeForever { isAuthenticated ->
                android.util.Log.d("ParkingApplication", "Initial auth state: isAuthenticated=$isAuthenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("ParkingApplication", "Error initializing application", e)
        }
    }
} 