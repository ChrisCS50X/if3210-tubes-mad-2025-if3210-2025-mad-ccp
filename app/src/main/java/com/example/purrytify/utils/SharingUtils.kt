package com.example.purrytify.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentManager
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.ui.dialog.ShareOptionsDialog
import java.io.File

/**
 * Utility class for handling song sharing and deep link generation/parsing
 */
object SharingUtils {
    private const val SCHEME = "purrytify" // Reverted to purrytify
    private const val HOST_APP = "song" // Reverted to song (acts as host for this scheme)

    /**
     * Generate a deep link URI for a song
     * @param songId The ID of the song to link to
     * @return A URI in the format purrytify://song/{songId}
     */
    fun createSongDeepLink(songId: Long): Uri {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST_APP) // Using "song" as authority/host
            .appendPath(songId.toString())
            .build()
    }

    /**
     * Extract song ID from a deep link URI
     * @param uri The deep link URI
     * @return The song ID, or null if the URI is not a valid song deep link
     */
    fun extractSongIdFromDeepLink(uri: Uri): Long? {
        // For purrytify://song/123
        // scheme = "purrytify"
        // host (authority) = "song"
        // pathSegments = ["123"]
        return if (uri.scheme == SCHEME && uri.host == HOST_APP && uri.pathSegments.size == 1) {
            uri.lastPathSegment?.toLongOrNull()
        } else {
            null
        }
    }

    /**
     * Show sharing options dialog for a song
     * @param fragmentManager The fragment manager to use for showing the dialog
     * @param song The song to share
     */
    fun showShareOptions(fragmentManager: FragmentManager, song: Song) {
        val dialog = ShareOptionsDialog.newInstance(song)
        dialog.show(fragmentManager, "share_options_dialog")
    }

    /**
     * Share a song via a shareable link
     * @param context Application context
     * @param song The song to share
     */
    fun shareSong(context: Context, song: Song) {
        // Use the HTML file sharing approach
        shareSongAsHtmlFile(context, song)
    }
    
    /**
     * Check if a song can be shared (only server songs can be shared, not local songs)
     * @param song The song to check
     * @return True if the song can be shared, false otherwise
     */
    fun canShareSong(song: Song): Boolean {
        // Songs that can be shared must have an ID and should not be local files
        // In Purrytify, we assume a song is from the server if its filePath starts with http
        return song.filePath.startsWith("http")
    }

    fun shareSongAsHtmlFile(context: Context, song: Song) {
        val deepLinkUri = createSongDeepLink(song.id)

        try {
            // Create HTML content
            val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${song.title} - Purrytify</title>
            <style>
                body { 
                    font-family: Arial, sans-serif; 
                    background-color: #121212; 
                    color: white; 
                    text-align: center; 
                    padding: 30px 20px;
                }
                .card {
                    background: linear-gradient(45deg, #1DB954, #169b45);
                    border-radius: 12px;
                    padding: 25px;
                    max-width: 400px;
                    margin: 0 auto;
                }
                .btn {
                    display: block;
                    background: white;
                    color: #1DB954;
                    padding: 15px 30px;
                    border-radius: 50px;
                    text-decoration: none;
                    font-weight: bold;
                    font-size: 18px;
                    margin: 20px auto;
                    text-align: center;
                    width: 80%;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <h2>${song.title}</h2>
                <p>by ${song.artist}</p>
                <a href="${deepLinkUri}" class="btn">Listen in Purrytify</a>
            </div>
        </body>
        </html>
        """.trimIndent()

            // Create file in external cache directory for better compatibility
            val cachePath = File(context.externalCacheDir, "shared")
            if (!cachePath.exists()) cachePath.mkdirs()

            val htmlFile = File(cachePath, "purrytify_song_${song.id}.html")
            htmlFile.writeText(htmlContent)

            // Get content URI through FileProvider
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                htmlFile
            )

            // Share as general MIME type for better compatibility
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/*"  // More generic MIME type
                putExtra(Intent.EXTRA_SUBJECT, "${song.title} - Listen on Purrytify")
                putExtra(Intent.EXTRA_TEXT, "I'm sharing '${song.title}' by ${song.artist}. Open the attached file to listen in Purrytify.")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Song"))

        } catch (e: Exception) {
            android.util.Log.e("SharingUtils", "Error sharing HTML: ${e.message}")
            e.printStackTrace()
            // Fallback to regular sharing
            shareSongRegular(context, song)
        }
    }

    private fun shareSongRegular(context: Context, song: Song) {
        val deepLinkUri = createSongDeepLink(song.id)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(
                Intent.EXTRA_TEXT,
                "${context.getString(R.string.shared_song_message)}\n\n" +
                        "'${song.title}' by ${song.artist}\n\n" +
                        "Open this link in a browser to listen: $deepLinkUri"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song_via)))
    }
}
