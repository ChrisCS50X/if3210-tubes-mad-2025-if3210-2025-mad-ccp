package com.example.purrytify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.ui.main.MainActivity
import java.io.IOException
import android.util.Log
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.data.repository.SongRepository
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch


/**
 * Service untuk pemutaran musik.
 * Bertugas memutar lagu di background dan menampilkan notifikasi kontrol.
 * Service ini extend LifecycleService biar bisa pake LiveData.
 */
class MediaPlayerService : LifecycleService() {
    private val TAG = "MediaPlayerService"

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null
    
    // Analytics tracking
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var tokenManager: TokenManager
    private var playStartTime: Long = 0
    private var totalPlayTimeInSession: Long = 0

    // LiveData untuk dipake ViewModel dan UI
    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> = _progress

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    // Binder untuk komunikasi dengan activity/fragment
    private val binder = LocalBinder()
    private var progressUpdateJob: Runnable? = null

    // Handler untuk update progress di main thread
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var notificationManager: NotificationManager

    /**
     * Local binder class buat interaksi service dengan activity.
     * Ini biar activity bisa manggil method service secara langsung.
     */
    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    /**
     * Inisialisasi service waktu pertama dibuat.
     * Setup audio manager dan media session di sini.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service created")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Initialize analytics tracking
        val database = AppDatabase.getInstance(applicationContext)
        analyticsRepository = AnalyticsRepository(database.listeningStatsDao())
        tokenManager = TokenManager(applicationContext)

        setupMediaSession()
        checkAudioSettings()
        createNotificationChannel()

        progressUpdateJob = Runnable {
            updateProgress()
            progressUpdateJob?.let {
                mainHandler.postDelayed(it, 500)
            }
        }
    }

    /**
     * Method yang dipanggil pas service di-bind.
     * Kita return binder yang nanti dipake activity.
     */
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind: Service bound")
        super.onBind(intent)
        return binder
    }

    /**
     * Setup media session buat integrasi dengan sistem Android.
     * Penting buat kontrol dari notifikasi dan headset.
     */
    private fun setupMediaSession() {
        Log.d(TAG, "setupMediaSession: Setting up media session")

        mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession").apply {
            // Set flags for media buttons and transport controls
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

            // Set initial PlaybackState
            setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP)
                .build()
            )

            // Set active state
            isActive = true

            // Set callback for media button events
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession callback: onPlay")
                    play()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession callback: onPause")
                    pause()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession callback: onSkipToNext")
                    playNext()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession callback: onSkipToPrevious")
                    playPrevious()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession callback: onStop")
                    stopPlayback()
                    stopForeground(true)
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    Log.d(TAG, "MediaSession callback: onSeekTo: $pos")
                    seekTo(pos.toInt())
                }
            })
        }
    }



    /**
     * Ngecek dan mastiin volume suara ga nol.
     */
    private fun checkAudioSettings() {
        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

        Log.d(TAG, "checkAudioSettings: Music volume: $currentVolume/$maxVolume")

        if (currentVolume == 0) {
            Log.d(TAG, "checkAudioSettings: Volume is 0, increasing to 1/3 of max")
            audioManager?.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                maxVolume / 3,
                0
            )
        }
    }

    private suspend fun getLocalFilePathIfAvailable(song: Song): String {
        try {
            val repository = SongRepository(
                AppDatabase.getInstance(applicationContext).songDao(),
                applicationContext
            )

            // If song is online (URL) and we have a downloaded version, use the local path
            if (song.filePath.startsWith("http")) {
                try {
                    val localSong = repository.getSongById(song.id)
                    if (localSong != null &&
                        !localSong.filePath.startsWith("http") &&
                        java.io.File(localSong.filePath).exists()) {

                        Log.d(TAG, "Found verified local file for song: ${song.title}")
                        return localSong.filePath
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for local file: ${e.message}")
                    // Fall through to return original path
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in getLocalFilePathIfAvailable", e)
        }

        // Otherwise use the original path
        return song.filePath
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.let { player ->
                // Step-by-step safe cleanup
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping player: ${e.message}", e)
                }

                try {
                    player.reset()
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting player: ${e.message}", e)
                }

                try {
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing player: ${e.message}", e)
                }
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaPlayer cleanup: ${e.message}", e)
        }
    }

    /**
     * Method utama buat mulai memutar lagu baru.
     * Reset player lama (kalo ada) dan bikin player baru.
     */
    fun playSong(song: Song) {
        Log.d(TAG, "playSong: Starting to play ${song.title}")
        Log.d(TAG, "playSong: File path: ${song.filePath}")

        try {
            stopMediaPlayer()

            // Initialize LiveData values
            _isPlaying.postValue(false)
            _currentSong.postValue(song)

            // Create and prepare new MediaPlayer
            Log.d(TAG, "playSong: Creating new MediaPlayer instance")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Set up all listeners BEFORE setting data source
                setOnPreparedListener { mp ->
                    Log.d(TAG, "MediaPlayer onPrepared: Ready to play")
                    // Don't automatically start here - that will happen in the
                    // online or offline specific code paths
                }

                setOnCompletionListener { mp ->
                    Log.d(TAG, "MediaPlayer onCompletion: Playback completed")
                    mainHandler.post {
                        try {
                            // Record the final segment of listening time
                            if (playStartTime > 0) {
                                val currentTime = System.currentTimeMillis()
                                val duration = currentTime - playStartTime
                                totalPlayTimeInSession += duration
                                
                                // Record the listening session in the database
                                _currentSong.value?.let { song ->
                                    val userId = tokenManager.getEmail()
                                    if (!userId.isNullOrEmpty()) {
                                        lifecycleScope.launch {
                                            analyticsRepository.recordSongListening(userId, song, duration)
                                            Log.d(TAG, "onCompletion: Recorded ${duration}ms of listening time for ${song.title}")
                                        }
                                    }
                                }
                                
                                // Reset the start time
                                playStartTime = 0
                            }
                            
                            // Update playing status
                            _isPlaying.postValue(false)

                            // Check for repeat mode
                            val repeatModeIntent = Intent("com.example.purrytify.CHECK_REPEAT_MODE")
                            // Set package to make it explicit
                            repeatModeIntent.setPackage(applicationContext.packageName)
                            LocalBroadcastManager.getInstance(applicationContext)
                                .sendBroadcast(repeatModeIntent)

                            // Use LocalBroadcastManager for safer broadcast
                            try {
                                val intent = Intent("com.example.purrytify.PLAY_NEXT")
                                // Set package to make it explicit
                                intent.setPackage(applicationContext.packageName)
                                LocalBroadcastManager.getInstance(applicationContext)
                                    .sendBroadcast(intent)
                                Log.d(TAG, "MediaPlayer onCompletion: Local broadcast sent to play next song")
                            } catch (e: Exception) {
                                Log.e(TAG, "MediaPlayer onCompletion: Error sending local broadcast", e)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "MediaPlayer onCompletion: Error in completion handler", e)
                        }
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer onError: what=$what, extra=$extra")
                    _isPlaying.postValue(false)
                    false
                }

                try {
                    val isOnlineSource = song.filePath.startsWith("http://") ||
                            song.filePath.startsWith("https://")

                    if (isOnlineSource) {
                        // Check for downloaded version first
                        val localPath = runBlocking { getLocalFilePathIfAvailable(song) }
                        val useLocalFile = !localPath.startsWith("http")

                        if (useLocalFile) {
                            // We have a local downloaded version - use it instead of streaming
                            Log.d(TAG, "playSong: Using downloaded local file: $localPath")

                            val uri = if (localPath.startsWith("content://") ||
                                localPath.startsWith("android.resource://") ||
                                localPath.startsWith("file://")) {
                                localPath.toUri()
                            } else {
                                "file://$localPath".toUri()
                            }

                            // Special preparation for local files
                            try {
                                setDataSource(applicationContext, uri)
                                prepare()  // Synchronous for local files
                                _duration.postValue(duration)

                                // Local files can start playing immediately
                                start()
                                _isPlaying.postValue(true)
                                // Start tracking playback time for analytics
                                playStartTime = System.currentTimeMillis()
                                Log.d(TAG, "Local file: Started tracking playback time at $playStartTime")
                                startProgressUpdates()
                                updateNotification()

                                // Notification must be created AFTER MediaPlayer is prepared
                                try {
                                    Log.d(TAG, "playSong: Starting foreground service with notification")
                                    startForeground(NOTIFICATION_ID, createNotification(song))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error starting foreground service", e)
                                }

                                return@apply  // Exit early
                            } catch (e: Exception) {
                                Log.e(TAG, "Error playing local file, falling back to online source", e)
                                // Continue to online path as fallback
                            }
                        }

                        // Online streaming path
                        Log.d(TAG, "playSong: Streaming online URL: ${song.filePath}")

                        // For online URLs, set prepared listener before setDataSource
                        setOnPreparedListener { mp ->
                            Log.d(TAG, "MediaPlayer onPrepared: Ready to play online content")
                            _duration.postValue(mp.duration)
                            try {
                                start()
                                _isPlaying.postValue(true)
                                // Start tracking playback time for analytics
                                playStartTime = System.currentTimeMillis()
                                Log.d(TAG, "Online content: Started tracking playback time at $playStartTime")
                                startProgressUpdates()
                                updateNotification()
                                Log.d(TAG, "Online content prepared successfully, duration: ${mp.duration}ms")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting playback after prepare", e)
                                _isPlaying.postValue(false)
                            }
                        }

                        // Set URL for online streams
                        setDataSource(song.filePath)

                        // Set default duration while we wait for the actual value
                        _duration.postValue(0)

                        // Asynchronous preparation for streaming
                        Log.d(TAG, "playSong: DataSource set, preparing async for online content...")
                        prepareAsync()

                        // Notification must be created even before preparation completes
                        try {
                            Log.d(TAG, "playSong: Starting foreground service with notification")
                            startForeground(NOTIFICATION_ID, createNotification(song))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting foreground service", e)
                        }
                    } else {
                        // Standard local file path handling
                        val uri = if (song.filePath.startsWith("content://") ||
                            song.filePath.startsWith("android.resource://") ||
                            song.filePath.startsWith("file://")) {
                            song.filePath.toUri()
                        } else {
                            "file://${song.filePath}".toUri()
                        }

                        Log.d(TAG, "playSong: URI: $uri")
                        setDataSource(applicationContext, uri)
                        Log.d(TAG, "playSong: DataSource set, preparing...")
                        prepare()
                        Log.d(TAG, "playSong: MediaPlayer prepared successfully, duration: ${duration}ms")
                        _duration.postValue(duration)

                        // Start playing immediately
                        start()
                        _isPlaying.postValue(true)
                        startProgressUpdates()

                        // Notification creation after successful preparation
                        try {
                            Log.d(TAG, "playSong: Starting foreground service with notification")
                            startForeground(NOTIFICATION_ID, createNotification(song))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting foreground service", e)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "playSong: Error preparing MediaPlayer", e)
                    _isPlaying.postValue(false)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "playSong: IllegalStateException preparing MediaPlayer", e)
                    _isPlaying.postValue(false)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "playSong: IllegalArgumentException with URI", e)
                    _isPlaying.postValue(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "playSong: Unexpected error", e)
            _isPlaying.postValue(false)
        }
    }

    /**
     * Mulai/lanjutin pemutaran lagu.
     * Mastiin kita dapet audio focus dulu baru play.
     */
    fun play() {
        Log.d(TAG, "play: Method called, current player state: ${mediaPlayer?.isPlaying}")

        if (mediaPlayer == null) {
            Log.e(TAG, "play: Cannot play - MediaPlayer is null")
            return
        }

        // Coba minta audio focus beberapa kali kalo gagal
        var focusGranted = false
        var attempts = 0
        while (!focusGranted && attempts < 3) {
            focusGranted = requestAudioFocus()
            attempts++
            if (!focusGranted && attempts < 3) {
                Log.d(TAG, "play: Audio focus denied, retrying (attempt $attempts)")
                try { Thread.sleep(100) } catch (e: InterruptedException) { }
            }
        }

        if (focusGranted) {
            Log.d(TAG, "play: Audio focus granted after $attempts attempts, starting playback")
            try {
                        mediaPlayer?.let { player ->
                    if (!player.isPlaying) {
                        Log.d(TAG, "play: About to call player.start()")
                        player.start()
                        
                        // Start tracking playback time for analytics
                        playStartTime = System.currentTimeMillis()
                        Log.d(TAG, "play: Started tracking playback time at $playStartTime")
                        Log.d(TAG, "play: player.start() called, isPlaying: ${player.isPlaying}")
                        _isPlaying.postValue(true)
                        startProgressUpdates()
                        updateNotification()
                    } else {
                        // If already playing but no tracking has started, start it now
                        if (playStartTime == 0L) {
                            playStartTime = System.currentTimeMillis()
                            Log.d(TAG, "play: Started tracking playback time at $playStartTime for already playing media")
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "play: IllegalStateException", e)

                // Coba recovery kalo error
                _currentSong.value?.let { song ->
                    Log.d(TAG, "play: Attempting to recover")
                    prepareMediaPlayer(song)
                }
            }
        } else {
            Log.e(TAG, "play: Cannot play - audio focus denied after $attempts attempts")
        }
    }

    /**
     * Prepare ulang MediaPlayer kalo error.
     * Ini cuma prepare aja, ga langsung play.
     */
    private fun prepareMediaPlayer(song: Song) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                val uri = song.filePath.toUri()
                setDataSource(applicationContext, uri)
                prepare()

                // Jangan auto-play, ini cuma persiapan aja
                _duration.postValue(duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepareMediaPlayer: Error preparing player", e)
        }
    }

    /**
     * Pause pemutaran lagu.
     */
    fun pause() {
        Log.d(TAG, "pause: Pausing playback")
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    Log.d(TAG, "pause: Playback paused")
                    
                    // Calculate and record listening time for analytics
                    if (playStartTime > 0) {
                        val currentTime = System.currentTimeMillis()
                        val duration = currentTime - playStartTime
                        totalPlayTimeInSession += duration
                        
                        // Record the listening session in the database
                        _currentSong.value?.let { song ->
                            val userId = tokenManager.getEmail()
                            if (!userId.isNullOrEmpty()) {
                                lifecycleScope.launch {
                                    analyticsRepository.recordSongListening(userId, song, duration)
                                    Log.d(TAG, "pause: Recorded ${duration}ms of listening time for ${song.title}")
                                }
                            }
                        }
                        
                        // Reset the start time
                        playStartTime = 0
                    } else {
                        // Not recording analytics since playback just started
                        Log.d(TAG, "pause: No listening time to record")
                    }
                } else {
                    Log.d(TAG, "pause: Player not playing, nothing to pause")
                }
            }
            _isPlaying.postValue(false)
            stopProgressUpdates()
            updateNotification()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "pause: IllegalStateException", e)
            // Reset player kalo di state yang invalid
            _currentSong.value?.let { prepareMediaPlayer(it) }
        }
    }

    /**
     * Loncat ke posisi tertentu dalam lagu.
     * Dipanggil waktu user ngegeser progress bar lagu.
     */
    fun seekTo(position: Int) {
        Log.d(TAG, "seekTo: Seeking to $position ms")
        mediaPlayer?.let { player ->
            try {
                player.seekTo(position)
                _progress.postValue(position)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "seekTo: IllegalStateException", e)
            }
        } ?: run {
            Log.e(TAG, "seekTo: Cannot seek - MediaPlayer is null")
        }
    }

    /**
     * Ambil posisi current pemutaran dalam millisecond.
     */
    fun getCurrentPosition(): Int {
        return try {
            val position = mediaPlayer?.currentPosition ?: 0
            Log.v(TAG, "getCurrentPosition: $position ms")
            position
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentPosition: Error", e)
            0
        }
    }

    /**
     * Ambil durasi total lagu dalam millisecond.
     */
    fun getDuration(): Int {
        return try {
            val duration = mediaPlayer?.duration ?: 0
            Log.v(TAG, "getDuration: $duration ms")
            duration
        } catch (e: Exception) {
            Log.e(TAG, "getDuration: Error", e)
            0
        }
    }

    /**
     * Minta permission audio focus ke sistem.
     * Ini penting biar ga tabrakan sama app lain yang muter audio.
     */
    private fun requestAudioFocus(): Boolean {
        Log.d(TAG, "requestAudioFocus: Starting focus request")

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                Log.d(TAG, "requestAudioFocus: Creating new AudioFocusRequest")
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                        mainHandler.postDelayed({
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                Log.d(TAG, "Delayed handling of AUDIOFOCUS_LOSS")
                                handleAudioFocusChange(focusChange)
                            } else {
                                handleAudioFocusChange(focusChange)
                            }
                        }, 200)
                    }
                    .build()
            }

            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    Log.d(TAG, "Audio focus changed (legacy): $focusChange")
                    // Kasih delay dikit buat focus loss
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        mainHandler.postDelayed({
                            handleAudioFocusChange(focusChange)
                        }, 200)
                    } else {
                        handleAudioFocusChange(focusChange)
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        Log.d(TAG, "requestAudioFocus: Result: $result")
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Handle perubahan audio focus dari sistem.
     * Misal kalo ada telepon masuk atau app lain minta focus.
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "handleAudioFocusChange: AUDIOFOCUS_LOSS")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "handleAudioFocusChange: AUDIOFOCUS_LOSS_TRANSIENT")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "handleAudioFocusChange: AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                // Ga pause, cuma kecilin volume
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "handleAudioFocusChange: AUDIOFOCUS_GAIN")
                mediaPlayer?.setVolume(1.0f, 1.0f)
                // Auto-play lagi cuma kalo sebelumnya emang lagi play
                if (_isPlaying.value == true) {
                    play()
                }
            }
        }
    }

    /**
     * Lepasin audio focus kalo udah selesai.
     */
    private fun abandonAudioFocus() {
        // Lepasin cuma kalo kita emang punya request
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                Log.d(TAG, "abandonAudioFocus: Abandoning audio focus")
                audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    /**
     * Mulai update progress secara berkala.
     */
    private fun startProgressUpdates() {
        Log.d(TAG, "startProgressUpdates: Starting progress updates")
        progressUpdateJob?.let { mainHandler.post(it) }
    }

    /**
     * Berhenti update progress.
     */
    private fun stopProgressUpdates() {
        Log.d(TAG, "stopProgressUpdates: Stopping progress updates")
        progressUpdateJob?.let { mainHandler.removeCallbacks(it) }
    }

    /**
     * Update progress player dan kirim ke LiveData.
     */
    private fun updateProgress() {
        mediaPlayer?.let {
            try {
                val position = it.currentPosition
                _progress.postValue(position)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "updateProgress: IllegalStateException", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Purrytify Player",
                NotificationManager.IMPORTANCE_LOW  // Important for media players
            )
            channel.description = "Purrytify Media Player Controls"
            channel.setShowBadge(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "createNotificationChannel: Notification channel created")
        }
    }

    /**
     * Bikin notifikasi player yang tampil saat musik diputar.
     * Termasuk tombol play/pause dan info lagu.
     */
    private fun createNotification(song: Song): Notification {
        Log.d(TAG, "createNotification: Creating notification for ${song.title}")

        // Create intent for when notification is clicked (opens player UI)
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_PLAYER", true)
            putExtra("SONG_ID", song.id)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Safely check playback state
        val isCurrentlyPlaying = try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error checking isPlaying state in notification", e)
            _isPlaying.value ?: false  // Use LiveData value as fallback
        }

        // Create action buttons
        val playPauseAction = if (isCurrentlyPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pause",
                createActionIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Play",
                createActionIntent(ACTION_PLAY)
            )
        }

        val previousAction = NotificationCompat.Action(
            R.drawable.ic_previous,
            "Previous",
            createActionIntent(ACTION_PREVIOUS)
        )

        val nextAction = NotificationCompat.Action(
            R.drawable.ic_next,
            "Next",
            createActionIntent(ACTION_NEXT)
        )

        val stopAction = NotificationCompat.Action(
            R.drawable.ic_close,
            "Stop",
            createActionIntent(ACTION_STOP)
        )

        // Get album art for large icon
        var largeIcon = BitmapFactory.decodeResource(resources, R.drawable.placeholder_album)

        try {
            if (!song.coverUrl.isNullOrEmpty()) {
                // Try to load the album art synchronously for notification
                val bitmap = Glide.with(applicationContext)
                    .asBitmap()
                    .load(song.coverUrl)
                    .submit(144, 144)
                    .get()

                if (bitmap != null) {
                    largeIcon = bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading album art for notification", e)
        }

        // Build the enhanced notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)  // App logo
            .setLargeIcon(largeIcon)  // Album art
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .setOngoing(true)  // Can't be swiped away while playing
            .setDeleteIntent(createActionIntent(ACTION_STOP))  // Called when swiped away
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)  // Show prev, play/pause, next in compact view
            )
            .setShowWhen(false)  // Don't show timestamp

        // Add all action buttons
        builder.addAction(previousAction)
        builder.addAction(playPauseAction)
        builder.addAction(nextAction)
        builder.addAction(stopAction)

        Log.d(TAG, "createNotification: Enhanced notification built successfully")
        return builder.build()
    }

    /**
     * Update notifikasi player.
     * Dipanggil saat status play/pause berubah.
     */
    private fun updateNotification() {
        try {
            Log.d(TAG, "updateNotification: Updating notification")
            _currentSong.value?.let { song ->
                val notification = createNotification(song)
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "updateNotification: Notification updated")
            } ?: run {
                Log.d(TAG, "updateNotification: No current song, skipping notification update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }

    /**
     * Bikin PendingIntent buat action di notifikasi.
     */
    private fun createActionIntent(action: String): PendingIntent {
        Log.d(TAG, "createActionIntent: Creating action intent for $action")
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            this.action = action
        }

        // Use different request codes for different actions
        val requestCode = when(action) {
            ACTION_PLAY -> 0
            ACTION_PAUSE -> 1
            ACTION_PREVIOUS -> 2
            ACTION_NEXT -> 3
            ACTION_STOP -> 4
            else -> 5
        }

        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun playPrevious() {
        _currentSong.value?.let { currentSong ->
            lifecycleScope.launch {
                try {
                    val repository = SongRepository(
                        AppDatabase.getInstance(applicationContext).songDao(),
                        applicationContext
                    )

                    val previousSong = repository.getPreviousSong(currentSong.id)
                    previousSong?.let {
                        playSong(it)

                        // Update song stats
                        repository.updateLastPlayed(it.id)
                        repository.incrementPlayCount(it.id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing previous song: ${e.message}", e)
                }
            }
        }
    }

    fun playNext() {
        _currentSong.value?.let { currentSong ->
            lifecycleScope.launch {
                try {
                    val repository = SongRepository(
                        AppDatabase.getInstance(applicationContext).songDao(),
                        applicationContext
                    )

                    val nextSong = repository.getNextSong(currentSong.id)
                    nextSong?.let {
                        playSong(it)

                        // Update song stats
                        repository.updateLastPlayed(it.id)
                        repository.incrementPlayCount(it.id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing next song: ${e.message}", e)
                }
            }
        }
    }

    fun stopPlayback() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        mediaPlayer?.reset()

        // Update LiveData
        _isPlaying.postValue(false)
        _progress.postValue(0)

        stopProgressUpdates()
    }

    /**
     * Handle intent dari notifikasi player.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Action=${intent?.action}")

        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> {
                    Log.d(TAG, "onStartCommand: Executing ACTION_PLAY")
                    play()
                }
                ACTION_PAUSE -> {
                    Log.d(TAG, "onStartCommand: Executing ACTION_PAUSE")
                    pause()
                }
                ACTION_PREVIOUS -> {
                    Log.d(TAG, "onStartCommand: Executing ACTION_PREVIOUS")
                    playPrevious()
                }
                ACTION_NEXT -> {
                    Log.d(TAG, "onStartCommand: Executing ACTION_NEXT")
                    playNext()
                }
                ACTION_STOP -> {
                    Log.d(TAG, "onStartCommand: Executing ACTION_STOP")
                    stopPlayback()
                    stopForeground(true)
                    stopSelf()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Bersihkan semua resource waktu service dihancurkan.
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Service being destroyed")
        stopProgressUpdates()

        // Kalo lagi play, update state dulu biar observer dapet notif
        if (mediaPlayer?.isPlaying == true) {
            _isPlaying.postValue(false)
        }

        // Bebasin MediaPlayer
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
                Log.d(TAG, "onDestroy: MediaPlayer released")
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy: Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null

        // Bersihkan resource lainnya
        mediaSession?.release()
        abandonAudioFocus()
        Log.d(TAG, "onDestroy: Cleanup complete")

        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "purrytify_media_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_PLAY = "com.example.purrytify.service.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.purrytify.service.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.example.purrytify.service.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.purrytify.service.ACTION_NEXT"
        const val ACTION_STOP = "com.example.purrytify.service.ACTION_STOP"
    }
}