package com.example.purrytify.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("accessToken") val token: String?,
    val refreshToken: String?,
    val status: String? = null,
    val message: String? = null
)

data class RefreshTokenRequest(
    val refreshToken: String
)