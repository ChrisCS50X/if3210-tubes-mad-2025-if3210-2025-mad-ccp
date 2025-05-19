package com.example.purrytify.ui.main

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.example.purrytify.NavGraphDirections
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.utils.SharingUtils
import kotlinx.coroutines.launch

/**
 * Extension file for MainActivity to handle deep link processing
 */

/**
 * Handle incoming deep links (e.g., purrytify://song/123)
 */
fun MainActivity.handleDeepLink(intent: Intent?) {
    val uri = intent?.data ?: return
    Log.d("DeepLink", "Processing URI: $uri")
    
    // Check if this is a song deep link
    val songId = SharingUtils.extractSongIdFromDeepLink(uri)
    if (songId != null) {
        Log.d("DeepLink", "Found song ID: $songId")
        handleSongDeepLink(songId)
    }
}

/**
 * Handle song deep link by fetching the song and opening the Now Playing UI
 */
private fun MainActivity.handleSongDeepLink(songId: Long) {
    val songRepository = SongRepository(
        database.songDao(),
        applicationContext
    )
    
    lifecycleScope.launch {
        try {
            // Fetch the song from the repository
            val song = songRepository.getSongById(songId)
            
            if (song != null) {
                // Navigate to the now playing page with the song
                playSharedSong(song)
                Log.d("DeepLink", "Successfully opened song: ${song.title}")
            } else {
                Log.e("DeepLink", "Song not found with ID: $songId")
                // Future enhancement: show an error toast or dialog
            }
        } catch (e: Exception) {
            Log.e("DeepLink", "Error processing song deep link", e)
            // Future enhancement: show an error toast or dialog
        }
    }
}

/**
 * Play a song that was shared via deep link
 */
private fun MainActivity.playSharedSong(song: Song) {
    try {
        // Update the UI to show the song is playing
        this.getMusicPlayerViewModel().playSong(song)
        
        // Navigate to the now playing screen
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val action = NavGraphDirections.actionGlobalNavigationNowPlaying(song)
        navController.navigate(action)
        
        Toast.makeText(this, "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("DeepLink", "Error playing shared song", e)
        Toast.makeText(this, "Unable to play the song", Toast.LENGTH_SHORT).show()
    }
}
