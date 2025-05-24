package com.example.purrytify.data.model

data class ChartSong(
    val id: Long,
    val title: String,
    val artist: String,
    val artwork: String,
    val url: String,
    val duration: String,
    val country: String,
    val rank: Int,
    val createdAt: String,
    val updatedAt: String
) {
    fun toSong(): Song {
        // Handle potential parsing errors in the duration
        val durationMs = try {
            val durationParts = duration.split(":")
            val minutes = durationParts[0].toLongOrNull() ?: 0
            val seconds = if (durationParts.size > 1) durationParts[1].toLongOrNull() ?: 0 else 0
            (minutes * 60 + seconds) * 1000
        } catch (e: Exception) {
            3 * 60 * 1000L
        }

        return Song(
            id = id,
            title = title ?: "Unknown Title",
            artist = artist ?: "Unknown Artist",
            coverUrl = artwork,
            filePath = url,
            duration = durationMs,
            isLiked = false
        )
    }
}