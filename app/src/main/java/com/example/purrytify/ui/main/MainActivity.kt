package com.example.purrytify.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.example.purrytify.service.TokenRefreshManager
import android.content.Intent
import android.util.Log
import android.view.View
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.UserRepository
import com.example.purrytify.ui.login.LoginActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.ui.player.MusicPlayerViewModel
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory
import com.example.purrytify.NavGraphDirections


import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {
    private val networkViewModel by viewModels<NetworkViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenRefreshManager: TokenRefreshManager
    private lateinit var musicPlayerViewModel: MusicPlayerViewModel

    private var currentTab = R.id.navigation_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check token validity before showing any UI
        verifyTokenAndNavigate()
    }

    private fun verifyTokenAndNavigate() {
        val tokenManager = TokenManager(this)

        // Debug logging
        val currentToken = tokenManager.getToken()
        Log.d("TokenDebug", "Current stored token: $currentToken")
        val refreshToken = tokenManager.getRefreshToken()
        Log.d("TokenDebug", "Current refresh token: $refreshToken")

        // If no token exists, go to login
        if (!tokenManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        // Check if token is valid
        lifecycleScope.launch {
            val userRepository = UserRepository(tokenManager)
            val isTokenValid = userRepository.verifyToken()

            if (!isTokenValid) {
                Log.d("TokenDebug", "Token invalid, attempting refresh")
                // Try to refresh the token
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isFailure) {
                    Log.d("TokenDebug", "Token refresh failed, navigating to login")
                    // Couldn't refresh token, go to login
                    tokenManager.clearTokens()
                    navigateToLogin()
                    return@launch
                }
            }

            // Token is valid or was refreshed successfully
            Log.d("TokenDebug", "Token valid or refreshed, continuing to main app")
            continueToMainApp()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun continueToMainApp() {
        runOnUiThread {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupMusicPlayer()
            setupNavigation()
            checkPermissions()
            requestNotificationPermission()
            setupTokenRefresh()
        }
    }

    private fun setupTokenRefresh() {
        tokenRefreshManager = TokenRefreshManager(this)
        tokenRefreshManager.scheduleTokenRefresh()
    }


    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Track whether we're currently in the NowPlayingFragment
        var isInNowPlayingFragment = false

        // Add navigation listener to handle mini player visibility
        navController.addOnDestinationChangedListener { _, destination, _ ->
            isInNowPlayingFragment = (destination.id == R.id.navigation_now_playing)
            updateMiniPlayerVisibility(isInNowPlayingFragment)
        }

        // Observe song changes separately from navigation
        musicPlayerViewModel.currentSong.observe(this) { song ->
            if (!isInNowPlayingFragment) {
                // Only update visibility if we're not in the now playing fragment
                binding.miniPlayerContainer.visibility =
                    if (song != null) View.VISIBLE else View.GONE
            }

            // Update mini player content regardless of visibility
            song?.let { updateMiniPlayer(it) }
        }
    }

    private fun updateMiniPlayerVisibility(isInNowPlayingFragment: Boolean) {
        binding.miniPlayerContainer.visibility = if (isInNowPlayingFragment) {
            View.GONE
        } else if (musicPlayerViewModel.currentSong.value != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request granular media permissions
            val permissions = mutableListOf<String>()

            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }

            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), STORAGE_PERMISSION_CODE)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Storage permission is required to add songs", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun setupMusicPlayer() {
        // Get the ViewModel
        val database = AppDatabase.getInstance(this)
        val repository = SongRepository(database.songDao(), applicationContext)
        val factory = MusicPlayerViewModelFactory(application, repository)
        musicPlayerViewModel = ViewModelProvider(this, factory)[MusicPlayerViewModel::class.java]

        musicPlayerViewModel.currentSong.value?.let { song ->
            lifecycleScope.launch {
                val isLiked =repository.getLikedStatusBySongId(song.id)
                binding.miniPlayer.btnAddLiked.setImageResource(
                    if (isLiked) R.drawable.ic_minus else R.drawable.ic_plus
                )
            }
        }

        // Set up mini player controls
        binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
            Log.d("MainActivity", "Play/Pause button clicked, current state: ${musicPlayerViewModel.isPlaying.value}")
            musicPlayerViewModel.togglePlayPause()
        }

        binding.miniPlayer.root.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                navigateToNowPlaying(song)
            }
        }

        binding.miniPlayer.btnAddLiked.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                lifecycleScope.launch {
                    if(repository.getLikedStatusBySongId(song.id)){
                        repository.updateLikeStatus(song.id, false)
                        binding.miniPlayer.btnAddLiked.setImageResource(R.drawable.ic_plus)
                    }
                    else{
                        repository.updateLikeStatus(song.id, true)
                        binding.miniPlayer.btnAddLiked.setImageResource(R.drawable.ic_minus)
                    }
                }

            }
        }

        // Update mini player seek bar with current progress
        musicPlayerViewModel.progress.observe(this) { progress ->
            binding.miniPlayer.miniSeekBar.progress = progress
        }

        // Update seek bar max value when duration changes
        musicPlayerViewModel.duration.observe(this) { duration ->
            binding.miniPlayer.miniSeekBar.max = duration
        }

        // Observe music player
        musicPlayerViewModel.currentSong.observe(this) { song ->
            Log.d("MainActivity", "Current song changed: ${song?.title ?: "null"}")
            song?.let {
                updateMiniPlayer(it)
            }
        }

        musicPlayerViewModel.isPlaying.observe(this) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
            binding.miniPlayer.btnMiniPlayPause.setImageResource(icon)
        }
    }

    private fun updateMiniPlayer(song: Song) {
        binding.miniPlayer.tvMiniTitle.text = song.title
        binding.miniPlayer.tvMiniArtist.text = song.artist

        // Load cover art with Glide
        Glide.with(this)
            .load(song.coverUrl)
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .into(binding.miniPlayer.ivMiniCover)
    }

    private fun navigateToNowPlaying(song: Song) {
        // Get NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Store current tab before navigating
        currentTab = navController.currentDestination?.id ?: R.id.navigation_home
        Log.d("Navigation", "Navigating to Now Playing from tab: $currentTab")

        // Always use global action for simplicity and consistency
        val action = NavGraphDirections.actionGlobalNavigationNowPlaying(
            song, musicPlayerViewModel.isPlaying.value ?: false
        )

        // Use basic navigation without complex options
        navController.navigate(action)

        // Debug: Print back stack after navigation
        logBackStack(navController)
    }

    private fun logBackStack(navController: NavController) {
        val backStack = mutableListOf<String>()
        var backStackIndex = 0

        // Try-catch in case of exceptions with the internal NavController methods
        try {
            while (true) {
                val entry = navController.getBackStackEntry(backStackIndex)
                backStack.add("${entry.destination.label ?: entry.destination.id}")
                backStackIndex++
            }
        } catch (e: Exception) {
            // This is expected when we reach the end of the back stack
        }

        Log.d("NavigationDebug", "Back stack (${backStack.size}): ${backStack.joinToString(" -> ")}")
    }

    // Add this public method to MainActivity
    fun navigateBackFromNowPlaying() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        Log.d("Navigation", "Navigating back from Now Playing to tab: $currentTab")

        // Navigate to the saved tab
        when (currentTab) {
            R.id.navigation_home -> navController.navigate(R.id.navigation_home)
            R.id.navigation_library -> navController.navigate(R.id.navigation_library)
            R.id.navigation_profile -> navController.navigate(R.id.navigation_profile)
            else -> navController.navigate(R.id.navigation_home)
        }

        // Debug: Print back stack after navigation
        logBackStack(navController)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
        private const val STORAGE_PERMISSION_CODE = 100
    }
}