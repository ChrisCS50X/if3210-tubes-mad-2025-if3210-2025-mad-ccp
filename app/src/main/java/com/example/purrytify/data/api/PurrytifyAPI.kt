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

interface PurrytifyAPI {
    @POST("/api/login")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getUserProfile(@Header("Authorization") token: String): UserProfile

    @POST("/api/refresh-token")
    suspend fun refreshToken(@Body refreshTokenRequest: RefreshTokenRequest): LoginResponse

    @GET("/api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<Unit>
}