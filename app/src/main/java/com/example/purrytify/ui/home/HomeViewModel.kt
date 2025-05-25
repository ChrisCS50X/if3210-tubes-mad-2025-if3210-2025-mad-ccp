package com.example.purrytify.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.model.UserProfile
import com.example.purrytify.data.repository.ChartRepository
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.data.repository.UserRepository
import com.example.purrytify.ui.adapter.ChartItem
import kotlinx.coroutines.launch

class HomeViewModel(
    private val songRepository: SongRepository,
    private val context: Context,
    private val userRepository: UserRepository,
    private val chartRepository: ChartRepository
) : ViewModel() {

    private val _newSongs = MutableLiveData<List<Song>>()
    val newSongs: LiveData<List<Song>> = _newSongs

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userProfile = MutableLiveData<UserProfile?>()
    private val _chartItems = MutableLiveData<List<ChartItem>>()
    val chartItems: LiveData<List<ChartItem>> = _chartItems

    private val _chartsLoading = MutableLiveData<Boolean>()
    val chartsLoading: LiveData<Boolean> = _chartsLoading

    private val countryNameMap = mapOf(
        "ID" to "Indonesia",
        "MY" to "Malaysia",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CH" to "Switzerland",
        "DE" to "Germany",
        "BR" to "Brazil"
    )

    // Update the loadHomeData() method:
    fun loadHomeData() {
        _isLoading.value = true
        _chartsLoading.value = true

        viewModelScope.launch {
            try {
                val tokenManager = TokenManager(context)
                _newSongs.value = songRepository.getNewSongs(tokenManager.getEmail(), 10)
                _recentlyPlayed.value = songRepository.getRecentlyPlayed(tokenManager.getEmail(), 10)

                // Load user profile for country code
                val profileResult = userRepository.getUserProfile()
                val countryCode = if (profileResult.isSuccess) {
                    _userProfile.value = profileResult.getOrNull()
                    _userProfile.value?.location ?: "ID"
                } else {
                    "ID"
                }

                // Create chart items with dynamic images
                val chartItems = mutableListOf<ChartItem>()

                // Global chart
                chartItems.add(
                    ChartItem(
                        id = "global",
                        title = "Top 50 Global",
                        imageResId = com.example.purrytify.R.drawable.global_chart_cover,
                        type = "global"
                    )
                )

                // Local chart with country-specific image
                val localChartImageResId = when (countryCode) {
                    "ID" -> com.example.purrytify.R.drawable.id_chart_cover
                    "MY" -> com.example.purrytify.R.drawable.my_chart_cover
                    "US" -> com.example.purrytify.R.drawable.us_chart_cover
                    "GB" -> com.example.purrytify.R.drawable.gb_chart_cover
                    "CH" -> com.example.purrytify.R.drawable.ch_chart_cover
                    "DE" -> com.example.purrytify.R.drawable.de_chart_cover
                    "BR" -> com.example.purrytify.R.drawable.br_chart_cover
                    else -> com.example.purrytify.R.drawable.unknown_chart_cover
                }

                val countryName = countryNameMap[countryCode] ?: countryCode
                chartItems.add(
                    ChartItem(
                        id = "local_$countryCode",
                        title = "Top 10 $countryName",
                        imageResId = localChartImageResId,
                        type = "local"
                    )
                )

                // Your Top Mixes
                chartItems.add(
                    ChartItem(
                        id = "your_${countryCode}_top_songs",
                        title = "Top Mixes",
                        imageResId = com.example.purrytify.R.drawable.your_top_song,
                        type = "yours"
                    )
                )

                _chartItems.value = chartItems
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
                _chartsLoading.value = false
            }
        }
    }
}