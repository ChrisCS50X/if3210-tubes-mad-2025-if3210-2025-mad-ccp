package com.example.purrytify.util

import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

suspend fun <T> executeWithTokenRefresh(
    userRepository: UserRepository,
    apiCall: suspend () -> T
): Result<T> {
    return withContext(Dispatchers.IO) {
        try {
            // First attempt
            val result = apiCall()
            Result.success(result)
        } catch (e: HttpException) {
            // If 401/403, try refreshing token and retry
            if (e.code() == 401 || e.code() == 403) {
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isSuccess) {
                    try {
                        // Retry with new token
                        val result = apiCall()
                        Result.success(result)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                } else {
                    Result.failure(Exception("Authentication failed. Please log in again."))
                }
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}