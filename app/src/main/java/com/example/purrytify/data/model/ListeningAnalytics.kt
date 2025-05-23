package com.example.purrytify.data.model

/**
 * Model class for user listening analytics
 * Contains data for the Sound Capsule feature
 */
data class ListeningAnalytics(
    val userId: String,
    val year: Int,
    val month: Int,
    val timeListenedMillis: Long = 0,
    val topArtist: String? = null,
    val topSong: String? = null,
    val dayStreakSong: String? = null,
    val dayStreakCount: Int = 0
) {
    /**
     * Formats the time listened in a human-readable format (hours and minutes)
     */
    fun getFormattedTimeListened(): String {
        val hours = timeListenedMillis / (1000 * 60 * 60)
        val minutes = (timeListenedMillis % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "$hours h $minutes min"
            else -> "$minutes min"
        }
    }
    
    /**
     * Returns true if analytics has actual data
     */
    fun hasData(): Boolean {
        return timeListenedMillis > 0 || topArtist != null || topSong != null || dayStreakSong != null
    }
}