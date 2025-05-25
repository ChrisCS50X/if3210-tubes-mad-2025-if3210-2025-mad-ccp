package com.example.purrytify.data.repository

import android.util.Log
import com.example.purrytify.data.api.NetworkModule
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.ChartSong
import com.example.purrytify.utils.executeWithTokenRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChartRepository(private val tokenManager: TokenManager, private val songRepository: SongRepository) {

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

    suspend fun getTopMixes(countryCode: String): Result<List<ChartSong>> {
        return withContext(Dispatchers.IO) {
            try {
                // Dapatkan smart recommendations (maksimal 5)
                val smartRecommendations = songRepository.getSmartRecommendations(7)
                    .take(5)
                    .map { song ->
                        ChartSong(
                            id = song.id,
                            title = song.title,
                            artist = song.artist,
                            artwork = "${song.coverUrl}",
                            url = song.filePath,
                            duration = "${song.duration}",
                            country = "",
                            rank = 0,
                            createdAt = "",
                            updatedAt = ""
                        )
                    }

                // Set pasangan artist-title untuk menghindari duplikasi
                val existingArtistTitlePairs = smartRecommendations.map { it.artist to it.title }.toMutableSet()

                val currentCount = smartRecommendations.size
                val remainingNeeded = 10 - currentCount

                if (remainingNeeded <= 0) {
                    return@withContext Result.success(smartRecommendations)
                }

                var globalSongsNeeded = (remainingNeeded * 0.6).toInt().coerceAtLeast(1)
                val localSongsNeeded = remainingNeeded - globalSongsNeeded

                // Filter Local Songs
                val localSongs = try {
                    getCountryTopSongs(countryCode).getOrNull()
                        ?.filter { (it.artist to it.title) !in existingArtistTitlePairs }
                        ?.sortedBy { it.rank }
                        ?.take(localSongsNeeded)
                        ?: emptyList()
                } catch (e: Exception) {
                    Log.e("ChartRepository", "Error getting local songs for country $countryCode: ${e.message}")
                    emptyList()
                }

                Log.d("country code", countryCode)
                Log.d("Local Songs", localSongs.size.toString())

                // Tambahkan pasangan artist-title lokal ke set
                existingArtistTitlePairs.addAll(localSongs.map { it.artist to it.title })

                if (localSongs.size < localSongsNeeded) {
                    globalSongsNeeded += (localSongsNeeded - localSongs.size)
                }

                // Filter Global Songs
                val globalSongs = try {
                    getGlobalTopSongs().getOrNull()
                        ?.filter { (it.artist to it.title) !in existingArtistTitlePairs }
                        ?.sortedBy { it.rank }
                        ?.take(globalSongsNeeded)
                        ?: emptyList()
                } catch (e: Exception) {
                    Log.e("ChartRepository", "Error getting global songs: ${e.message}")
                    emptyList()
                }

                // Gabungkan semua lagu
                val finalRecommendations = (smartRecommendations + localSongs + globalSongs)
                    .take(10)
                    .mapIndexed { index, song ->
                        song.copy(rank = index + 1)
                    }
                Log.d("Final Recommendations", finalRecommendations.size.toString())

                return@withContext Result.success(finalRecommendations)// Kembalikan sebagai Result

            } catch (e: Exception) {
                Log.e("ChartRepository", "Error getting top mixes for country $countryCode: ${e.message}")
                // Fallback: return global songs jika ada error
                try {
                    val fallbackGlobalSongs = getGlobalTopSongs().getOrNull()?.shuffled()?.take(10) ?: emptyList()
                    Result.success(fallbackGlobalSongs)
                } catch (fallbackError: Exception) {
                    Log.e("ChartRepository", "Fallback also failed: ${fallbackError.message}")
                    Result.failure(fallbackError)
                }
            }
        }
    }
}