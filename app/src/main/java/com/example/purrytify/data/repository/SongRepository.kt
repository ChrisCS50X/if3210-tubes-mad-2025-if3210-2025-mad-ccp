package com.example.purrytify.data.repository

import com.example.purrytify.data.local.dao.SongDao
import com.example.purrytify.data.local.entity.SongEntity
import com.example.purrytify.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.purrytify.data.mapper.toDomainModel
import com.example.purrytify.data.mapper.toEntity

class SongRepository(private val songDao: SongDao) {
    val allSongs = songDao.getAllSongs().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val likedSongs = songDao.getLikedSongs().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun incrementPlayCount(songId: Long) {
        songDao.incrementPlayCount(songId)
    }

    suspend fun toggleLikedStatus(songId: Long, isLiked: Boolean) {
        songDao.updateLikedStatus(songId, isLiked)
    }

    suspend fun insertSong(song: Song): Long {
        return songDao.insertSong(song.toEntity())
    }
}