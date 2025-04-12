package com.example.purrytify.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import com.example.purrytify.data.model.Song

object BackgroundColorProvider {
    private const val TAG = "BackgroundColorProvider"

    private val backgroundColors = listOf(
        Color.parseColor("#121212"),
        Color.parseColor("#1E2B3C"),
        Color.parseColor("#3C1E2B"),
        Color.parseColor("#2B3C1E"),
        Color.parseColor("#2B1E3C"),
        Color.parseColor("#3C2B1E")
    )

    fun getColorForSong(song: Song): Int {
        try {
            val hash = song.id.hashCode()
            val colorIndex = Math.abs(hash) % backgroundColors.size
            return backgroundColors[colorIndex]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting color for song: ${e.message}")
            return Color.parseColor("#121212")
        }
    }

    fun applyColorSafely(view: View?, color: Int) {
        try {
            if (view != null && view.context != null) {
                view.setBackgroundColor(color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying color: ${e.message}")
        }
    }

    fun createGradientBackground(context: Context?, song: Song): GradientDrawable {
        val songColor = getColorForSong(song)
        return createGradientDrawable(songColor)
    }

    fun createGradientDrawable(startColor: Int): GradientDrawable {
        val colors = intArrayOf(
            adjustAlpha(startColor, 0.9f),
            adjustAlpha(startColor, 0.8f),
            adjustAlpha(startColor, 0.6f),
            Color.BLACK
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