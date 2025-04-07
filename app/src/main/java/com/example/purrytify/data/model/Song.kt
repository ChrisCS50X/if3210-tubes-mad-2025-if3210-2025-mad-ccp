package com.example.purrytify.data.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val filePath: String,
    val duration: Long,
    val isLiked: Boolean = false
)
