package com.example.test.auth

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.auth0.android.jwt.JWT
import com.example.test.models.User
import com.example.test.network.ParkingService
import com.example.test.network.RetrofitClient
import com.example.test.network.LoginRequest
import com.example.test.network.RegisterRequest
import com.example.test.network.Enable2FARequest
import com.example.test.network.Enable2FAResponse
import com.example.test.network.Verify2FARequest
import com.example.test.network.Disable2FARequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AuthManager private constructor(private val context: Context) {
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isAuthenticated = MutableLiveData<Boolean>()
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated

    private val parkingService: ParkingService by lazy {
        RetrofitClient.getParkingService()
    }

    init {
        android.util.Log.d("AuthManager", "AuthManager instance created with context: $context")
        // Launch a coroutine to perform the initial auth check
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            checkAuthState()
        }
    }

    private suspend fun checkAuthState() {
        val token = RetrofitClient.getStoredToken()
        android.util.Log.d("AuthManager", "Checking auth state with token: ${token?.take(10)}...")

        if (token != null && !isTokenExpired(token)) {
            try {
                // First, validate the token locally for quick checks
                val jwt = JWT(token)
                val userId = jwt.getClaim("id").asLong()
                val email = jwt.getClaim("email").asString()

                if (userId == null || email.isNullOrEmpty()) {
                    android.util.Log.d("AuthManager", "Token invalid: missing required claims.")
                    signOut()
                    return
                }

                // Token seems valid, now fetch the full user profile from the server
                // This ensures we have the most up-to-date user data, including 2FA status
                val response = parkingService.getCurrentUser()
                if (response.isSuccessful) {
                    val userFromServer = response.body()
                    if (userFromServer != null) {
                        _currentUser.postValue(userFromServer)
                        _isAuthenticated.postValue(true)
                        android.util.Log.d("AuthManager", "User authenticated successfully via server: ${userFromServer.email}")
                    } else {
                        android.util.Log.e("AuthManager", "Server returned empty user profile.")
                        signOut()
                    }
                } else {
                    android.util.Log.e("AuthManager", "Failed to fetch user profile from server: ${response.code()}")
                    handleAuthError() // Token might be valid, but API denies access
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthManager", "Error during auth state check", e)
                signOut()
            }
        } else {
            android.util.Log.d("AuthManager", "No valid token found or token expired, signing out")
            signOut()
        }
    }

    suspend fun login(email: String, password: String, token2FA: String? = null): LoginResult = withContext(Dispatchers.IO) {
        try {
            val request = LoginRequest(email, password, token2FA)
            val response = parkingService.login(request)

            if (response.isSuccessful) {
                response.body()?.let { authResponse ->
                    // Successful login with a valid token
                    RetrofitClient.saveToken(authResponse.token)
                    // Use the user object from the response directly! This is the fix.
                    _currentUser.postValue(authResponse.user)
                    _isAuthenticated.postValue(true)
                    LoginResult.Success(authResponse)
                } ?: LoginResult.Error("Login failed: Empty response body")
            } else {
                // Check for 2FA required case
                // The user clarified the backend will send a response that the frontend should interpret
                // I will assume a specific status code or error message. A 403 or a custom error response is likely.
                val errorBody = response.errorBody()?.string()
                if (response.code() == 403 || (errorBody != null && "2FA" in errorBody.uppercase())) {
                    LoginResult.TwoFactorRequired
                } else {
                    LoginResult.Error("Login failed: ${errorBody ?: response.message()}")
                }
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "An unknown error occurred")
        }
    }

    suspend fun enable2fa(): Result<Enable2FAResponse> = withContext(Dispatchers.IO) {
        try {
            val userId = _currentUser.value?.id ?: return@withContext Result.failure(Exception("User not logged in"))
            val request = Enable2FARequest(userId)
            val response = parkingService.enable2fa(request)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("Failed to enable 2FA: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verify2fa(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = _currentUser.value?.id ?: return@withContext Result.failure(Exception("User not logged in"))
            val request = Verify2FARequest(userId, token)
            val response = parkingService.verify2fa(request)
            if (response.isSuccessful) {
                // Backend now returns the updated user object. Let's use it!
                response.body()?.user?.let { updatedUser ->
                    _currentUser.postValue(updatedUser)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Verification failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disable2fa(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = _currentUser.value?.id ?: return@withContext Result.failure(Exception("User not logged in"))
            val request = Disable2FARequest(userId)
            val response = parkingService.disable2fa(request)
            if (response.isSuccessful) {
                // After successful deactivation, we must refetch user to update 2FA status
                checkAuthState()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Deactivation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, firstName: String, lastName: String, email: String, phoneNumber: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = parkingService.register(RegisterRequest(
                    username = username,
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phone = phoneNumber,
                    password = password
                ))
                if (response.isSuccessful) {
                    response.body()?.let { authResponse ->
                        RetrofitClient.saveToken(authResponse.token)
                        // Don't set user directly, let checkAuthState be the single source of truth
                        _currentUser.postValue(authResponse.user)
                        _isAuthenticated.postValue(true)
                        android.util.Log.d("AuthManager", "isAuthenticated set to true in register")
                        Result.success(authResponse.user)
                    } ?: Result.failure(Exception("Empty response body"))
                } else {
                    Result.failure(Exception("Registration failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun signOut() {
        RetrofitClient.clearToken()
        _currentUser.postValue(null)
        _isAuthenticated.postValue(false)
        android.util.Log.d("AuthManager", "isAuthenticated set to false in signOut")
    }

    fun handleAuthError() {
        android.util.Log.d("AuthManager", "Handling authentication error")
        RetrofitClient.clearToken()
        _currentUser.postValue(null)
        _isAuthenticated.postValue(false)
        android.util.Log.d("AuthManager", "isAuthenticated set to false due to auth error")
    }

    private fun isTokenExpired(token: String): Boolean {
        return try {
            val jwt = JWT(token)
            val expirationDate = jwt.expiresAt
            expirationDate?.before(Date(System.currentTimeMillis() - 5 * 60 * 1000)) ?: true
        } catch (e: Exception) {
            true
        }
    }

    companion object {
        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): AuthManager {
            return instance ?: throw IllegalStateException("AuthManager must be initialized with context first")
        }
    }
}

sealed class AuthState {
    object NotAuthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
}

sealed class LoginResult {
    data class Success(val authResponse: com.example.test.network.AuthResponse) : LoginResult()
    object TwoFactorRequired : LoginResult()
    data class Error(val message: String) : LoginResult()
}