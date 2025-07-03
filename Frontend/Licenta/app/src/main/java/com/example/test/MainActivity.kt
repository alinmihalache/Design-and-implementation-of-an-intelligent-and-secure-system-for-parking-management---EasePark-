package com.example.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.test.auth.AuthManager
import com.example.test.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val authManager by lazy { AuthManager.getInstance() }
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        observeAuthState()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Ascunde bara de navigare pentru fragmentele de autentificare
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment -> {
                    binding.bottomNav.visibility = android.view.View.GONE
                }
                else -> {
                    binding.bottomNav.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun observeAuthState() {
        authManager.isAuthenticated.observe(this) { isAuthenticated ->
            android.util.Log.d("MainActivity", "isAuthenticated: $isAuthenticated, currentDestination: ${navController.currentDestination?.id}")
            if (isNavigating) return@observe

            try {
                isNavigating = true
                when {
                    !isAuthenticated && navController.currentDestination?.id != R.id.loginFragment -> {
                        android.util.Log.d("MainActivity", "Navigating to loginFragment (deauth)")
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.mapFragment, true)
                            .build()
                        navController.navigate(R.id.loginFragment, null, navOptions)
                    }
                    isAuthenticated && navController.currentDestination?.id == R.id.loginFragment -> {
                        android.util.Log.d("MainActivity", "Navigating to mapFragment (auth)")
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.loginFragment, true)
                            .build()
                        navController.navigate(R.id.mapFragment, null, navOptions)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Navigation error: ", e)
            } finally {
                isNavigating = false
            }
        }
    }
} 