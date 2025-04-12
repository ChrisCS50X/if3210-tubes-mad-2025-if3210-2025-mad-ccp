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

/**
 * Repository untuk managemen data user dan autentikasi.
 * Bertanggung jawab untuk komunikasi dengan backend API dan
 * menyimpan data login ke penyimpanan lokal.
 */
class UserRepository(private val tokenManager: TokenManager) {

    // Ambil instance API service dari NetworkModule
    private val apiService = NetworkModule.apiService

    /**
     * Proses login user.
     * Panggil API login dan simpan token yang diterima.
     * @return Result<LoginResponse> berisi hasil sukses atau error
     */
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val loginRequest = LoginRequest(email, password)
                val response = apiService.login(loginRequest)

                // Simpan access token
                response.token?.let { tokenManager.saveToken(it) }

                // Simpan refresh token kalo ada
                response.refreshToken?.let { tokenManager.saveRefreshToken(it) }

                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Ambil data profile user yang login.
     * Pake helper executeWithTokenRefresh supaya otomatis refresh kalo token expired.
     */
    suspend fun getUserProfile(): Result<UserProfile> {
        if (!tokenManager.isLoggedIn()) {
            return Result.failure(IllegalStateException("Not logged in"))
        }

        return executeWithTokenRefresh(this) {
            val token = tokenManager.getToken()!!
            apiService.getUserProfile("Bearer $token")
        }
    }

    /**
     * Cek apakah token yang dimiliki masih valid.
     * Dipanggil pas awal app dibuka untuk verifikasi login.
     */
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

    /**
     * Minta token baru pake refresh token.
     * Dipanggil otomatis saat token expired waktu request API.
     */
    suspend fun refreshToken(): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val refreshToken = tokenManager.getRefreshToken() ?: return@withContext Result.failure(
                    IllegalStateException("No refresh token available")
                )

                val request = RefreshTokenRequest(refreshToken)
                val response = apiService.refreshToken(request)

                // Simpan token-token baru
                response.token?.let { tokenManager.saveToken(it) }
                response.refreshToken?.let { tokenManager.saveRefreshToken(it) }

                Result.success(response)
            } catch (e: Exception) {
                Log.e("UserRepository", "Failed to refresh token", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Proses logout user dengan hapus token dari penyimpanan.
     */
    fun logout() {
        tokenManager.clearTokens()
    }
}