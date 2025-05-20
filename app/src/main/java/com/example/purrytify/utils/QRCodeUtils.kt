package com.example.purrytify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

/**
 * Utility class for generating QR codes from deep links
 */
object QRCodeUtils {

    /**
     * Generate a QR code bitmap from a deep link URI
     *
     * @param uri The deep link URI to encode
     * @param width The width of the QR code in pixels
     * @param height The height of the QR code in pixels
     * @return A Bitmap containing the QR code
     */
    fun generateQRCode(uri: Uri, width: Int = 512, height: Int = 512): Bitmap? {
        return try {
            Log.d("QRCodeUtils", "Generating QR code for URI: $uri")
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.MARGIN] = 2 // Smaller margin for cleaner look
            hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H // Higher error correction for better readability
            
            val bitMatrix = MultiFormatWriter().encode(
                uri.toString(),
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
            )
            
            createBitmap(bitMatrix)
        } catch (e: WriterException) {
            Log.e("QRCodeUtils", "Error generating QR code: ${e.message}")
            null
        }
    }
    
    /**
     * Generate a QR code bitmap with song information displayed below it
     * 
     * @param uri The deep link URI to encode
     * @param songTitle The title of the song
     * @param artistName The name of the artist
     * @param width The width of the QR code in pixels
     * @param height The height of the QR code in pixels
     * @return A Bitmap containing the QR code and song information
     */
    fun generateQRCodeWithInfo(
        uri: Uri,
        songTitle: String,
        artistName: String,
        width: Int = 512,
        height: Int = 650
    ): Bitmap? {
        try {
            val padding = 40
            val qrCodeSize = width.coerceAtMost(height - 150) // Leave space for text
            val qrCode = generateQRCode(uri, qrCodeSize - padding*2, qrCodeSize - padding*2) ?: return null
            
            // Create a new bitmap with space for the text
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            
            // White background
            canvas.drawColor(Color.WHITE)
            
            // Draw rounded rectangle for QR code background
            val bgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                setShadowLayer(8f, 0f, 2f, Color.parseColor("#20000000"))
                isAntiAlias = true
            }
            canvas.drawRoundRect(padding.toFloat(), padding.toFloat(), 
                (width - padding).toFloat(), (qrCodeSize - padding).toFloat(), 
                16f, 16f, bgPaint)
            
            // Draw QR code centered
            val left = (width - qrCode.width) / 2
            canvas.drawBitmap(qrCode, left.toFloat(), (padding * 1.5).toFloat(), null)
            
            // Draw song title
            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(
                truncateText(songTitle, width - 60, titlePaint), 
                width / 2f, 
                qrCodeSize + 50f, 
                titlePaint
            )
            
            // Draw artist name
            val artistPaint = Paint().apply {
                color = Color.GRAY
                textSize = 24f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(
                truncateText(artistName, width - 80, artistPaint), 
                width / 2f, 
                qrCodeSize + 90f, 
                artistPaint
            )
            
            // Draw small "Scan to listen" text at the bottom
            val scanPaint = Paint().apply {
                color = Color.parseColor("#1DB954") // Spotify green
                textSize = 18f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText(
                "Scan to listen on Purrytify", 
                width / 2f, 
                qrCodeSize + 130f, 
                scanPaint
            )
            
            return result
        } catch (e: Exception) {
            Log.e("QRCodeUtils", "Error generating QR code with info: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Save a QR code bitmap to a file in the app's cache directory
     *
     * @param context The application context
     * @param bitmap The QR code bitmap to save
     * @param songId The ID of the song for filename uniqueness
     * @return Uri to the saved file, or null if saving failed
     */
    fun saveQRCodeToCache(context: Context, bitmap: Bitmap, songId: Long): Uri? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, "qrcode_song_$songId.png")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e("QRCodeUtils", "Error saving QR code to cache: ${e.message}")
            null
        }
    }
    
    /**
     * Create a bitmap from a bit matrix
     */
    private fun createBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    /**
     * Truncate text to fit within a specified width
     */
    private fun truncateText(text: String, maxWidth: Int, paint: Paint): String {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        
        if (bounds.width() <= maxWidth) {
            return text
        }
        
        var truncated = text
        while (truncated.isNotEmpty()) {
            truncated = truncated.substring(0, truncated.length - 1)
            paint.getTextBounds(truncated + "...", 0, truncated.length + 3, bounds)
            if (bounds.width() <= maxWidth) {
                return truncated + "..."
            }
        }
        
        return "..."
    }
}
