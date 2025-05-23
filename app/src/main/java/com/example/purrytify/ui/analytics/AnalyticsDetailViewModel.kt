package com.example.purrytify.ui.analytics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.local.dao.ListeningStatsDao.ArtistWithDuration
import com.example.purrytify.data.local.dao.ListeningStatsDao.SongWithDuration
import com.example.purrytify.data.local.dao.ListeningStatsDao.SongWithStreak
import com.example.purrytify.data.repository.AnalyticsRepository
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for all analytics detail screens
 */
class AnalyticsDetailViewModel(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _timeListenedData = MutableLiveData<List<Pair<Int, Long>>>()
    val timeListenedData: LiveData<List<Pair<Int, Long>>> = _timeListenedData
    
    private val _topArtistsData = MutableLiveData<List<ArtistWithDuration>>()
    val topArtistsData: LiveData<List<ArtistWithDuration>> = _topArtistsData
    
    private val _topSongsData = MutableLiveData<List<SongWithDuration>>()
    val topSongsData: LiveData<List<SongWithDuration>> = _topSongsData
    
    private val _streakData = MutableLiveData<List<SongWithStreak>>()
    val streakData: LiveData<List<SongWithStreak>> = _streakData
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    /**
     * Load time listened data per day for a specific month
     */
    fun loadTimeListenedByDay(userId: String, year: Int, month: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val daysInMonth = getDaysInMonth(year, month)
                val result = mutableListOf<Pair<Int, Long>>()
                
                for (day in 1..daysInMonth) {
                    val duration = analyticsRepository.getTimeListenedByDay(userId, year, month, day)
                    result.add(Pair(day, duration))
                }
                
                _timeListenedData.value = result
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load top artists for a specific month
     */
    fun loadTopArtists(userId: String, year: Int, month: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val artists = analyticsRepository.getTopArtistsForMonth(userId, year, month, limit = 10)
                _topArtistsData.value = artists
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load top songs for a specific month
     */
    fun loadTopSongs(userId: String, year: Int, month: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val songs = analyticsRepository.getTopSongsForMonth(userId, year, month, limit = 10)
                _topSongsData.value = songs
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load streak data for a specific month
     */
    fun loadStreakData(userId: String, year: Int, month: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val streaks = analyticsRepository.getSongStreaksForMonth(userId, year, month)
                _streakData.value = streaks
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun getDaysInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1) // Month is 0-based in Calendar
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}

/**
 * Factory for creating AnalyticsDetailViewModel
 */
class AnalyticsDetailViewModelFactory(
    private val analyticsRepository: AnalyticsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsDetailViewModel(analyticsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
