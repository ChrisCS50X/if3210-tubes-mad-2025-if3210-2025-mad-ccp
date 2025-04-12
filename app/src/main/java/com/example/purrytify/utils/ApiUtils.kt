package com.example.purrytify.utils

import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Fungsi helper untuk eksekusi API call dengan refresh token otomatis.
 *
 * Cara kerjanya:
 * 1. Coba panggil API
 * 2. Kalo error 401/403 (token expired), refresh dulu token-nya
 * 3. Terus coba lagi API call dengan token baru
 *
 * Fungsi ini bikin kode lebih bersih karena ga perlu handling token expired
 * di setiap panggilan API.
 */
suspend fun <T> executeWithTokenRefresh(
    userRepository: UserRepository,
    apiCall: suspend () -> T
): Result<T> {
    return withContext(Dispatchers.IO) {
        try {
            // Percobaan pertama
            val result = apiCall()
            Result.success(result)
        } catch (e: HttpException) {
            // Kalo error 401/403, coba refresh token terus retry
            if (e.code() == 401 || e.code() == 403) {
                // Token kayaknya expired, coba refresh dulu
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isSuccess) {
                    try {
                        // Token berhasil di-refresh, coba lagi API call-nya
                        val result = apiCall()
                        Result.success(result)
                    } catch (e: Exception) {
                        // Tetep error meskipun udah pake token baru, ada masalah lain
                        Result.failure(e)
                    }
                } else {
                    // Refresh token gagal, user harus login ulang
                    Result.failure(Exception("Authentication failed. Please log in again."))
                }
            } else {
                // Error bukan karena token, terusin error-nya
                Result.failure(e)
            }
        } catch (e: Exception) {
            // Error lainnya (misal network error)
            Result.failure(e)
        }
    }
}