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

    // Create our own internal LiveData proxies
    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> = _progress

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MusicPlayerViewModel", "Service connected")
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            bound = true
            _isServiceConnected.value = true

            // Start observing service LiveData and relay values
            observeServiceLiveData()
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

    private fun observeServiceLiveData() {
        Log.d("MusicPlayerViewModel", "Setting up LiveData observers for service")
        mediaPlayerService?.let { service ->
            // Use observeForever since we need these observers to stay active
            service.currentSong.observeForever { song ->
                Log.d("MusicPlayerViewModel", "Song updated from service: ${song?.title}")
                _currentSong.postValue(song)
            }

            service.isPlaying.observeForever { playing ->
                Log.d("MusicPlayerViewModel", "isPlaying updated from service: $playing")
                _isPlaying.postValue(playing)
            }

            service.progress.observeForever { prog ->
                _progress.postValue(prog)
            }

            service.duration.observeForever { dur ->
                _duration.postValue(dur)
            }
        }
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
        Log.d("MusicPlayerViewModel", "togglePlayPause called, current state: ${mediaPlayerService?.isPlaying?.value}")
        mediaPlayerService?.let {
            if (it.isPlaying.value == true) {
                Log.d("MusicPlayerViewModel", "Calling pause()")
                it.pause()
            } else {
                Log.d("MusicPlayerViewModel", "Calling play()")
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

    fun playNextSong() {
        viewModelScope.launch {
            val currentSong = _currentSong.value ?: return@launch
            try {
                val nextSong = songRepository.getNextSong(currentSong.id)
                nextSong?.let {
                    playSong(it)
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerViewModel", "Error playing next song", e)
            }
        }
    }

    fun playPreviousSong() {
        viewModelScope.launch {
            val currentSong = _currentSong.value ?: return@launch
            try {
                val previousSong = songRepository.getPreviousSong(currentSong.id)
                previousSong?.let {
                    playSong(it)
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerViewModel", "Error playing previous song", e)
            }
        }
    }
}