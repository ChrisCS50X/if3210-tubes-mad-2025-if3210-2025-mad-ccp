package com.example.purrytify.data.mapper

import android.content.Context
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.local.entity.SongEntity
import com.example.purrytify.data.model.Song

/**
 * Fungsi mapper untuk konversi antara entity database dan model domain.
 */

/**
 * Ubah entity dari database jadi objek domain yang lebih simple.
 * Ini ngurangin data yang gak perlu ditampilin ke UI.
 */
fun SongEntity.toDomainModel(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        filePath = filePath,
        duration = duration,
        isLiked = isLiked
    )
}

/**
 * Ubah objek domain jadi entity yang bisa disimpan di database.
 */
fun Song.toEntity(context: Context): SongEntity {
    val tokenManager = TokenManager(context)
    return SongEntity(
        id = id,
        userId = tokenManager.getEmail(),
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        filePath = filePath,
        duration = duration,
        isLiked = isLiked
    )
}