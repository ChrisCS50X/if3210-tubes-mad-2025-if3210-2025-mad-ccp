package com.example.purrytify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.example.purrytify.R

/**
 * Utility class for extracting colors from images that already loaded in ImageViews
 * This is more reliable than loading images from URLs directly
 */
object ColorUtils {
    private const val TAG = "ColorUtils"
    
    /**
     * Get dominant color from an ImageView that already has image loaded
     */
    fun getDominantColor(imageView: ImageView?, onColorReady: (Int) -> Unit) {
        try {
            // Default color if extraction fails
            val defaultColor = Color.parseColor("#121212") // Dark gray
            
            // If imageView is null, return default color
            if (imageView == null) {
                onColorReady(defaultColor)
                return
            }
            
            // Get the drawable from ImageView
            val drawable = imageView.drawable
            
            when (drawable) {
                // If it's a BitmapDrawable, we can extract color from it
                is BitmapDrawable -> {
                    val bitmap = drawable.bitmap
                    extractColorFromBitmap(bitmap, defaultColor, onColorReady)
                }
                
                // If it's a color drawable, use that color
                is ColorDrawable -> {
                    onColorReady(adjustColorForBackground(drawable.color))
                }
                
                // Otherwise use default color
                else -> {
                    onColorReady(defaultColor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dominant color: ${e.message}")
            onColorReady(Color.parseColor("#121212"))
        }
    }
    
    /**
     * Extract color from bitmap, with error handling
     */
    private fun extractColorFromBitmap(bitmap: Bitmap?, defaultColor: Int, onColorReady: (Int) -> Unit) {
        try {
            if (bitmap == null) {
                onColorReady(defaultColor)
                return
            }
            
            // Use Palette API to extract colors
            Palette.from(bitmap).generate { palette ->
                val color = when {
                    palette?.darkVibrantSwatch != null -> palette.darkVibrantSwatch!!.rgb
                    palette?.vibrantSwatch != null -> palette.vibrantSwatch!!.rgb
                    palette?.dominantSwatch != null -> palette.dominantSwatch!!.rgb
                    palette?.darkMutedSwatch != null -> palette.darkMutedSwatch!!.rgb
                    palette?.mutedSwatch != null -> palette.mutedSwatch!!.rgb
                    else -> defaultColor
                }
                
                onColorReady(adjustColorForBackground(color))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting colors: ${e.message}")
            onColorReady(defaultColor)
        }
    }
    
    /**
     * Adjust color to make it more suitable for a background
     */
    private fun adjustColorForBackground(color: Int): Int {
        return try {
            // Darken the color a bit and add some transparency
            val alpha = 180  // 0-255, with 255 being fully opaque
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            
            // Make it darker
            hsv[2] = hsv[2] * 0.6f
            
            // Create the final color
            Color.HSVToColor(alpha, hsv)
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting color: ${e.message}")
            Color.argb(180, 18, 18, 18)  // Fallback semi-transparent dark gray
        }
    }
}