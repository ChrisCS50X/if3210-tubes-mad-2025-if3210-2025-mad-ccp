package com.example.purrytify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purrytify.R

object PaletteUtils {
    
    private const val TAG = "PaletteUtils"

    fun extractDominantColorFromImageUrl(
        context: Context,
        imageUrl: String?,
        onColorExtracted: (Int) -> Unit
    ) {
        try {
            if (imageUrl.isNullOrEmpty()) {
                val defaultColor = try {
                    ContextCompat.getColor(context, R.color.colorPrimary)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting default color: ${e.message}")
                    Color.parseColor("#1DB954")
                }
                onColorExtracted(defaultColor)
                return
            }

            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                        try {
                            extractColorFromBitmap(bitmap) { color ->
                                onColorExtracted(color)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting color from bitmap: ${e.message}")
                            onColorExtracted(Color.parseColor("#1DB954"))
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        val fallbackColor = try {
                            ContextCompat.getColor(context, R.color.colorPrimary)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting fallback color: ${e.message}")
                            Color.parseColor("#1DB954")
                        }
                        onColorExtracted(fallbackColor)
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractDominantColorFromImageUrl: ${e.message}")
            onColorExtracted(Color.parseColor("#1DB954"))
        }
    }

    private fun extractColorFromBitmap(bitmap: Bitmap, onColorExtracted: (Int) -> Unit) {
        try {
            Palette.from(bitmap).generate { palette ->
                val color = when {
                    palette?.vibrantSwatch != null -> palette.vibrantSwatch!!.rgb
                    palette?.darkVibrantSwatch != null -> palette.darkVibrantSwatch!!.rgb
                    palette?.lightVibrantSwatch != null -> palette.lightVibrantSwatch!!.rgb
                    palette?.dominantSwatch != null -> palette.dominantSwatch!!.rgb
                    else -> Color.BLACK
                }

                val finalColor = adjustColorForBackground(color)
                onColorExtracted(finalColor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractColorFromBitmap: ${e.message}")
            onColorExtracted(Color.BLACK)
        }
    }

    private fun adjustColorForBackground(color: Int): Int {
        return try {
            val alpha = 200
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] = hsv[2] * 0.7f
            val darkerColor = Color.HSVToColor(hsv)
            Color.argb(
                alpha,
                Color.red(darkerColor),
                Color.green(darkerColor),
                Color.blue(darkerColor)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in adjustColorForBackground: ${e.message}")
            Color.argb(200, 0, 0, 0)
        }
    }
}