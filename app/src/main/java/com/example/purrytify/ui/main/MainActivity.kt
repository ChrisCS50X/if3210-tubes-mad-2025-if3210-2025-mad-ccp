package com.example.purrytify.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.example.purrytify.service.TokenRefreshManager
import android.content.Intent
import android.util.Log
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.UserRepository
import com.example.purrytify.ui.login.LoginActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {
    private val networkViewModel by viewModels<NetworkViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenRefreshManager: TokenRefreshManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Just log tokens
        val tokenManager = TokenManager(this)
        val currentToken = tokenManager.getToken()
        Log.d("TokenDebug", "Current stored token: $currentToken")
        val refreshToken = tokenManager.getRefreshToken()
        Log.d("TokenDebug", "Current refresh token: $refreshToken")

        // Check token validity before showing any UI
        verifyTokenAndNavigate()
    }

    private fun verifyTokenAndNavigate() {
        val tokenManager = TokenManager(this)

        // Debug logging
        val currentToken = tokenManager.getToken()
        Log.d("TokenDebug", "Current stored token: $currentToken")
        val refreshToken = tokenManager.getRefreshToken()
        Log.d("TokenDebug", "Current refresh token: $refreshToken")

        // If no token exists, go to login
        if (!tokenManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        // Check if token is valid
        lifecycleScope.launch {
            val userRepository = UserRepository(tokenManager)
            val isTokenValid = userRepository.verifyToken()

            if (!isTokenValid) {
                Log.d("TokenDebug", "Token invalid, attempting refresh")
                // Try to refresh the token
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isFailure) {
                    Log.d("TokenDebug", "Token refresh failed, navigating to login")
                    // Couldn't refresh token, go to login
                    tokenManager.clearTokens()
                    navigateToLogin()
                    return@launch
                }
            }

            // Token is valid or was refreshed successfully
            Log.d("TokenDebug", "Token valid or refreshed, continuing to main app")
            continueToMainApp()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun continueToMainApp() {
        runOnUiThread {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupNavigation()
            checkPermissions()
            setupTokenRefresh()
        }
    }

    private fun setupTokenRefresh() {
        tokenRefreshManager = TokenRefreshManager(this)
        tokenRefreshManager.scheduleTokenRefresh()
    }


    private fun setupNavigation() {
        // Get NavController correctly from the NavHostFragment
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Connect BottomNavigationView with NavController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request granular media permissions
            val permissions = mutableListOf<String>()

            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }

            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), STORAGE_PERMISSION_CODE)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Storage permission is required to add songs", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }
}