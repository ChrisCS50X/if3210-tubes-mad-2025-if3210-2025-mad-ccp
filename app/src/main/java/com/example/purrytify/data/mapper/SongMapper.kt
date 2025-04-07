package com.example.purrytify.data.mapper

import com.example.purrytify.data.local.entity.SongEntity
import com.example.purrytify.data.model.Song

fun SongEntity.toDomainModel(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        coverUrl = coverPath,
        filePath = filePath,
        duration = duration,
        isLiked = isLiked
    )
}

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        coverPath = coverUrl,
        filePath = filePath,
        duration = duration,
        isLiked = isLiked
    )
}