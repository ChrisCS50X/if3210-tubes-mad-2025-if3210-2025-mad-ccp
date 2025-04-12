package com.example.purrytify.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data class untuk kirim request login ke server.
 */
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Data class untuk nampung response dari server setelah login.
 * @SerializedName dipake buat mapping dari JSON ke object,
 * karena nama field di API beda sama yang kita pake di app.
 */
data class LoginResponse(
    @SerializedName("accessToken") val token: String?,
    val refreshToken: String?,
    val status: String? = null,
    val message: String? = null
)

/**
 * Data class buat request refresh token.
 * Dipake waktu access token expired tapi masih ada refresh token yang valid.
 */
data class RefreshTokenRequest(
    val refreshToken: String
)