package com.example.purrytify.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tokenManager = TokenManager(appContext)
    private val userRepository = UserRepository(tokenManager)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking token validity")

            // Skip if not logged in
            if (!tokenManager.isLoggedIn()) {
                Log.d(TAG, "User not logged in, skipping token check")
                return@withContext Result.success()
            }

            // Check if token is valid using verify-token endpoint
            val isTokenValid = userRepository.verifyToken()

            if (!isTokenValid) {
                Log.d(TAG, "Token expired, attempting refresh")

                // Try to refresh the token
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isSuccess) {
                    Log.d(TAG, "Token refreshed successfully")
                    return@withContext Result.success()
                } else {
                    // Failed to refresh, user needs to login again
                    Log.d(TAG, "Failed to refresh token, clearing tokens")
                    tokenManager.clearTokens()
                    return@withContext Result.failure()
                }
            }

            Log.d(TAG, "Token is still valid")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/refreshing token", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TokenRefreshWorker"
    }
}