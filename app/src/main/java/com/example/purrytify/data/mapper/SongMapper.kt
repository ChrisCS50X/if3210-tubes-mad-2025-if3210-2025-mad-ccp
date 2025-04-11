package com.example.purrytify.data.mapper

import android.content.Context
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.local.entity.SongEntity
import com.example.purrytify.data.model.Song

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