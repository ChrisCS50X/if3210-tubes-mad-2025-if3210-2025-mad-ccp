package com.example.purrytify.data.repository

import android.content.Context
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.local.dao.SongDao
import com.example.purrytify.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.purrytify.data.mapper.toDomainModel
import com.example.purrytify.data.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class SongRepository(private val songDao: SongDao, context: Context) {
    private val tokenManager = TokenManager(context)

    val allSongs = songDao.getAllSongsByUserId(tokenManager.getEmail()).map { entities ->
        entities.map { it.toDomainModel() }
    }

    val likedSongs = songDao.getLikedSongs().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun incrementPlayCount(songId: Long) {
        songDao.incrementPlayCount(songId)
    }

    suspend fun deleteSong(songId: Long) {
        songDao.deleteSongById(songId)
    }

    suspend fun toggleLikedStatus(songId: Long, isLiked: Boolean) {
        songDao.updateLikedStatus(songId, isLiked)
    }

    suspend fun insertSong(song: Song, context: Context): Long {
        return songDao.insertSong(song.toEntity(context))
    }

    suspend fun getNewSongs( userId: String?, limit: Int = 10): List<Song> {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getLatestSongsByUserId(userId,limit)
            songEntities.map { it.toDomainModel() }
        }
    }

    suspend fun getRecentlyPlayed(userId: String?, limit: Int = 10): List<Song> {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getRecentlyPlayedByUserId(userId, limit)
            songEntities.map { it.toDomainModel() }
        }
    }

    suspend fun updateLastPlayed(songId: Long) {
        withContext(Dispatchers.IO) {
            songDao.updateLastPlayed(songId, System.currentTimeMillis())
        }
    }

    suspend fun updateLikeStatus(songId: Long, isLiked: Boolean) {
        withContext(Dispatchers.IO) {
            songDao.updateLikedStatus(songId, isLiked)
        }
    }

    suspend fun getLikedStatusBySongId(songId: Long): Boolean {
        return songDao.getLikedStatusBySongId(songId)
    }

    suspend fun getLikedSongsCount(userId: String?): Flow<Int> {
        return songDao.getLikedSongsCountByUserId(userId)
    }

    suspend fun getOwnedSongsCount(userId: String?): Int {
        return songDao.getOwnedSongsCountByUserId(userId)
    }

    suspend fun getHeardSongsCount(userId: String?): Int {
        return songDao.getHeardSongsCountByUserId(userId)
    }

    suspend fun getAllSongsOrdered(): List<Song> {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getAllSongsByUserIdOrdered(tokenManager.getEmail())
            songEntities.map { it.toDomainModel() }
        }
    }

    suspend fun getNextSong(currentSongId: Long): Song? {
        return withContext(Dispatchers.IO) {
            val allSongs = getAllSongsOrdered()
            if (allSongs.isEmpty()) return@withContext null

            val currentIndex = allSongs.indexOfFirst { it.id == currentSongId }
            if (currentIndex == -1) return@withContext allSongs.firstOrNull()

            return@withContext if (currentIndex < allSongs.size - 1) {
                allSongs[currentIndex + 1]
            } else {
                // Loop back to the beginning
                allSongs.firstOrNull()
            }
        }
    }

    suspend fun getPreviousSong(currentSongId: Long): Song? {
        return withContext(Dispatchers.IO) {
            val allSongs = getAllSongsOrdered()
            if (allSongs.isEmpty()) return@withContext null

            val currentIndex = allSongs.indexOfFirst { it.id == currentSongId }
            if (currentIndex == -1) return@withContext allSongs.lastOrNull()

            return@withContext if (currentIndex > 0) {
                allSongs[currentIndex - 1]
            } else {
                // Loop back to the end
                allSongs.lastOrNull()
            }
        }
    }
}