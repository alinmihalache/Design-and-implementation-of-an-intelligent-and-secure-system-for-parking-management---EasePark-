package com.example.test.network

import android.util.Log
import com.example.test.models.ParkingSpot
import com.example.test.models.ParkingSpotEvent
import com.example.test.models.ParkingSpotUpdateEvent
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ReservationEvent(
    @SerializedName("type")
    val type: String,
    @SerializedName("reservationId")
    val reservationId: Long,
    @SerializedName("userId")
    val userId: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("timestamp")
    val timestamp: String? = null
)

class ParkingSpotSSEClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?
) {
    private var client: OkHttpClient? = null
    private var connection: Call? = null
    private var isConnected = false
    private var reconnectJob: Job? = null
    
    private val gson = Gson()
    private val eventListeners = mutableListOf<(ParkingSpotUpdateEvent) -> Unit>()
    private val statusListeners = mutableListOf<(ParkingSpotEvent) -> Unit>()
    private val reservationListeners = mutableListOf<(ReservationEvent) -> Unit>()
    
    companion object {
        private const val TAG = "ParkingSpotSSEClient"
        private const val RECONNECT_DELAY = 5000L // 5 secunde
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
    
    fun connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected to SSE")
            return
        }
        
        Log.d(TAG, "Connecting to SSE endpoint")
        
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for SSE
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/parking-spots/stream")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
        
        // Adaugă token-ul de autentificare dacă există
        val token = tokenProvider()
        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        val request = requestBuilder.build()
        
        connection = client?.newCall(request)
        connection?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "SSE connection failed: ${e.message}")
                isConnected = false
                scheduleReconnect()
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d(TAG, "SSE connection established. Starting to read stream...")
                    isConnected = true
                    handleSSEResponse(response)
                } else {
                    Log.e(TAG, "SSE connection failed with code: ${response.code}")
                    isConnected = false
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun handleSSEResponse(response: Response) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                var line: String?
                
                while (reader.readLine().also { line = it } != null && isConnected) {
                    line?.let { processSSELine(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading SSE stream: ${e.message}")
                isConnected = false
                scheduleReconnect()
            }
        }
    }
    
    private fun processSSELine(line: String) {
        if (line.startsWith("data: ")) {
            val data = line.substring(6) // Remove "data: " prefix
            if (data.isNotBlank() && data != "[DONE]") {
                try {
                    processEventData(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SSE data: ${e.message}")
                }
            }
        }
    }
    
    private fun processEventData(data: String) {
        try {
            // Log raw data for debugging
            Log.d(TAG, "SSE: Received raw data: $data")
            
            // First try to parse as array of ParkingSpot (actual server format)
            try {
                val spots = gson.fromJson(data, Array<com.example.test.models.ParkingSpot>::class.java)
                if (spots.isNotEmpty()) {
                    Log.d(TAG, "SSE: Parsed as full update with ${spots.size} spots.")
                    // Convert to ParkingSpotUpdateEvent
                    val updateEvent = ParkingSpotUpdateEvent(
                        type = "parking_spot_updated",
                        spots = spots.toList()
                    )
                    notifyUpdateListeners(updateEvent)
                    return
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not an array of ParkingSpot: ${e.message}")
            }
            
            // Then try to parse as ParkingSpotUpdateEvent (lista completă)
            try {
                val updateEvent = gson.fromJson(data, ParkingSpotUpdateEvent::class.java)
                if (updateEvent.type == "parking_spot_updated" && updateEvent.spots.isNotEmpty()) {
                    Log.d(TAG, "SSE: Parsed as ParkingSpotUpdateEvent with ${updateEvent.spots.size} spots.")
                    notifyUpdateListeners(updateEvent)
                    return
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a ParkingSpotUpdateEvent: ${e.message}")
            }
            
            // Then try to parse as array of ParkingSpotEvent
            try {
                val events = gson.fromJson(data, Array<ParkingSpotEvent>::class.java)
                if (events.isNotEmpty()) {
                    Log.d(TAG, "SSE: Parsed as array of ${events.size} ParkingSpotEvent(s).")
                    events.forEach { event ->
                        if (event.spotId > 0) {
                            Log.d(TAG, "SSE: Processing event: ${event.type} for spot ${event.spotId} with status ${event.status}")
                            notifyStatusListeners(event)
                        } else {
                            Log.w(TAG, "Invalid spot ID ${event.spotId} in event")
                        }
                    }
                    return
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not an array of ParkingSpotEvent: ${e.message}")
            }
            
            // Finally try to parse as single ParkingSpotEvent
            try {
                val event = gson.fromJson(data, ParkingSpotEvent::class.java)
                if (event.spotId > 0) {
                    Log.d(TAG, "SSE: Parsed as single ParkingSpotEvent: ${event.type} for spot ${event.spotId} with status ${event.status}")
                    notifyStatusListeners(event)
                    return
                } else {
                    Log.w(TAG, "Invalid spot ID ${event.spotId} in single event")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a single ParkingSpotEvent: ${e.message}")
            }
            
            // Try to parse as ReservationEvent (for scheduler updates)
            try {
                val reservationEvent = gson.fromJson(data, ReservationEvent::class.java)
                if (reservationEvent.reservationId > 0 && reservationEvent.userId > 0) {
                    Log.d(TAG, "SSE: Parsed as ReservationEvent: ${reservationEvent.type} for reservation ${reservationEvent.reservationId} with status ${reservationEvent.status}")
                    notifyReservationListeners(reservationEvent)
                    return
                } else {
                    Log.w(TAG, "Invalid reservation ID ${reservationEvent.reservationId} or user ID ${reservationEvent.userId} in reservation event")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a ReservationEvent: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSE: Error processing data block.", e)
            Log.e(TAG, "SSE: Data was: $data")
        }
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(RECONNECT_DELAY)
            Log.d(TAG, "Attempting to reconnect to SSE")
            connect()
        }
    }
    
    fun disconnect() {
        Log.d(TAG, "Disconnecting from SSE")
        isConnected = false
        reconnectJob?.cancel()
        connection?.cancel()
        connection = null
        client = null
    }
    
    fun addUpdateListener(listener: (ParkingSpotUpdateEvent) -> Unit) {
        eventListeners.add(listener)
    }
    
    fun addStatusListener(listener: (ParkingSpotEvent) -> Unit) {
        statusListeners.add(listener)
    }
    
    fun addReservationListener(listener: (ReservationEvent) -> Unit) {
        reservationListeners.add(listener)
    }
    
    fun removeUpdateListener(listener: (ParkingSpotUpdateEvent) -> Unit) {
        eventListeners.remove(listener)
    }
    
    fun removeStatusListener(listener: (ParkingSpotEvent) -> Unit) {
        statusListeners.remove(listener)
    }
    
    fun removeReservationListener(listener: (ReservationEvent) -> Unit) {
        reservationListeners.remove(listener)
    }
    
    private fun notifyUpdateListeners(event: ParkingSpotUpdateEvent) {
        CoroutineScope(Dispatchers.Main).launch {
            eventListeners.forEach { listener ->
                try {
                    listener(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in update listener: ${e.message}")
                }
            }
        }
    }
    
    private fun notifyStatusListeners(event: ParkingSpotEvent) {
        CoroutineScope(Dispatchers.Main).launch {
            statusListeners.forEach { listener ->
                try {
                    listener(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in status listener: ${e.message}")
                }
            }
        }
    }
    
    private fun notifyReservationListeners(event: ReservationEvent) {
        CoroutineScope(Dispatchers.Main).launch {
            reservationListeners.forEach { listener ->
                try {
                    listener(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in reservation listener: ${e.message}")
                }
            }
        }
    }
    
    fun isConnected(): Boolean = isConnected
} 