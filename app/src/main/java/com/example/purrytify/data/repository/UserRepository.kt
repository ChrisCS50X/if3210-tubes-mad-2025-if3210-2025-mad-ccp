package com.example.purrytify.data.repository

import com.example.purrytify.data.api.NetworkModule
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.LoginRequest
import com.example.purrytify.data.model.LoginResponse
import com.example.purrytify.data.model.RefreshTokenRequest
import com.example.purrytify.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.purrytify.utils.executeWithTokenRefresh
import android.util.Log
import com.example.purrytify.data.model.ApiResponse
import com.example.purrytify.data.model.EditProfile
import okhttp3.MultipartBody

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
     * Update profile user dengan data baru.
     * Pake helper executeWithTokenRefresh supaya otomatis refresh kalo token expired.
     */
    suspend fun updateProfile(parts: List<MultipartBody.Part>): Result<ApiResponse<EditProfile>> {
        if (!tokenManager.isLoggedIn()) {
            return Result.failure(IllegalStateException("Not logged in"))
        }

        // Logging parts
        parts.forEachIndexed { index, part ->
            val fieldName = part.headers?.get("Content-Disposition")
                ?.substringAfter("name=\"")
                ?.substringBefore("\"") ?: "Unknown Field"
            val contentType = part.body.contentType()?.toString() ?: "Unknown Content Type"
            val contentLength = part.body.contentLength()
            Log.d("UpdateProfile", "Part[$index] - Field: $fieldName, ContentType: $contentType, ContentLength: $contentLength")

            // Log konten kecil
            if (contentLength < 1024 * 1024) {
                val buffer = okio.Buffer()
                part.body.writeTo(buffer)
                Log.d("UpdateProfile", "Part[$index] - Content: ${buffer.readUtf8()}")
            } else {
                Log.d("UpdateProfile", "Part[$index] - Content is too large to log.")
            }
        }

        return executeWithTokenRefresh(this) {
            val token = "Bearer ${tokenManager.getToken()!!}"
            val response = apiService.updateProfile(token, parts)

            // Periksa apakah response sukses (kode 2xx)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty response body")
            } else {
                val errorBody = response.errorBody()?.string()

                if (errorBody?.contains("Profile updated successfully") == true) {
                    ApiResponse(success = true, message = "Profile updated successfully", data = null)
                } else {
                    throw Exception("Failed to update profile: $errorBody")
                }
            }
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