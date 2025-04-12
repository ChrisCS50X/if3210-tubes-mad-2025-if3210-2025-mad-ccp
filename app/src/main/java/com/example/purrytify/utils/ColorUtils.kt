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

object ColorUtils {
    private const val TAG = "ColorUtils"

    fun getDominantColor(imageView: ImageView?, onColorReady: (Int) -> Unit) {
        try {
            val defaultColor = Color.parseColor("#121212")

            if (imageView == null) {
                onColorReady(defaultColor)
                return
            }

            val drawable = imageView.drawable
            
            when (drawable) {
                is BitmapDrawable -> {
                    val bitmap = drawable.bitmap
                    extractColorFromBitmap(bitmap, defaultColor, onColorReady)
                }

                is ColorDrawable -> {
                    onColorReady(adjustColorForBackground(drawable.color))
                }

                else -> {
                    onColorReady(defaultColor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dominant color: ${e.message}")
            onColorReady(Color.parseColor("#121212"))
        }
    }

    private fun extractColorFromBitmap(bitmap: Bitmap?, defaultColor: Int, onColorReady: (Int) -> Unit) {
        try {
            if (bitmap == null) {
                onColorReady(defaultColor)
                return
            }

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

    private fun adjustColorForBackground(color: Int): Int {
        return try {
            val alpha = 180
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)

            hsv[2] = hsv[2] * 0.6f

            Color.HSVToColor(alpha, hsv)
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting color: ${e.message}")
            Color.argb(180, 18, 18, 18)
        }
    }
}