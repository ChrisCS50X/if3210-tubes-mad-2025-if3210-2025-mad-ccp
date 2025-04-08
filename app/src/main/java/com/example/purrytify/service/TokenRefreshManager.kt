package com.example.purrytify.service

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class TokenRefreshManager(private val context: Context) {

    fun scheduleTokenRefresh() {
        // Define constraints - needs network connectivity
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a periodic work request that runs every 4 minutes
        // (just before the 5-minute expiration)
        val tokenRefreshRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
            4, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        // Enqueue the work request
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TOKEN_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            tokenRefreshRequest
        )
    }

    fun cancelTokenRefresh() {
        WorkManager.getInstance(context).cancelUniqueWork(TOKEN_REFRESH_WORK_NAME)
    }

    companion object {
        private const val TOKEN_REFRESH_WORK_NAME = "token_refresh_work"
    }
}