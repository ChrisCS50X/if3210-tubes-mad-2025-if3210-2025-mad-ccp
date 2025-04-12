package com.example.purrytify.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
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
    fun applyColorSafely(view: View?, color: Int) {
        try {
            if (view != null && view.context != null) {
                view.setBackgroundColor(color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying color: ${e.message}")
        }
    }
    
    /**
     * Create a gradient background that transitions from the song color to black
     * This creates a nice visual effect for the Now Playing screen
     */
    fun createGradientBackground(context: Context?, song: Song): GradientDrawable {
        val songColor = getColorForSong(song)
        return createGradientDrawable(songColor)
    }
    
    /**
     * Creates a gradient drawable that transitions from the given color to black
     */
    fun createGradientDrawable(startColor: Int): GradientDrawable {
        val colors = intArrayOf(
            adjustAlpha(startColor, 0.9f),  // Semi-transparent start color
            adjustAlpha(startColor, 0.8f),  // Less intense
            adjustAlpha(startColor, 0.6f),  // Keep fading
            Color.BLACK                     // End with pure black
        )
        
        return try {
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
            gradient.gradientType = GradientDrawable.LINEAR_GRADIENT
            gradient
        } catch (e: Exception) {
            Log.e(TAG, "Error creating gradient: ${e.message}")
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, 
                intArrayOf(Color.parseColor("#121212"), Color.BLACK)
            )
        }
    }
    
    /**
     * Adjust alpha component of the color
     */
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(
            alpha,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}