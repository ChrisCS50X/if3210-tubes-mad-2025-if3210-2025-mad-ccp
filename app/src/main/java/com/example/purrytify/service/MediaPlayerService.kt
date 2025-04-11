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
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.ui.main.MainActivity
import java.io.IOException
import android.util.Log

class MediaPlayerService : LifecycleService() {
    private val TAG = "MediaPlayerService"

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> = _progress

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    private val binder = LocalBinder()
    private var progressUpdateJob: Runnable? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service created")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupMediaSession()
        checkAudioSettings()

        progressUpdateJob = Runnable {
            updateProgress()
            progressUpdateJob?.let {
                mainHandler.postDelayed(it, 500)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind: Service bound")
        super.onBind(intent)
        return binder
    }

    private fun setupMediaSession() {
        Log.d(TAG, "setupMediaSession: Setting up media session")
        mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession").apply {
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
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession callback: onSkipToPrevious")
                }

                override fun onSeekTo(pos: Long) {
                    Log.d(TAG, "MediaSession callback: onSeekTo: $pos")
                    seekTo(pos.toInt())
                }
            })
        }
    }

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

    fun playSong(song: Song) {
        try {
            Log.d(TAG, "playSong: Attempting to play: ${song.title}")
            Log.d(TAG, "playSong: File path: ${song.filePath}")

            // Release any existing MediaPlayer
            mediaPlayer?.let {
                Log.d(TAG, "playSong: Releasing existing MediaPlayer")
                it.stop()
                it.release()
            }

            // Create and prepare a new MediaPlayer
            Log.d(TAG, "playSong: Creating new MediaPlayer instance")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer onPrepared: Ready to play")
                }

                setOnCompletionListener {
                    Log.d(TAG, "MediaPlayer onCompletion: Playback completed")
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer onError: what=$what, extra=$extra")
                    false
                }

                setOnInfoListener { _, what, extra ->
                    Log.d(TAG, "MediaPlayer onInfo: what=$what, extra=$extra")
                    true
                }

                try {
                    // Process the URI based on its format
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
                } catch (e: IOException) {
                    Log.e(TAG, "playSong: Error preparing MediaPlayer", e)
                    return
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "playSong: IllegalStateException preparing MediaPlayer", e)
                    return
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "playSong: IllegalArgumentException with URI", e)
                    return
                }
            }

            _currentSong.postValue(song)
            Log.d(TAG, "playSong: Song set, starting playback")
            play()
            Log.d(TAG, "playSong: Starting foreground service with notification")
            startForeground(NOTIFICATION_ID, createNotification(song))

        } catch (e: Exception) {
            Log.e(TAG, "playSong: Unexpected error", e)
        }
    }

    fun play() {
        Log.d(TAG, "play: Method called, current player state: ${mediaPlayer?.isPlaying}")

        if (mediaPlayer == null) {
            Log.e(TAG, "play: Cannot play - MediaPlayer is null")
            return
        }

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
                        Log.d(TAG, "play: player.start() called, isPlaying: ${player.isPlaying}")
                        _isPlaying.postValue(true)
                        startProgressUpdates()
                        updateNotification()
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "play: IllegalStateException", e)

                // Try to recover
                _currentSong.value?.let { song ->
                    Log.d(TAG, "play: Attempting to recover")
                    prepareMediaPlayer(song)
                }
            }
        } else {
            Log.e(TAG, "play: Cannot play - audio focus denied after $attempts attempts")
        }
    }


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

                // Don't automatically start playing since this is just preparation
                _duration.postValue(duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepareMediaPlayer: Error preparing player", e)
        }
    }

    fun pause() {
        Log.d(TAG, "pause: Pausing playback")
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    Log.d(TAG, "pause: Playback paused")
                } else {
                    Log.d(TAG, "pause: Player not playing, nothing to pause")
                }
            }
            _isPlaying.postValue(false)
            stopProgressUpdates()
            updateNotification()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "pause: IllegalStateException", e)
            // Reset the player if in an invalid state
            _currentSong.value?.let { prepareMediaPlayer(it) }
        }
    }

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
                    // Add a small delay for focus loss
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
                // Don't pause, just lower volume
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "handleAudioFocusChange: AUDIOFOCUS_GAIN")
                mediaPlayer?.setVolume(1.0f, 1.0f)
                // Only auto-play if we were previously playing
                if (_isPlaying.value == true) {
                    play()
                }
            }
        }
    }

    private fun abandonAudioFocus() {
        // Only abandon if we actually have a request
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

    private fun startProgressUpdates() {
        Log.d(TAG, "startProgressUpdates: Starting progress updates")
        progressUpdateJob?.let { mainHandler.post(it) }
    }

    private fun stopProgressUpdates() {
        Log.d(TAG, "stopProgressUpdates: Stopping progress updates")
        progressUpdateJob?.let { mainHandler.removeCallbacks(it) }
    }

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

    private fun createNotification(song: Song): Notification {
        Log.d(TAG, "createNotification: Creating notification for ${song.title}")
        // Only create channel on Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Purrytify Player",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Purrytify Media Player Controls"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "createNotification: Notification channel created")
        }

        // Create intent for when notification is clicked
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Add play/pause action
        val isCurrentlyPlaying = mediaPlayer?.isPlaying ?: false
        Log.d(TAG, "createNotification: Current playback state: isPlaying=$isCurrentlyPlaying")

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
        builder.addAction(playPauseAction)

        Log.d(TAG, "createNotification: Notification built successfully")
        return builder.build()
    }

    private fun updateNotification() {
        Log.d(TAG, "updateNotification: Updating notification")
        _currentSong.value?.let { song ->
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification(song))
            Log.d(TAG, "updateNotification: Notification updated")
        } ?: run {
            Log.d(TAG, "updateNotification: No current song, skipping notification update")
        }
    }

    private fun createActionIntent(action: String): PendingIntent {
        Log.d(TAG, "createActionIntent: Creating action intent for $action")
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Service being destroyed")
        stopProgressUpdates()

        // If we're playing, update isPlaying state first so observers get notified
        if (mediaPlayer?.isPlaying == true) {
            _isPlaying.postValue(false)
        }

        // Release media player
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

        // Clean up other resources
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
    }
}