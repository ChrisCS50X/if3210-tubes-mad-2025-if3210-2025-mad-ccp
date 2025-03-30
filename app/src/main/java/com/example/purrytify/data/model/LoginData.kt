package com.example.purrytify.data.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String?,
    val refreshToken: String?,
    val status: String? = null,
    val message: String? = null
)

data class RefreshTokenRequest(
    val refreshToken: String
)

