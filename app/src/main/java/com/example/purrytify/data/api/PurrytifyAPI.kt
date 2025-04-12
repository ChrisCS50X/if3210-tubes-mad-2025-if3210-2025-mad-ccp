package com.example.purrytify.data.api

import com.example.purrytify.data.model.LoginRequest
import com.example.purrytify.data.model.LoginResponse
import com.example.purrytify.data.model.UserProfile
import com.example.purrytify.data.model.RefreshTokenRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Interface untuk semua endpoint API Purrytify.
 * Setiap function mewakili satu endpoint dari server.
 */
interface PurrytifyAPI {
    /**
     * Endpoint login user
     * NOTE PENTING : Token ini akan disimpan dan digunakan untuk semua request ke server yang butuh otorisasi
     * @param loginRequest data username dan password
     * @return LoginResponse dengan token dan refresh token
     */
    @POST("/api/login")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse

    /**
     * Ambil data profile user yang sedang login
     * NOTE PENTING : Perlu token untuk authorization
     * @param token JWT token dari user yang login (format: "Bearer token")
     * @return UserProfile dengan info lengkap tentang user
     */
    @GET("/api/profile")
    suspend fun getUserProfile(@Header("Authorization") token: String): UserProfile

    /**
     * Refresh token yang sudah mau expired
     * @param refreshTokenRequest berisi refresh token yang valid
     * @return LoginResponse dengan token baru dan refresh token
     */
    @POST("/api/refresh-token")
    suspend fun refreshToken(@Body refreshTokenRequest: RefreshTokenRequest): LoginResponse

    /**
     * Verifikasi apakah token masih valid
     * Kalau valid, status code 200. Kalau tidak, error 401/403 (Di spek dibilangnya 403, tapi di QNA 401)
     * @param token JWT token yang mau diverifikasi (format: "Bearer token")
     * @return Response kosong tapi yang dilihat status codenya
     */
    @GET("/api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<Unit>
}