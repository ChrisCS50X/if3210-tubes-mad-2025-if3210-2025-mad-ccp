package com.example.purrytify.data.local.dao

import androidx.room.*
import com.example.purrytify.data.local.entity.ListeningStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningStats(stats: ListeningStatsEntity): Long
    
    @Query("SELECT * FROM listening_stats WHERE userId = :userId AND songId = :songId AND date = :date LIMIT 1")
    suspend fun getListeningStatsByDate(userId: String, songId: Long, date: String): ListeningStatsEntity?
    
    @Query("UPDATE listening_stats SET listeningDurationMillis = :duration WHERE id = :id")
    suspend fun updateListeningDuration(id: Long, duration: Long)

    @Query("SELECT * FROM listening_stats WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllUserListeningStats(userId: String): Flow<List<ListeningStatsEntity>>

    @Query("SELECT SUM(listeningDurationMillis) FROM listening_stats WHERE userId = :userId AND strftime('%Y', date) = :year AND strftime('%m', date) = :month")
    fun getTotalListeningTimeForMonth(userId: String, year: String, month: String): Flow<Long?>

    // Use data class to represent query result
    data class ArtistWithDuration(val artist: String, val total: Long)
    
    @Query("""
        SELECT artist, SUM(listeningDurationMillis) as total 
        FROM listening_stats 
        WHERE userId = :userId 
        AND strftime('%Y', date) = :year 
        AND strftime('%m', date) = :month 
        GROUP BY artist 
        ORDER BY total DESC 
        LIMIT 1
    """)
    fun getTopArtistForMonth(userId: String, year: String, month: String): Flow<ArtistWithDuration?>

    // Use data class to represent query result
    data class SongWithDuration(val songTitle: String, val total: Long)
    
    @Query("""
        SELECT songTitle, SUM(listeningDurationMillis) as total 
        FROM listening_stats 
        WHERE userId = :userId 
        AND strftime('%Y', date) = :year 
        AND strftime('%m', date) = :month 
        GROUP BY songTitle 
        ORDER BY total DESC 
        LIMIT 1
    """)
    fun getTopSongForMonth(userId: String, year: String, month: String): Flow<SongWithDuration?>

    // Use data class to represent query result
    data class SongWithStreak(val songTitle: String, val streak: Int)
    
    // Simplified query for detecting streaks without window functions
    @Query("""
        SELECT s1.songTitle as songTitle, COUNT(DISTINCT s1.date) as streak
        FROM listening_stats s1
        WHERE s1.userId = :userId 
        AND strftime('%Y', s1.date) = :year 
        AND strftime('%m', s1.date) = :month
        GROUP BY s1.songId
        HAVING COUNT(DISTINCT s1.date) >= 2
        ORDER BY COUNT(DISTINCT s1.date) DESC
        LIMIT 1
    """)
    fun getLongestStreakSongForMonth(userId: String, year: String, month: String): Flow<SongWithStreak?>

    @Query("DELETE FROM listening_stats WHERE userId = :userId")
    suspend fun deleteAllUserStats(userId: String)
    
    // Get time listened for a specific day
    @Query("SELECT SUM(listeningDurationMillis) FROM listening_stats WHERE userId = :userId AND date = :date")
    suspend fun getTimeListenedByDay(userId: String, date: String): Long?
    
    // Get top artists for a month with limit
    @Query("""
        SELECT artist, SUM(listeningDurationMillis) as total 
        FROM listening_stats 
        WHERE userId = :userId 
        AND strftime('%Y', date) = :year 
        AND strftime('%m', date) = :month 
        GROUP BY artist 
        ORDER BY total DESC 
        LIMIT :limit
    """)
    suspend fun getTopArtistsForMonth(userId: String, year: String, month: String, limit: Int): List<ArtistWithDuration>
    
    // Get top songs for a month with limit
    @Query("""
        SELECT songTitle, SUM(listeningDurationMillis) as total 
        FROM listening_stats 
        WHERE userId = :userId 
        AND strftime('%Y', date) = :year 
        AND strftime('%m', date) = :month 
        GROUP BY songTitle 
        ORDER BY total DESC 
        LIMIT :limit
    """)
    suspend fun getTopSongsForMonth(userId: String, year: String, month: String, limit: Int): List<SongWithDuration>
    
    // Get all songs with streak data for a month
    @Query("""
        SELECT s1.songTitle as songTitle, COUNT(DISTINCT s1.date) as streak
        FROM listening_stats s1
        WHERE s1.userId = :userId 
        AND strftime('%Y', s1.date) = :year 
        AND strftime('%m', s1.date) = :month
        GROUP BY s1.songId
        HAVING COUNT(DISTINCT s1.date) >= 2
        ORDER BY COUNT(DISTINCT s1.date) DESC
    """)
    suspend fun getSongStreaksForMonth(userId: String, year: String, month: String): List<SongWithStreak>
}