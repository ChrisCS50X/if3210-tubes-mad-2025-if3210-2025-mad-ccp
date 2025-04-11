package com.example.purrytify.ui.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.service.MediaPlayerService
import kotlinx.coroutines.launch
import android.util.Log

class MusicPlayerViewModel(
    application: Application,
    private val songRepository: SongRepository
) : AndroidViewModel(application) {

    // Service connection
    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false

    // LiveData
    private val _isServiceConnected = MutableLiveData<Boolean>()
    val isServiceConnected: LiveData<Boolean> = _isServiceConnected

    // Forward LiveData from service
    val currentSong: LiveData<Song?>
        get() = mediaPlayerService?.currentSong ?: MutableLiveData()

    val isPlaying: LiveData<Boolean>
        get() = mediaPlayerService?.isPlaying ?: MutableLiveData()

    val progress: LiveData<Int>
        get() = mediaPlayerService?.progress ?: MutableLiveData()

    val duration: LiveData<Int>
        get() = mediaPlayerService?.duration ?: MutableLiveData()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MusicPlayerViewModel", "Service connected")
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            bound = true
            _isServiceConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MusicPlayerViewModel", "Service disconnected")
            mediaPlayerService = null
            bound = false
            _isServiceConnected.value = false
        }
    }

    init {
        // Bind to the service when ViewModel is created
        bindService()
    }

    private fun bindService() {
        val context = getApplication<Application>()
        Intent(context, MediaPlayerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            context.startService(intent) // Also start the service to keep it running
        }
    }

    fun playSong(song: Song) {
        // Update the repository
        viewModelScope.launch {
            try {
                songRepository.updateLastPlayed(song.id)
                songRepository.incrementPlayCount(song.id)
            } catch (e: Exception) {
                // Handle error
            }
        }

        // Play the song in service
        mediaPlayerService?.playSong(song)
    }

    fun togglePlayPause() {
        mediaPlayerService?.let {
            if (it.isPlaying.value == true) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayerService?.seekTo(position)
    }

    fun getCurrentPosition(): Int {
        return mediaPlayerService?.getCurrentPosition() ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayerService?.getDuration() ?: 0
    }

    override fun onCleared() {
        super.onCleared()
        // Unbind from service when ViewModel is cleared
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
    }
}