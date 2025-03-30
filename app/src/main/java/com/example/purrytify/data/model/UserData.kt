package com.example.purrytify.data.model

data class UserProfile(
    val id: Int,
    val username: String,
    val email: String,
    val profilePhoto: String,
    val location: String, // country code
    val createdAt: String,
    val updatedAt: String
)

data class UserStats(
    val addedSongsCount: Int = 0,
    val likedSongsCount: Int = 0,
    val listenedSongsCount: Int = 0
)