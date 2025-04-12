package com.example.purrytify.utils

import android.graphics.Color
import android.util.Log
import com.example.purrytify.data.model.Song

/**
 * A simple utility class to provide background colors for music players
 * This avoids complex image processing that might cause crashes
 */
object BackgroundColorProvider {
    private const val TAG = "BackgroundColorProvider"
    
    // Predefined attractive dark background colors suitable for player UI
    private val backgroundColors = listOf(
        Color.parseColor("#121212"), // Dark gray
        Color.parseColor("#1E2B3C"), // Dark blue
        Color.parseColor("#3C1E2B"), // Dark pink
        Color.parseColor("#2B3C1E"), // Dark green
        Color.parseColor("#2B1E3C"), // Dark purple
        Color.parseColor("#3C2B1E")  // Dark brown
    )
    
    /**
     * Get a color based on song. This approach avoids any risk of crashing
     * due to image loading or processing.
     */
    fun getColorForSong(song: Song): Int {
        try {
            // Use the song ID to consistently get the same color for a specific song
            val hash = song.id.hashCode()
            val colorIndex = Math.abs(hash) % backgroundColors.size
            return backgroundColors[colorIndex]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting color for song: ${e.message}")
            return Color.parseColor("#121212") // Default dark background
        }
    }
    
    /**
     * Apply a color to any view safely
     */
    fun applyColorSafely(view: android.view.View?, color: Int) {
        try {
            if (view != null && view.context != null) {
                view.setBackgroundColor(color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying color: ${e.message}")
        }
    }
}