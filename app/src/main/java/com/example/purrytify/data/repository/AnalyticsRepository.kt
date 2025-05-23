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
}