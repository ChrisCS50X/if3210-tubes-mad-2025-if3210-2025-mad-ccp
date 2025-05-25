package com.example.purrytify.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentManager
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.ui.dialog.ShareOptionsDialog

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
        val deepLinkUri = createSongDeepLink(song.id)
        
        // Create share intent with custom messaging
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(
                Intent.EXTRA_TEXT, 
                "${context.getString(R.string.shared_song_message)}: " +
                "'${song.title}' by ${song.artist}\n$deepLinkUri"
            )
        }
        
        // Launch the share chooser
        val chooser = Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_song_via)
        )
        context.startActivity(chooser)
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
}
