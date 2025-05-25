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
import com.example.purrytify.data.repository.ChartRepository
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.ui.player.NowPlayingFragment
import com.example.purrytify.utils.SharingUtils
import kotlinx.coroutines.launch

/**
 * Extension file for MainActivity to handle deep link processing
 */

/**
 * Handle incoming deep links (e.g., purrytify://song/123)
 */
fun MainActivity.handleDeepLink(intent: Intent?) {
    val uri = intent?.data
    Log.d("DeepLink", "Processing URI: $uri")

    // Check for notification-specific deep link (content://song/ID format)
    if (uri != null && uri.toString().startsWith("content://song/")) {
        try {
            val songIdStr = uri.lastPathSegment
            val songId = songIdStr?.toLongOrNull()

            Log.d("DeepLink", "Found notification deep link with song ID: $songId")

            if (songId != null) {
                handleSongDeepLink(songId)
                return
            }
        } catch (e: Exception) {
            Log.e("DeepLink", "Error parsing notification URI: ${e.message}", e)
        }
    }

    // Check for the standard song deep link
    if (uri != null) {
        val songId = SharingUtils.extractSongIdFromDeepLink(uri)
        if (songId != null) {
            Log.d("DeepLink", "Found standard deep link with song ID: $songId")
            handleSongDeepLink(songId)
            return
        }
    }

    // CRITICAL: Also check for direct extras approach from notification
    val openPlayer = intent?.getBooleanExtra("OPEN_PLAYER", false) ?: false
    val songId = intent?.getLongExtra("SONG_ID", -1L) ?: -1L

    if (openPlayer && songId != -1L) {
        Log.d("DeepLink", "Found direct extras with song ID: $songId")
        handleSongDeepLink(songId)
        return
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
    val chartRepository = ChartRepository( // Added ChartRepository instance
        com.example.purrytify.data.local.TokenManager(applicationContext),
        songRepository
    )

    lifecycleScope.launch {
        try {
            // Fetch the song from the server using ChartRepository
            val result = chartRepository.getSongDetailFromServer(songId)
            if (result.isSuccess) {
                val chartSong = result.getOrNull()
                if (chartSong != null) {
                    val song = chartSong.toSong() // Convert ChartSong to Song
                    // Navigate to the now playing page with the song
                    playSharedSong(song)
                    Log.d("DeepLink", "Successfully opened song: ${song.title}")
                } else {
                    Log.e("DeepLink", "Song not found with ID: $songId (from server)")
                    Toast.makeText(this@handleSongDeepLink, "Song not found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("DeepLink", "Error fetching song $songId from server: ${result.exceptionOrNull()?.message}")
                // Fallback to local repository if server fetch fails
                Log.d("DeepLink", "Falling back to local repository for song ID: $songId")
                val song = songRepository.getSongById(songId)
                if (song != null) {
                    playSharedSong(song)
                    Log.d("DeepLink", "Successfully opened song from local: ${song.title}")
                } else {
                    Log.e("DeepLink", "Song not found with ID: $songId (local fallback)")
                    Toast.makeText(this@handleSongDeepLink, "Song not found", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("DeepLink", "Error processing song deep link", e)
            Toast.makeText(this@handleSongDeepLink, "Error playing song", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Play a song that was shared via deep link
 */
private fun MainActivity.playSharedSong(song: Song) {
    try {
        val viewModel = this.getMusicPlayerViewModel()

        // Check if we're already playing this song
        val currentSong = viewModel.currentSong.value
        val isSameSong = currentSong != null && currentSong.id == song.id

        // Only start playback if it's a different song
        if (!isSameSong) {
            Log.d("DeepLink", "Starting playback of new song: ${song.title}")
            viewModel.playSong(song)
        } else {
            Log.d("DeepLink", "Song ${song.title} already playing")
            // If it's paused, toggle it back to play
            if (viewModel.isPlaying.value != true) {
                Log.d("DeepLink", "Song is paused, resuming playback")
                viewModel.togglePlayPause()  // This is the correct method to use
            }
        }

        // Check if we're already on the player screen
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

        // Only navigate if we're not already on the player screen
        if (currentFragment !is NowPlayingFragment) {
            val navController = navHostFragment?.navController
            if (navController != null) {
                val action = NavGraphDirections.actionGlobalNavigationNowPlaying(
                    song,
                    viewModel.isPlaying.value ?: true
                )
                navController.navigate(action)
                Log.d("DeepLink", "Navigated to player for ${song.title}")
            }
        } else {
            Log.d("DeepLink", "Already on player screen, skipping navigation")
        }
    } catch (e: Exception) {
        Log.e("DeepLink", "Error processing shared song", e)
        Toast.makeText(this, "Unable to play the song", Toast.LENGTH_SHORT).show()
    }
}
