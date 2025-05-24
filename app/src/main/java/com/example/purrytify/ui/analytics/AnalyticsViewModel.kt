package com.example.purrytify.ui.analytics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.ListeningAnalytics
import com.example.purrytify.data.repository.AnalyticsRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _currentAnalytics = MutableLiveData<ListeningAnalytics?>()
    val currentAnalytics: LiveData<ListeningAnalytics?> = _currentAnalytics

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _months = MutableLiveData<List<MonthYear>>()
    val months: LiveData<List<MonthYear>> = _months

    private val _exportResult = MutableLiveData<ExportResult>()
    val exportResult: LiveData<ExportResult> = _exportResult

    init {
        generateMonthsList()
    }

    private fun generateMonthsList() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        val monthsList = mutableListOf<MonthYear>()
        
        // Add current month and previous 11 months
        for (i in 0 until 12) {
            val targetCalendar = Calendar.getInstance()
            targetCalendar.set(Calendar.YEAR, currentYear)
            targetCalendar.set(Calendar.MONTH, currentMonth - 1)
            targetCalendar.add(Calendar.MONTH, -i)
            
            val month = targetCalendar.get(Calendar.MONTH) + 1
            val year = targetCalendar.get(Calendar.YEAR)
            
            monthsList.add(MonthYear(month, year))
        }
        
        _months.value = monthsList
    }

    fun loadAnalyticsForMonth(userId: String, monthYear: MonthYear) {
        _isLoading.value = true
        viewModelScope.launch {
            analyticsRepository.getMonthlyAnalytics(userId, monthYear.year, monthYear.month)
                .collect { analytics ->
                    _currentAnalytics.postValue(analytics)
                    _isLoading.postValue(false)
                }
        }
    }

    fun exportAnalyticsToCsv(userId: String, directory: File): File? {
        val analytics = _currentAnalytics.value ?: return null
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "purrytify_analytics_${analytics.year}_${analytics.month}_$timestamp.csv"
        val file = File(directory, filename)
        
        try {
            file.bufferedWriter().use { writer ->
                writer.write("Purrytify Analytics for ${getMonthName(analytics.month)} ${analytics.year}\n")
                writer.write("User: $userId\n\n")
                writer.write("Metric,Value\n")
                writer.write("Time Listened,${analytics.getFormattedTimeListened()}\n")
                writer.write("Top Artist,${analytics.topArtist ?: "None"}\n")
                writer.write("Top Song,${analytics.topSong ?: "None"}\n")
                writer.write("Day Streak,${if (analytics.dayStreakCount > 0) "${analytics.dayStreakSong} (${analytics.dayStreakCount} days)" else "None"}\n")
            }
            _exportResult.postValue(ExportResult.Success(file))
            return file
        } catch (e: Exception) {
            _exportResult.postValue(ExportResult.Error(e.message ?: "Unknown error"))
            return null
        }
    }
    
    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> "Unknown"
        }
    }
}

data class MonthYear(val month: Int, val year: Int) {
    override fun toString(): String {
        val monthName = when (month) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> "Unknown"
        }
        return "$monthName $year"
    }
}

sealed class ExportResult {
    data class Success(val file: File) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

class AnalyticsViewModelFactory(
    private val analyticsRepository: AnalyticsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(analyticsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
