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
import com.example.purrytify.ui.player.NowPlayingFragment
import com.example.purrytify.utils.ColorUtils
import com.example.purrytify.utils.BackgroundColorProvider

import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.bumptech.glide.Glide
import android.content.Context

class MainActivity : AppCompatActivity() {
    private val networkViewModel by viewModels<NetworkViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenRefreshManager: TokenRefreshManager
    private lateinit var musicPlayerViewModel: MusicPlayerViewModel

    private val songCompletionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.example.purrytify.PLAY_NEXT") {
                Log.d("MainActivity", "Broadcast received: song completed, playing next song")
                musicPlayerViewModel.playNext()
            }
        }
    }

    private var currentTab = R.id.navigation_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verifyTokenAndNavigate()
    }

    private fun verifyTokenAndNavigate() {
        val tokenManager = TokenManager(this)

        val currentToken = tokenManager.getToken()
        Log.d("TokenDebug", "Current stored token: $currentToken")
        val refreshToken = tokenManager.getRefreshToken()
        Log.d("TokenDebug", "Current refresh token: $refreshToken")

        if (!tokenManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        lifecycleScope.launch {
            val userRepository = UserRepository(tokenManager)
            val isTokenValid = userRepository.verifyToken()

            if (!isTokenValid) {
                Log.d("TokenDebug", "Token invalid, attempting refresh")
                val refreshResult = userRepository.refreshToken()

                if (refreshResult.isFailure) {
                    Log.d("TokenDebug", "Token refresh failed, navigating to login")
                    tokenManager.clearTokens()
                    navigateToLogin()
                    return@launch
                }
            }

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

        var isInNowPlayingFragment = false

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            val isOnNowPlaying = currentFragment is NowPlayingFragment

            if (isOnNowPlaying) {
                navController.popBackStack(navController.graph.startDestinationId, false)
            }

            when (item.itemId) {
                R.id.navigation_home -> navController.navigate(R.id.navigation_home)
                R.id.navigation_library -> navController.navigate(R.id.navigation_library)
                R.id.navigation_profile -> navController.navigate(R.id.navigation_profile)
            }

            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            isInNowPlayingFragment = (destination.id == R.id.navigation_now_playing)
            updateMiniPlayerVisibility(isInNowPlayingFragment)

            if (destination.id != R.id.navigation_now_playing) {
                binding.bottomNavigation.menu.findItem(destination.id)?.isChecked = true
            }
        }

        musicPlayerViewModel.currentSong.observe(this) { song ->
            if (!isInNowPlayingFragment) {
                binding.miniPlayerContainer.visibility =
                    if (song != null) View.VISIBLE else View.GONE
            }

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
        val database = AppDatabase.getInstance(this)
        val repository = SongRepository(database.songDao(), applicationContext)
        val factory = MusicPlayerViewModelFactory(application, repository)
        musicPlayerViewModel = ViewModelProvider(this, factory)[MusicPlayerViewModel::class.java]

        musicPlayerViewModel.currentSong.value?.let { song ->
            lifecycleScope.launch {
                val isLiked =repository.getLikedStatusBySongId(song.id)
                binding.miniPlayer.btnAddLiked.setImageResource(
                    if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
            }
        }

        binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
            Log.d("MainActivity", "Play/Pause button clicked, current state: ${musicPlayerViewModel.isPlaying.value}")
            musicPlayerViewModel.togglePlayPause()
        }

        binding.miniPlayer.root.setOnClickListener {
            val currentSong = musicPlayerViewModel.currentSong.value
            if (currentSong != null) {
                navigateToNowPlaying(currentSong)
            } else {
                binding.miniPlayerContainer.visibility = View.GONE
                Toast.makeText(this, "This song is no longer available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.miniPlayer.btnAddLiked.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                lifecycleScope.launch {
                    if(repository.getLikedStatusBySongId(song.id)){
                        repository.updateLikeStatus(song.id, false)
                        binding.miniPlayer.btnAddLiked.setImageResource(R.drawable.ic_heart_outline)
                    }
                    else{
                        repository.updateLikeStatus(song.id, true)
                        binding.miniPlayer.btnAddLiked.setImageResource(R.drawable.ic_heart_filled)
                    }
                }
            }
        }

        musicPlayerViewModel.progress.observe(this) { progress ->
            binding.miniPlayer.miniSeekBar.progress = progress
        }

        musicPlayerViewModel.duration.observe(this) { duration ->
            binding.miniPlayer.miniSeekBar.max = duration
        }

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
        val database = AppDatabase.getInstance(this)
        val repository = SongRepository(database.songDao(), applicationContext)

        binding.miniPlayer.tvMiniTitle.text = song.title
        binding.miniPlayer.tvMiniArtist.text = song.artist

        musicPlayerViewModel.currentSong.value?.let { currentSong ->
            lifecycleScope.launch {
                val isLiked = try {
                    repository.getLikedStatusBySongId(currentSong.id)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error getting liked status: ${e.message}")
                    false
                }
                binding.miniPlayer.btnAddLiked.setImageResource(
                    if (isLiked == true) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
            }
        }

        Glide.with(this)
            .load(song.coverUrl)
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .into(binding.miniPlayer.ivMiniCover)

        val backgroundColor = BackgroundColorProvider.getColorForSong(song)
        BackgroundColorProvider.applyColorSafely(binding.miniPlayer.root, backgroundColor)
    }

    private fun navigateToNowPlaying(song: Song) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        currentTab = navController.currentDestination?.id ?: R.id.navigation_home

        val action = NavGraphDirections.actionGlobalNavigationNowPlaying(
            song, musicPlayerViewModel.isPlaying.value ?: false
        )

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .build()

        navController.navigate(action.actionId, action.arguments, navOptions)
    }

    private fun logBackStack(navController: NavController) {
        val backStack = mutableListOf<String>()
        var backStackIndex = 0

        try {
            while (true) {
                val entry = navController.getBackStackEntry(backStackIndex)
                backStack.add("${entry.destination.label ?: entry.destination.id}")
                backStackIndex++
            }
        } catch (e: Exception) {

        }

        Log.d("NavigationDebug", "Back stack (${backStack.size}): ${backStack.joinToString(" -> ")}")
    }

    fun navigateBackFromNowPlaying() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        Log.d("Navigation", "Navigating back from Now Playing to tab: $currentTab")

        when (currentTab) {
            R.id.navigation_home -> navController.navigate(R.id.navigation_home)
            R.id.navigation_library -> navController.navigate(R.id.navigation_library)
            R.id.navigation_profile -> navController.navigate(R.id.navigation_profile)
            else -> navController.navigate(R.id.navigation_home)
        }

        logBackStack(navController)
    }

    override fun onResume() {
        super.onResume()
        if (::musicPlayerViewModel.isInitialized) {
            try {
                val filter = android.content.IntentFilter("com.example.purrytify.PLAY_NEXT")

                // Use the Context.RECEIVER_NOT_EXPORTED flag for Android 14+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerReceiver(songCompletionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(songCompletionReceiver, filter)
                }

                Log.d("MainActivity", "Registered song completion broadcast receiver")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error registering receiver: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()

        try {
            unregisterReceiver(songCompletionReceiver)
            Log.d("MainActivity", "Unregistered song completion broadcast receiver")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
        private const val STORAGE_PERMISSION_CODE = 100
    }
}