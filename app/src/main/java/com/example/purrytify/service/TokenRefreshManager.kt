package com.example.purrytify.service

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Kelas untuk ngatur refresh token otomatis.
 * Gunanya buat jadwalin WorkManager yang secara berkala refresh token JWT
 * sebelum token kita expired.
 */
class TokenRefreshManager(private val context: Context) {

    /**
     * Jadwalin proses refresh token secara periodik.
     * Kita set jalan tiap 4 menit, karena token kita expired-nya 5 menit.
     * Jadi kita refresh duluan sebelum beneran expired.
     */
    fun scheduleTokenRefresh() {
        // Tentuin requirement, harus ada koneksi internet
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Bikin periodic work request yang jalan tiap 4 menit
        val tokenRefreshRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
            4, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        // Masukkin ke antrian WorkManager
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TOKEN_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            tokenRefreshRequest
        )
    }

    /**
     * Batalin jadwal refresh token.
     * Dipanggil waktu user logout.
     */
    fun cancelTokenRefresh() {
        WorkManager.getInstance(context).cancelUniqueWork(TOKEN_REFRESH_WORK_NAME)
    }

    companion object {
        private const val TOKEN_REFRESH_WORK_NAME = "token_refresh_work"
    }
}