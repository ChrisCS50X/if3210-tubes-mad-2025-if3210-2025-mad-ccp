package com.example.purrytify.ui.charts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.ChartSong
import com.example.purrytify.data.repository.ChartRepository
import kotlinx.coroutines.launch

sealed class ChartState {
    object Loading : ChartState()
    data class Success(val songs: List<ChartSong>) : ChartState()
    data class Error(val message: String) : ChartState()
}

class ChartDetailViewModel(
    private val chartRepository: ChartRepository,
    private val chartType: String,
    private val countryCode: String
) : ViewModel() {

    private val _chartState = MutableLiveData<ChartState>()
    val chartState: LiveData<ChartState> = _chartState

    fun loadChartSongs() {
        _chartState.value = ChartState.Loading

        viewModelScope.launch {
            try {
                val result = when (chartType) {
                    "global" -> chartRepository.getGlobalTopSongs()
                    "local" -> chartRepository.getCountryTopSongs(countryCode)
                    else -> Result.failure(IllegalArgumentException("Invalid chart type"))
                }

                if (result.isSuccess) {
                    _chartState.value = ChartState.Success(result.getOrNull() ?: emptyList())
                } else {
                    _chartState.value = ChartState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load chart songs"
                    )
                }
            } catch (e: Exception) {
                _chartState.value = ChartState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}

class ChartDetailViewModelFactory(
    private val chartRepository: ChartRepository,
    private val chartType: String,
    private val countryCode: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChartDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChartDetailViewModel(chartRepository, chartType, countryCode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}