package com.example.purrytify.data.repository

import com.example.purrytify.data.api.NetworkModule
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.ChartSong
import com.example.purrytify.utils.executeWithTokenRefresh

class ChartRepository(private val tokenManager: TokenManager) {

    private val apiService = NetworkModule.apiService

    suspend fun getGlobalTopSongs(): Result<List<ChartSong>> {
        return if (!tokenManager.isLoggedIn()) {
            Result.failure(IllegalStateException("Not logged in"))
        } else {
            executeWithTokenRefresh<List<ChartSong>>(UserRepository(tokenManager)) {
                val token = tokenManager.getToken()!!
                apiService.getGlobalTopSongs("Bearer $token")
            }
        }
    }

    suspend fun getCountryTopSongs(countryCode: String): Result<List<ChartSong>> {
        return if (!tokenManager.isLoggedIn()) {
            Result.failure(IllegalStateException("Not logged in"))
        } else {
            executeWithTokenRefresh<List<ChartSong>>(UserRepository(tokenManager)) {
                val token = tokenManager.getToken()!!
                apiService.getCountryTopSongs("Bearer $token", countryCode)
            }
        }
    }
}