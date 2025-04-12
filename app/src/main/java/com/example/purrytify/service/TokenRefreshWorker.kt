package com.example.purrytify.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker yang ngejalanin proses refresh token.
 * Kelas ini turunan dari CoroutineWorker, jadi bisa pake coroutine.
 * Dipanggil otomatis sama WorkManager sesuai jadwal di TokenRefreshManager.
 */
class TokenRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tokenManager = TokenManager(appContext)
    private val userRepository = UserRepository(tokenManager)

    /**
     * Method utama yang dijalanin sama WorkManager.
     * Di sini kita ngecek token, terus refresh kalo perlu.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking token validity")

            // Kalo belum login, ga usah lanjut
            if (!tokenManager.isLoggedIn()) {
                Log.d(TAG, "User not logged in, skipping token check")
                return@withContext Result.success()
            }

            // Cek token masih valid apa engga
            val isTokenValid = userRepository.verifyToken()

            if (!isTokenValid) {
                Log.d(TAG, "Token expired, attempting refresh")

                // Coba refresh token
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isSuccess) {
                    Log.d(TAG, "Token refreshed successfully")
                    return@withContext Result.success()
                } else {
                    // Gagal refresh, user harus login lagi
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