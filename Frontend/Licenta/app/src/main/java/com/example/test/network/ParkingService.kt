package com.example.test.network

import com.example.test.models.*
import retrofit2.Response
import retrofit2.http.*
import java.util.Date
import com.google.gson.annotations.SerializedName

interface ParkingService {
    // Auth endpoints
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/enable-2fa")
    suspend fun enable2fa(@Body request: Enable2FARequest): Response<Enable2FAResponse>

    @POST("api/auth/verify-2fa")
    suspend fun verify2fa(@Body request: Verify2FARequest): Response<AuthResponse>

    @POST("api/auth/disable-2fa")
    suspend fun disable2fa(@Body request: Disable2FARequest): Response<Unit>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>

    // User endpoints
    @GET("api/users/me")
    suspend fun getCurrentUser(): Response<User>

    @PUT("api/users/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: Long,
        @Body request: UpdateProfileRequest
    ): Response<User>

    // Vehicle endpoints
    @GET("api/users/{userId}/vehicles")
    suspend fun getVehicles(@Path("userId") userId: Long): Response<List<Vehicle>>

    @POST("api/users/{userId}/vehicles")
    suspend fun addVehicle(
        @Path("userId") userId: Long,
        @Body request: VehicleRequest
    ): Response<Vehicle>

    @DELETE("api/users/{userId}/vehicles/{vehicleId}")
    suspend fun deleteVehicle(
        @Path("userId") userId: Long,
        @Path("vehicleId") vehicleId: Long
    ): Response<Unit>

    // Parking spot endpoints
    @GET("api/parking-spots")
    suspend fun getParkingSpots(
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("radius") radius: Double? = null
    ): Response<ParkingSpotsResponse>

    @GET("api/parking-spots/events")
    suspend fun getParkingSpotsEvents(): Response<String>

    @GET("parking-spots/{id}")
    suspend fun getParkingSpot(@Path("id") id: Long): Response<ParkingSpot>

    @POST("parking-spots")
    suspend fun createParkingSpot(@Body request: ParkingSpotRequest): Response<ParkingSpot>

    @PUT("parking-spots/{id}")
    suspend fun updateParkingSpot(
        @Path("id") id: Long,
        @Body request: ParkingSpotUpdateRequest
    ): Response<ParkingSpot>

    @DELETE("parking-spots/{id}")
    suspend fun deleteParkingSpot(@Path("id") id: Long): Response<Unit>

    // Reservation endpoints
    @GET("api/users/{userId}/reservations")
    suspend fun getReservations(@Path("userId") userId: Long): Response<List<Reservation>>

    @GET("api/users/{userId}/reservations/{id}")
    suspend fun getReservation(
        @Path("userId") userId: Long,
        @Path("id") id: Long
    ): Response<Reservation>

    @POST("api/reservations")
    suspend fun createReservation(@Body request: ReservationRequest): Response<Reservation>

    @PUT("api/users/{userId}/reservations/{id}")
    suspend fun updateReservation(
        @Path("userId") userId: Long,
        @Path("id") id: Long,
        @Body request: ReservationUpdateRequest
    ): Response<Reservation>

    @DELETE("api/users/{userId}/reservations/{id}")
    suspend fun cancelReservation(
        @Path("userId") userId: Long,
        @Path("id") id: Long
    ): Response<Unit>

    // Payment endpoints
    @GET("api/users/{userId}/payments")
    suspend fun getPayments(
        @Path("userId") userId: Long,
        @Query("reservationId") reservationId: Long? = null
    ): Response<List<Payment>>

    @POST("api/users/{userId}/payments")
    suspend fun createPayment(
        @Path("userId") userId: Long,
        @Body request: PaymentRequest
    ): Response<Payment>

    @GET("api/users/{userId}/payments/{id}")
    suspend fun getPayment(
        @Path("userId") userId: Long,
        @Path("id") id: Long
    ): Response<Payment>

    @POST("api/users/{userId}/payments/{id}/confirm")
    suspend fun confirmPayment(
        @Path("userId") userId: Long,
        @Path("id") id:Long
    ): Response<Payment>

    // Admin endpoints
    @GET("api/admin/users")
    suspend fun getAdminUsers(): Response<List<User>>

    @GET("api/admin/reservations")
    suspend fun getAdminReservations(): Response<List<Reservation>>

    @GET("api/admin/payments")
    suspend fun getAdminPayments(): Response<List<Payment>>

    companion object {
        private var instance: ParkingService? = null

        fun getInstance(): ParkingService {
            return instance ?: synchronized(this) {
                instance ?: RetrofitClient.getParkingService()
                    .also { instance = it }
            }
        }
    }
}

// Request/Response Models
data class Location(
    val latitude: Double,
    val longitude: Double
)

data class RegisterRequest(
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("token2FA") val token2FA: String? = null
)

data class Enable2FARequest(
    @SerializedName("userId") val userId: Long
)

data class Enable2FAResponse(
    @SerializedName("secret") val secret: String,
    @SerializedName("qr") val qr: String
)

data class Verify2FARequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("token") val token: String
)

data class Disable2FARequest(
    @SerializedName("userId") val userId: Long
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class UpdateProfileRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null
)

data class VehicleRequest(
    val plateNumber: String,
    val make: String,
    val model: String,
    val year: Int,
    val type: String
)

data class ParkingSpotRequest(
    @SerializedName("address")
    val address: String,
    @SerializedName("location")
    val location: Location,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("type")
    val type: ParkingSpotType,
    @SerializedName("price_per_hour")
    val pricePerHour: Double,
    @SerializedName("is_available")
    val isAvailable: Boolean = true
)

data class ParkingSpotUpdateRequest(
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("location")
    val location: Location? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("type")
    val type: ParkingSpotType? = null,
    @SerializedName("price_per_hour")
    val pricePerHour: Double? = null,
    @SerializedName("is_available")
    val isAvailable: Boolean? = null
)

data class ReservationRequest(
    @SerializedName("userId") val userId: Long,
    val vehicleId: Long,
    val parkingSpotId: Long,
    val startTime: String,
    val endTime: String
)

data class ReservationUpdateRequest(
    val startTime: Date? = null,
    val endTime: Date? = null,
    val status: ReservationStatus? = null
)

data class PaymentRequest(
    val reservationId: Long,
    val amount: Double
)

enum class PaymentMethod {
    CARD,
    CASH
} 