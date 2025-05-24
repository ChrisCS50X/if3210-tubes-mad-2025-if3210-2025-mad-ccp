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
                if (profileResult.isSuccess) {
                    _userProfile.value = profileResult.getOrNull()
                }

                // Create chart items
                val chartItems = mutableListOf<ChartItem>()
                chartItems.add(
                    ChartItem(
                        id = "global",
                        title = "Top 50 Global",
                        imageResId = com.example.purrytify.R.drawable.global_chart_cover,
                        type = "global"
                    )
                )

                // Add local chart if country is available
                val countryCode = _userProfile.value?.location ?: "ID" // Default to ID if not available
                chartItems.add(
                    ChartItem(
                        id = "local_$countryCode",
                        title = "Top 10 $countryCode",
                        imageResId = com.example.purrytify.R.drawable.local_chart_cover,
                        type = "local"
                    )
                )

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