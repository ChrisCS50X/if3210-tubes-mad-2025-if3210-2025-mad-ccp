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
import android.net.Uri
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
import com.example.purrytify.utils.SharingUtils
import androidx.navigation.findNavController
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.bumptech.glide.Glide
import android.content.Context
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private val networkViewModel by viewModels<NetworkViewModel>()
    lateinit var binding: ActivityMainBinding  // Changed from private to public
    private lateinit var tokenRefreshManager: TokenRefreshManager
    private lateinit var musicPlayerViewModel: MusicPlayerViewModel
    lateinit var database: AppDatabase

    private val songCompletionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.example.purrytify.PLAY_NEXT") {
                Log.d("MainActivity", "Broadcast received: song completed, playing next song")
                musicPlayerViewModel.playNext()
            }
        }
    }

    private var currentTab = R.id.navigation_home
    private var isLandscape = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verifyTokenAndNavigate()

        // Handle deep links that launched the app
        if (intent?.data != null) {
            handleDeepLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Log.d("MainActivity", "onNewIntent called, extras: ${intent?.extras}")
        setIntent(intent)

        if (intent?.data != null) {
            handleDeepLink(intent)
        } else {
            // Direct notification handling - this is critical for your case
            val openPlayer = intent?.getBooleanExtra("OPEN_PLAYER", false) ?: false
            val songId = intent?.getLongExtra("SONG_ID", -1L) ?: -1L

            if (openPlayer && songId != -1L) {
                Log.d("MainActivity", "onNewIntent: Opening player from notification, songId: $songId")

                lifecycleScope.launch {
                    try {
                        val repository = SongRepository(
                            database.songDao(),
                            this@MainActivity
                        )

                        val song = repository.getSongById(songId)
                        if (song != null) {
                            // Navigate to now playing screen
                            navigateToNowPlaying(song)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error handling notification in onNewIntent: ${e.message}", e)
                    }
                }
            }
        }
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

            // Check orientation
            isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            setupMusicPlayer()
            setupNavigation()
            setupListPadding()
            checkPermissions()
            requestNotificationPermission()
            setupTokenRefresh()
            handleNotificationOpen()
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

        if (isLandscape) {
            // Setup landscape navigation using sidebar LinearLayouts
            setupLandscapeNavigation(navController)
        } else {
            // Setup portrait navigation using BottomNavigationView
            setupPortraitNavigation(navController)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            isInNowPlayingFragment = (destination.id == R.id.navigation_now_playing)
            updateMiniPlayerVisibility(isInNowPlayingFragment)

            if (destination.id != R.id.navigation_now_playing) {
                if (!isLandscape) {
                    binding.bottomNavigation.menu.findItem(destination.id)?.isChecked = true
                } else {
                    updateLandscapeNavigationState(destination.id)
                }
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

    private fun setupPortraitNavigation(navController: NavController) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
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
    }

    private fun setupLandscapeNavigation(navController: NavController) {
        // Setup click listeners for landscape sidebar navigation
        binding.navHome?.setOnClickListener {
            navigateToDestination(navController, R.id.navigation_home)
        }

        binding.navLibrary?.setOnClickListener {
            navigateToDestination(navController, R.id.navigation_library)
        }

        binding.navProfile?.setOnClickListener {
            navigateToDestination(navController, R.id.navigation_profile)
        }
    }

    private fun navigateToDestination(navController: NavController, destinationId: Int) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
        val isOnNowPlaying = currentFragment is NowPlayingFragment

        if (isOnNowPlaying) {
            navController.popBackStack(navController.graph.startDestinationId, false)
        }

        try {
            navController.navigate(destinationId)
        } catch (e: Exception) {
            Log.e("MainActivity", "Navigation error: ${e.message}", e)
        }
    }

    private fun updateLandscapeNavigationState(currentDestinationId: Int) {
        // Reset all navigation items to unselected state
        binding.navHome?.isSelected = false
        binding.navLibrary?.isSelected = false
        binding.navProfile?.isSelected = false

        // Set warna hijau untuk item yang dipilih
        val selectedColor = getColor(R.color.accent_green)
        val unSelectedColor = getColor(R.color.white)
        when (currentDestinationId) {
            R.id.navigation_home -> {
                binding.navHomeIc?.setColorFilter(selectedColor)
                binding.navHomeText?.setTextColor(selectedColor)
                binding.navHome?.isSelected = true
                binding.navLibraryIc?.setColorFilter(unSelectedColor)
                binding.navLibraryText?.setTextColor(unSelectedColor)
                binding.navLibrary?.isSelected = false
                binding.navProfileIc?.setColorFilter(unSelectedColor)
                binding.navProfileText?.setTextColor(unSelectedColor)
                binding.navProfile?.isSelected = false

            }
            R.id.navigation_library -> {
                binding.navLibraryIc?.setColorFilter(selectedColor)
                binding.navLibraryText?.setTextColor(selectedColor)
                binding.navLibrary?.isSelected = true
                binding.navProfileIc?.setColorFilter(unSelectedColor)
                binding.navProfileText?.setTextColor(unSelectedColor)
                binding.navProfile?.isSelected = false
                binding.navHomeIc?.setColorFilter(unSelectedColor)
                binding.navHomeText?.setTextColor(unSelectedColor)
                binding.navHome?.isSelected = false
            }
            R.id.navigation_profile -> {
                binding.navProfileIc?.setColorFilter(selectedColor)
                binding.navProfileText?.setTextColor(selectedColor)
                binding.navProfile?.isSelected = true
                binding.navLibraryIc?.setColorFilter(unSelectedColor)
                binding.navLibraryText?.setTextColor(unSelectedColor)
                binding.navLibrary?.isSelected = false
                binding.navHomeIc?.setColorFilter(unSelectedColor)
                binding.navHomeText?.setTextColor(unSelectedColor)
                binding.navHome?.isSelected = false
            }
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

    private fun handleNotificationOpen() {
        // Check if we were launched from notification
        val openPlayer = intent?.getBooleanExtra("OPEN_PLAYER", false) ?: false
        val songId = intent?.getLongExtra("SONG_ID", -1L) ?: -1L

        if (openPlayer && songId != -1L) {
            Log.d("MainActivity", "Opening player from notification, songId: $songId")

            lifecycleScope.launch {
                try {
                    val repository = SongRepository(
                        database.songDao(),
                        this@MainActivity
                    )

                    val song = repository.getSongById(songId)
                    if (song != null) {
                        // Navigate to now playing screen
                        navigateToNowPlaying(song)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error handling notification open: ${e.message}", e)
                }
            }
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
        } else if (requestCode == BLUETOOTH_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All requested Bluetooth permissions granted
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for this feature", Toast.LENGTH_LONG).show()
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

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12+
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            
            val permissionsToRequest = requiredPermissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest, BLUETOOTH_PERMISSION_CODE)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            // For Android 11 and below
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            
            val permissionsToRequest = requiredPermissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest, BLUETOOTH_PERMISSION_CODE)
            }
        }
    }

    private fun setupMusicPlayer() {
        database = AppDatabase.getInstance(this)
        val repository = SongRepository(database.songDao(), applicationContext)
        val factory = MusicPlayerViewModelFactory(application, repository)
        musicPlayerViewModel = ViewModelProvider(this, factory)[MusicPlayerViewModel::class.java]

        musicPlayerViewModel.audioDeviceError.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                musicPlayerViewModel.clearAudioDeviceError()
            }
        }

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
            try {
                val currentSong = musicPlayerViewModel.currentSong.value
                if (currentSong != null) {
                    navigateToNowPlaying(currentSong)
                } else {
                    binding.miniPlayerContainer.visibility = View.GONE
                    Toast.makeText(this, "Song is still loading", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error navigating to now playing: ${e.message}", e)
                Toast.makeText(this, "Unable to open player", Toast.LENGTH_SHORT).show()
            }
        }

        // Updated like button to use ViewModel
        binding.miniPlayer.btnAddLiked.setOnClickListener {
            Log.d("MainActivity", "Like button clicked")
            musicPlayerViewModel.toggleLikeStatus()
        }

        binding.miniPlayer.btnMiniShare.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                if (SharingUtils.canShareSong(song)) {
                    SharingUtils.showShareOptions(supportFragmentManager, song)
                } else {
                    Toast.makeText(this, "Only server songs can be shared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe progress and duration
        musicPlayerViewModel.progress.observe(this) { progress ->
            binding.miniPlayer.miniSeekBar.progress = progress
        }

        musicPlayerViewModel.duration.observe(this) { duration ->
            binding.miniPlayer.miniSeekBar.max = duration
        }

        // Observe current song changes
        musicPlayerViewModel.currentSong.observe(this) { song ->
            Log.d("MainActivity", "Current song changed: ${song?.title ?: "null"}")
            song?.let {
                updateMiniPlayer(it)
            }
        }

        // Observe play/pause state
        musicPlayerViewModel.isPlaying.observe(this) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
            binding.miniPlayer.btnMiniPlayPause.setImageResource(icon)
        }

        // Observe like status dari ViewModel
        musicPlayerViewModel.isCurrentSongLiked.observe(this) { isLiked ->
            Log.d("MainActivity", "Like status changed in mini player: $isLiked")
            binding.miniPlayer.btnAddLiked.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
        }
    }

    private fun updateMiniPlayer(song: Song) {
        val database = AppDatabase.getInstance(this)
        val repository = SongRepository(database.songDao(), applicationContext)

        binding.miniPlayer.tvMiniTitle.text = song.title
        binding.miniPlayer.tvMiniArtist.text = song.artist
        
        // Observe active audio device and update indicator
        musicPlayerViewModel.activeAudioDevice.observe(this) { device ->
            device?.let { updateAudioDeviceIndicator(it) }
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
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController

            // Save current tab for later
            currentTab = navController.currentDestination?.id ?: R.id.navigation_home

            val action = NavGraphDirections.actionGlobalNavigationNowPlaying(
                song, musicPlayerViewModel.isPlaying.value ?: false
            )

            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setEnterAnim(R.anim.slide_in_up)
                .setExitAnim(R.anim.fade_out)
                .build()

            navController.navigate(action.actionId, action.arguments, navOptions)

        } catch (e: Exception) {
            Log.e("MainActivity", "Navigation error: ${e.message}", e)
            // Handle navigation failures gracefully
            Toast.makeText(this, "Unable to open player", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::musicPlayerViewModel.isInitialized) {
            try {
                val filter = android.content.IntentFilter("com.example.purrytify.PLAY_NEXT")

                // Use the RECEIVER_NOT_EXPORTED flag for Android 14+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerReceiver(
                        songCompletionReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    // For older versions, regular registration is fine since it's a local broadcast
                    // For even better practice, consider using LocalBroadcastManager
                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                        .registerReceiver(songCompletionReceiver, filter)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                unregisterReceiver(songCompletionReceiver)
            } else {
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(songCompletionReceiver)
            }
            Log.d("MainActivity", "Unregistered song completion broadcast receiver")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
    }

    private fun setupListPadding() {
        // Observe mini player visibility changes
        binding.miniPlayerContainer.viewTreeObserver.addOnGlobalLayoutListener {
            val miniPlayerHeight = if (binding.miniPlayerContainer.visibility == View.VISIBLE) {
                binding.miniPlayerContainer.height
            } else {
                0
            }

            // Broadcast the padding value to fragments
            val intent = Intent("com.example.purrytify.UPDATE_BOTTOM_PADDING")
            intent.setPackage(applicationContext.packageName)
            intent.putExtra("padding", miniPlayerHeight)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Getter method to safely expose the musicPlayerViewModel to extension functions
     */
    fun getMusicPlayerViewModel() = musicPlayerViewModel

    /**
     * Update the audio device indicator in the mini player
     */
    private fun updateAudioDeviceIndicator(device: com.example.purrytify.data.model.AudioDevice) {
        try {
            // Get mini player views
            val audioIndicator = binding.miniPlayer.audioOutputIndicator
            val deviceIcon = binding.miniPlayer.audioDeviceIcon
            val deviceName = binding.miniPlayer.audioDeviceName
            
            // Make indicator visible
            audioIndicator.visibility = View.VISIBLE
            
            // Set device name
            deviceName.text = device.name
            
            // Set appropriate icon based on device type
            when (device.type) {
                com.example.purrytify.data.model.AudioDeviceType.BLUETOOTH -> {
                    deviceIcon.setImageResource(R.drawable.ic_bluetooth_audio)
                }
                com.example.purrytify.data.model.AudioDeviceType.WIRED_HEADSET -> {
                    deviceIcon.setImageResource(R.drawable.ic_headset)
                }
                com.example.purrytify.data.model.AudioDeviceType.INTERNAL_SPEAKER -> {
                    deviceIcon.setImageResource(R.drawable.ic_speaker)
                }
                com.example.purrytify.data.model.AudioDeviceType.USB_AUDIO -> {
                    deviceIcon.setImageResource(R.drawable.ic_usb_audio)
                }
                else -> {
                    deviceIcon.setImageResource(R.drawable.ic_audio_output)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating audio device indicator: ${e.message}")
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
        private const val STORAGE_PERMISSION_CODE = 100
        private const val BLUETOOTH_PERMISSION_CODE = 102
    }
}