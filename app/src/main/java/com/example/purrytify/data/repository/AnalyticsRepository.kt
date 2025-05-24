package com.example.purrytify.data.repository

import com.example.purrytify.data.local.dao.ListeningStatsDao
import com.example.purrytify.data.local.dao.ListeningStatsDao.ArtistWithDuration
import com.example.purrytify.data.local.dao.ListeningStatsDao.SongWithDuration
import com.example.purrytify.data.local.dao.ListeningStatsDao.SongWithStreak
import com.example.purrytify.data.local.entity.ListeningStatsEntity
import com.example.purrytify.data.model.ListeningAnalytics
import com.example.purrytify.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository for analytics data
 */
class AnalyticsRepository(private val listeningStatsDao: ListeningStatsDao) {

    /**
     * Record a song listening event
     * Accumulates listening time for the same song on the same day
     */
    suspend fun recordSongListening(userId: String, song: Song, durationMillis: Long) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        
        // Check if we already have a record for this song today
        val existingRecord = listeningStatsDao.getListeningStatsByDate(userId, song.id, today)
        
        if (existingRecord != null) {
            // Add to existing listening time
            val updatedDuration = existingRecord.listeningDurationMillis + durationMillis
            listeningStatsDao.updateListeningDuration(existingRecord.id, updatedDuration)
        } else {
            // Create new record
            val stats = ListeningStatsEntity(
                userId = userId,
                songId = song.id,
                songTitle = song.title,
                artist = song.artist,
                date = today,
                listeningDurationMillis = durationMillis
            )
            listeningStatsDao.insertListeningStats(stats)
        }
    }

    /**
     * Get monthly analytics for a user
     */
    fun getMonthlyAnalytics(userId: String, year: Int, month: Int): Flow<ListeningAnalytics> {
        // Format month to two digits (e.g., "05" for May)
        val monthFormatted = String.format("%02d", month)
        val yearStr = year.toString()
        
        val timeListenedFlow = listeningStatsDao.getTotalListeningTimeForMonth(userId, yearStr, monthFormatted)
        val topArtistFlow = listeningStatsDao.getTopArtistForMonth(userId, yearStr, monthFormatted)
        val topSongFlow = listeningStatsDao.getTopSongForMonth(userId, yearStr, monthFormatted)
        val streakSongFlow = listeningStatsDao.getLongestStreakSongForMonth(userId, yearStr, monthFormatted)
        
        return combine(
            timeListenedFlow,
            topArtistFlow,
            topSongFlow,
            streakSongFlow
        ) { timeListened, topArtist, topSong, streakInfo ->
            ListeningAnalytics(
                userId = userId,
                year = year,
                month = month,
                timeListenedMillis = timeListened ?: 0L,
                topArtist = topArtist?.artist,
                topSong = topSong?.songTitle,
                dayStreakSong = streakInfo?.songTitle,
                dayStreakCount = streakInfo?.streak ?: 0
            )
        }
    }
    
    /**
     * Get time listened by day for a specific month and year
     */
    suspend fun getTimeListenedByDay(userId: String, year: Int, month: Int, day: Int): Long {
        return withContext(Dispatchers.IO) {
            val yearStr = year.toString()
            val monthStr = String.format("%02d", month)
            val dayStr = String.format("%02d", day)
            val date = "$yearStr-$monthStr-$dayStr"
            listeningStatsDao.getTimeListenedByDay(userId, date) ?: 0L
        }
    }
    
    /**
     * Get top artists for a specific month and year
     */
    suspend fun getTopArtistsForMonth(userId: String, year: Int, month: Int, limit: Int): List<ArtistWithDuration> {
        return withContext(Dispatchers.IO) {
            val yearStr = year.toString()
            val monthStr = String.format("%02d", month)
            listeningStatsDao.getTopArtistsForMonth(userId, yearStr, monthStr, limit)
        }
    }
    
    /**
     * Get top songs for a specific month and year
     */
    suspend fun getTopSongsForMonth(userId: String, year: Int, month: Int, limit: Int): List<SongWithDuration> {
        return withContext(Dispatchers.IO) {
            val yearStr = year.toString()
            val monthStr = String.format("%02d", month)
            listeningStatsDao.getTopSongsForMonth(userId, yearStr, monthStr, limit)
        }
    }
    
    /**
     * Get song streaks for a specific month and year
     */
    suspend fun getSongStreaksForMonth(userId: String, year: Int, month: Int): List<SongWithStreak> {
        return withContext(Dispatchers.IO) {
            val yearStr = year.toString()
            val monthStr = String.format("%02d", month)
            listeningStatsDao.getSongStreaksForMonth(userId, yearStr, monthStr)
        }
    }

    /**
     * Clear all analytics data for a user
     */
    suspend fun clearUserData(userId: String) {
        listeningStatsDao.deleteAllUserStats(userId)
    }
    
    /**
     * Track consecutive day streaks for songs
     * This implementation will handle calculating real consecutive day streaks
     * Single day of listening is also counted as a streak of 1
     */
    suspend fun trackConsecutiveDayStreaks(userId: String) {
        withContext(Dispatchers.IO) {
            // Get the current month and year
            val now = LocalDate.now()
            val currentYear = now.year
            val currentMonth = now.monthValue
            val yearStr = currentYear.toString()
            val monthStr = String.format("%02d", currentMonth)
            
            // Get all songs the user has listened to this month
            val songData = listeningStatsDao.getAllSongListeningDates(userId, yearStr, monthStr)
            
            // Process each song to find consecutive day streaks
            songData.forEach { songListeningDates ->
                // Parse dates from concatenated string and sort
                val dates = songListeningDates.getDates()
                val sortedDates = dates.sorted()
                
                if (sortedDates.isEmpty()) return@forEach
                
                // Single day listening counts as a streak of 1
                var currentStreak = 1
                var maxStreak = 1
                
                // If there's only one date, we already have our streak of 1
                if (sortedDates.size > 1) {
                    for (i in 1 until sortedDates.size) {
                        val currentDate = LocalDate.parse(sortedDates[i])
                        val previousDate = LocalDate.parse(sortedDates[i-1])
                        
                        // Check if this is the next consecutive day
                        if (currentDate.minusDays(1) == previousDate) {
                            currentStreak++
                            maxStreak = maxOf(maxStreak, currentStreak)
                        } else {
                            // Streak broken, reset counter
                            currentStreak = 1
                        }
                    }
                }
                
                // Store this streak information if needed
                // This part would depend on how you want to persist streak information
                // For now, we're just calculating it on the fly
            }
        }
    }
    
    /**
     * Check if a user has listened to music today
     * @return true if the user has listened to any song today
     */
    suspend fun hasListenedToday(userId: String): Boolean {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return withContext(Dispatchers.IO) {
            val todayStats = listeningStatsDao.getListeningStatsForDay(userId, today)
            todayStats.isNotEmpty()
        }
    }
    
    /**
     * Calculate the current streak for a given song
     * This counts individual days and returns 1 for a single day
     */
    suspend fun getCurrentStreak(userId: String, songId: Long): Int {
        return withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            
            // Get all listening records for this song, ordered by date (most recent first)
            val records = listeningStatsDao.getListeningStatsBySongIdOrderedByDate(userId, songId)
            
            if (records.isEmpty()) return@withContext 0
            
            // Start with a streak of 1 for the most recent day
            var streak = 1
            var currentDate = LocalDate.parse(records[0].date)
            
            // If the most recent listening is not today or yesterday, streak is only 1
            if (currentDate != today && currentDate != today.minusDays(1)) {
                return@withContext 1
            }
            
            // Iterate through records to find consecutive days
            for (i in 1 until records.size) {
                val previousDate = LocalDate.parse(records[i].date)
                
                // Check if the previous record is the day before
                if (currentDate.minusDays(1) == previousDate) {
                    streak++
                    currentDate = previousDate
                } else {
                    // Break in streak
                    break
                }
            }
            
            streak
        }
    }
}