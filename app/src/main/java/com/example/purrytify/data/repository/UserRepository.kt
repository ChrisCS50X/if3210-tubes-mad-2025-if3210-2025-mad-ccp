package com.example.purrytify.data.repository

import com.example.purrytify.data.api.NetworkModule
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.LoginRequest
import com.example.purrytify.data.model.LoginResponse
import com.example.purrytify.data.model.RefreshTokenRequest
import com.example.purrytify.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.purrytify.util.executeWithTokenRefresh
import android.util.Log

class UserRepository(private val tokenManager: TokenManager) {

    private val apiService = NetworkModule.apiService

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val loginRequest = LoginRequest(email, password)
                val response = apiService.login(loginRequest)

                // Save the authentication token
                response.token?.let { tokenManager.saveToken(it) }

                // Save refresh token if it's included in response
                response.refreshToken?.let { tokenManager.saveRefreshToken(it) }

                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserProfile(): Result<UserProfile> {
        if (!tokenManager.isLoggedIn()) {
            return Result.failure(IllegalStateException("Not logged in"))
        }

        return executeWithTokenRefresh(this) {
            val token = tokenManager.getToken()!!
            apiService.getUserProfile("Bearer $token")
        }
    }

    suspend fun verifyToken(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = tokenManager.getToken() ?: return@withContext false
                val response = apiService.verifyToken("Bearer $token")
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("UserRepository", "Error verifying token", e)
                false
            }
        }
    }

    suspend fun refreshToken(): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val refreshToken = tokenManager.getRefreshToken() ?: return@withContext Result.failure(
                    IllegalStateException("No refresh token available")
                )

                val request = RefreshTokenRequest(refreshToken)
                val response = apiService.refreshToken(request)

                // Save the new tokens
                response.token?.let { tokenManager.saveToken(it) }
                response.refreshToken?.let { tokenManager.saveRefreshToken(it) }

                Result.success(response)
            } catch (e: Exception) {
                Log.e("UserRepository", "Failed to refresh token", e)
                Result.failure(e)
            }
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }
}