package com.example.test.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.auth0.android.jwt.JWT
import com.example.test.models.VehicleType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.ref.WeakReference
import java.lang.reflect.Type
import java.util.Date
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://172.20.10.4:3000/"
    const val SSE_BASE_URL = "http://172.20.10.4:3000" // For SSE connections
    private const val TOKEN_KEY = "auth_token"
    private const val PREFS_NAME = "secure_prefs"
    private const val TOKEN_EXPIRY_BUFFER = 5 * 60 * 1000L // 5 minutes in milliseconds

    private lateinit var retrofit: Retrofit
    private var contextRef: WeakReference<Context>? = null
    private var sharedPreferences: SharedPreferences? = null

    @Volatile
    private var _parkingService: ParkingService? = null

    fun getParkingService(): ParkingService {
        return _parkingService ?: synchronized(this) {
            _parkingService ?: retrofit.create(ParkingService::class.java).also { _parkingService = it }
        }
    }

    fun initialize(context: Context) {
        // Folosim Application Context pentru a evita memory leaks
        val appContext = context.applicationContext
        this.contextRef = WeakReference(appContext)
        setupRetrofit(appContext)
        initializeSharedPreferences(appContext)
    }

    private fun initializeSharedPreferences(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                // Fallback pentru versiuni mai vechi de Android
                sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        } catch (e: Exception) {
            // Fallback la SharedPreferences normal dacă EncryptedSharedPreferences eșuează
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun setupRetrofit(context: Context) {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val originalRequest = chain.request()
                val url = originalRequest.url.encodedPath
                android.util.Log.d("AuthInterceptor", "Request URL: $url")
                android.util.Log.d("AuthInterceptor", "Full URL: ${originalRequest.url}")
                android.util.Log.d("AuthInterceptor", "Request Headers: ${originalRequest.headers}")
                android.util.Log.d("AuthInterceptor", "Request Method: ${originalRequest.method}")
                
                // Nu adăuga Authorization pentru login/register
                if (url.contains("/api/auth/register") || url.contains("/api/auth/login")) {
                    android.util.Log.d("AuthInterceptor", "NO Authorization header added for auth endpoint: $url")
                    val response = chain.proceed(originalRequest)
                    android.util.Log.d("AuthInterceptor", "Auth endpoint response code: ${response.code}")
                    return response
                }

                val token = getStoredToken()
                if (token == null) {
                    android.util.Log.d("AuthInterceptor", "NO token found for: $url")
                    return chain.proceed(originalRequest)
                }

                if (isTokenExpired(token)) {
                    android.util.Log.d("AuthInterceptor", "Token expired for: $url")
                    clearToken()
                    // Notify AuthManager about auth error
                    try {
                        val authManager = com.example.test.auth.AuthManager.getInstance()
                        authManager.handleAuthError()
                    } catch (e: Exception) {
                        android.util.Log.e("AuthInterceptor", "Error notifying AuthManager", e)
                    }
                    return chain.proceed(originalRequest)
                }

                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                android.util.Log.d("AuthInterceptor", "Authorization header added for: $url")
                
                val response = chain.proceed(newRequest)
                if (response.code == 401 || response.code == 403) {
                    android.util.Log.d("AuthInterceptor", "Received ${response.code} for: $url")
                    android.util.Log.d("AuthInterceptor", "Response body: ${response.peekBody(Long.MAX_VALUE).string()}")
                    // Clear token and notify AuthManager about auth error
                    clearToken()
                    try {
                        val authManager = com.example.test.auth.AuthManager.getInstance()
                        authManager.handleAuthError()
                    } catch (e: Exception) {
                        android.util.Log.e("AuthInterceptor", "Error notifying AuthManager", e)
                    }
                }
                return response
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Gson custom pentru a gestiona enum-urile în mod robust
        val gson = GsonBuilder()
            .registerTypeAdapter(VehicleType::class.java, VehicleTypeDeserializer())
            .create()

        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // Deserializer custom pentru VehicleType
    private class VehicleTypeDeserializer : JsonDeserializer<VehicleType> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): VehicleType {
            return try {
                val stringValue = json?.asString?.uppercase()
                if (stringValue != null) {
                    VehicleType.valueOf(stringValue)
                } else {
                    VehicleType.CAR
                }
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("VehicleTypeDeserializer", "Invalid vehicle type: ${json?.asString}, falling back to CAR")
                VehicleType.CAR
            } catch (e: Exception) {
                android.util.Log.e("VehicleTypeDeserializer", "Error deserializing vehicle type", e)
                VehicleType.CAR
            }
        }
    }

    fun saveToken(token: String) {
        android.util.Log.d("RetrofitClient", "Saving token: ${token.take(10)}...")
        try {
            val result = sharedPreferences?.edit()?.putString(TOKEN_KEY, token)?.commit()
            if (result == true) {
                android.util.Log.d("RetrofitClient", "Token saved successfully to EncryptedSharedPreferences")
            } else {
                android.util.Log.w("RetrofitClient", "Failed to save token to EncryptedSharedPreferences")
                // Fallback la SharedPreferences normal
                getContext()?.let { context ->
                    val normalResult = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(TOKEN_KEY, token)
                        .commit()
                    if (normalResult) {
                        android.util.Log.d("RetrofitClient", "Token saved successfully to regular SharedPreferences")
                    } else {
                        android.util.Log.e("RetrofitClient", "Failed to save token to both storage methods")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error saving token", e)
            // Fallback la SharedPreferences normal
            getContext()?.let { context ->
                try {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(TOKEN_KEY, token)
                        .commit()
                    android.util.Log.d("RetrofitClient", "Token saved successfully to regular SharedPreferences after error")
                } catch (e2: Exception) {
                    android.util.Log.e("RetrofitClient", "Error saving token to regular SharedPreferences", e2)
                }
            }
        }
    }

    fun getStoredToken(): String? {
        return try {
            val token = sharedPreferences?.getString(TOKEN_KEY, null)
            android.util.Log.d("RetrofitClient", "Retrieved token from EncryptedSharedPreferences: ${token?.take(10)}")
            token
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error getting token from EncryptedSharedPreferences", e)
            // Fallback la SharedPreferences normal
            try {
                val token = getContext()?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    ?.getString(TOKEN_KEY, null)
                android.util.Log.d("RetrofitClient", "Retrieved token from regular SharedPreferences: ${token?.take(10)}")
                token
            } catch (e2: Exception) {
                android.util.Log.e("RetrofitClient", "Error getting token from regular SharedPreferences", e2)
                null
            }
        }
    }

    fun clearToken() {
        android.util.Log.d("RetrofitClient", "Clearing stored token")
        try {
            sharedPreferences?.edit()?.remove(TOKEN_KEY)?.commit()
            android.util.Log.d("RetrofitClient", "Token cleared from EncryptedSharedPreferences")
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error clearing token from EncryptedSharedPreferences", e)
            // Fallback la SharedPreferences normal
            getContext()?.let { context ->
                try {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove(TOKEN_KEY)
                        .commit()
                    android.util.Log.d("RetrofitClient", "Token cleared from regular SharedPreferences")
                } catch (e2: Exception) {
                    android.util.Log.e("RetrofitClient", "Error clearing token from regular SharedPreferences", e2)
                }
            }
        }
    }

    private fun getContext(): Context? {
        return contextRef?.get()
    }

    private fun isTokenExpired(token: String): Boolean {
        return try {
            val jwt = JWT(token)
            val expirationDate = jwt.expiresAt
            expirationDate?.before(Date(System.currentTimeMillis() + TOKEN_EXPIRY_BUFFER)) ?: true
        } catch (e: Exception) {
            true
        }
    }
}