package com.example.purrytify.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.purrytify.data.local.entity.SongEntity

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY title ASC")
    fun getAllSongsByUserId(userId: String?): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY title ASC")
    fun getLikedSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
    suspend fun updateLikedStatus(songId: Long, isLiked: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long)

    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY id DESC LIMIT :limit")
    suspend fun getLatestSongsByUserId(userId: String?, limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE userId = :userId AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayedByUserId(userId: String?, limit: Int): List<SongEntity>

    @Query("UPDATE songs SET lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun updateLastPlayed(songId: Long, timestamp: Long)

    @Query("SELECT isLiked FROM songs WHERE id = :songId")
    suspend fun getLikedStatusBySongId(songId: Long): Boolean

    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId AND isLiked = 1")
    fun getLikedSongsCountByUserId(userId: String?): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId")
    suspend fun getOwnedSongsCountByUserId(userId: String?): Int

    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId AND playCount > 0")
    suspend fun getHeardSongsCountByUserId(userId: String?): Int

    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY title ASC")
    suspend fun getAllSongsByUserIdOrdered(userId: String?): List<SongEntity>
}