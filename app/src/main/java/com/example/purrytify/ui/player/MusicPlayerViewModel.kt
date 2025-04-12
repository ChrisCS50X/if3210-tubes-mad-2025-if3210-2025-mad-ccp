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
import java.util.LinkedList
import kotlin.random.Random

/**
 * Enum class to represent different repeat modes
 */
enum class RepeatMode {
    OFF,      // No repeat
    ALL,      // Repeat all songs
    ONE       // Repeat current song
}

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
    
    // Shuffle state
    private val _isShuffleEnabled = MutableLiveData<Boolean>(false)
    val isShuffleEnabled: LiveData<Boolean> = _isShuffleEnabled
    
    // Repeat mode - starts with OFF
    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.OFF)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    // Queue untuk menyimpan lagu-lagu yang antri
    private val _queue = LinkedList<Song>()
    
    // Original queue order for when shuffle is disabled
    private val _originalQueue = mutableListOf<Song>()

    // LiveData untuk UI yang mau nampilin queue
    private val _queueLiveData = MutableLiveData<List<Song>>(emptyList())
    val queueLiveData: LiveData<List<Song>> = _queueLiveData

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
    
    fun toggleShuffle() {
        val currentShuffleState = _isShuffleEnabled.value ?: false
        val newState = !currentShuffleState
        
        Log.d("MusicPlayerViewModel", "Toggling shuffle from $currentShuffleState to $newState")
        
        _isShuffleEnabled.value = newState
        
        if (_isShuffleEnabled.value == true) {
            // Enable shuffle
            if (_queue.isNotEmpty()) {
                // Save the original queue order
                _originalQueue.clear()
                _originalQueue.addAll(_queue)
                
                // Shuffle the queue
                val tempList = _queue.toMutableList()
                tempList.shuffle()
                
                // Update the queue
                _queue.clear()
                _queue.addAll(tempList)
                _queueLiveData.value = _queue.toList()
            }
        } else {
            // Disable shuffle, restore original order
            if (_originalQueue.isNotEmpty()) {
                _queue.clear()
                _queue.addAll(_originalQueue)
                _queueLiveData.value = _queue.toList()
            }
        }
        
        Log.d("MusicPlayerViewModel", "Shuffle is now ${if (_isShuffleEnabled.value == true) "enabled" else "disabled"}")
    }

    fun toggleRepeatMode() {
        val currentMode = _repeatMode.value ?: RepeatMode.OFF
        val newMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = newMode
        Log.d("MusicPlayerViewModel", "Repeat mode changed to $newMode")
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

    fun addToQueue(song: Song) {
        _queue.add(song)
        if (_isShuffleEnabled.value == false) {
            _originalQueue.add(song)
        }
        _queueLiveData.value = _queue.toList()
    }

    fun removeFromQueue(song: Song) {
        _queue.remove(song)
        _originalQueue.remove(song)
        _queueLiveData.value = _queue.toList()
    }

    fun clearQueue() {
        _queue.clear()
        _originalQueue.clear()
        _queueLiveData.value = emptyList()
    }

    fun playNext() {
        viewModelScope.launch {
            // Handle repeat mode first - if in repeat one mode, just replay the current song
            if (_repeatMode.value == RepeatMode.ONE) {
                currentSong.value?.let { 
                    playSong(it) 
                    return@launch
                }
            }
            
            if (_queue.isNotEmpty()) {
                // Ambil lagu pertama dari queue & hapus dari antrian
                val nextSong = _queue.removeFirst()
                if (_isShuffleEnabled.value == false && _originalQueue.isNotEmpty()) {
                    _originalQueue.remove(nextSong)
                }
                _queueLiveData.postValue(_queue.toList())

                // Mainin lagu dari queue
                playSong(nextSong)

                // Update database
                nextSong.id.let { songId ->
                    songRepository.updateLastPlayed(songId)
                    songRepository.incrementPlayCount(songId)
                }
            } else {
                currentSong.value?.let { currentSong ->
                    if (_isShuffleEnabled.value == true) {
                        viewModelScope.launch {
                            val allSongs = songRepository.getAllSongsOrdered()
                            if (allSongs.isNotEmpty()) {
                                val randomIndex = Random.nextInt(allSongs.size)
                                val randomSong = allSongs[randomIndex]
                                playSong(randomSong)

                                songRepository.updateLastPlayed(randomSong.id)
                                songRepository.incrementPlayCount(randomSong.id)
                            }
                        }
                    } else {
                        val nextSong = songRepository.getNextSong(currentSong.id)
                        nextSong?.let {
                            playSong(it)

                            songRepository.updateLastPlayed(it.id)
                            songRepository.incrementPlayCount(it.id)
                        }
                    }
                }
            }

            // Handle repeat all mode - restore queue if it's empty and we're in repeat all
            if (_repeatMode.value == RepeatMode.ALL && _queue.isEmpty()) {
                if (_originalQueue.isNotEmpty()) {
                    _queue.addAll(_originalQueue)
                    _queueLiveData.postValue(_queue.toList())
                }
            }
        }
    }

    fun playPrevious() {
        viewModelScope.launch {
            // Jika dalam mode repeat one, putar lagi lagu yang sama
            if (_repeatMode.value == RepeatMode.ONE) {
                currentSong.value?.let { 
                    playSong(it) 
                    return@launch
                }
            }
            
            currentSong.value?.let { currentSong ->
                if (_isShuffleEnabled.value == true) {
                    // In shuffle mode, get a random song
                    viewModelScope.launch {
                        val allSongs = songRepository.getAllSongsOrdered()
                        if (allSongs.isNotEmpty()) {
                            val randomIndex = Random.nextInt(allSongs.size)
                            val randomSong = allSongs[randomIndex]
                            playSong(randomSong)
                            
                            // Update database
                            songRepository.updateLastPlayed(randomSong.id)
                            songRepository.incrementPlayCount(randomSong.id)
                        }
                    }
                } else {
                    // Normal sequential playback
                    val previousSong = songRepository.getPreviousSong(currentSong.id)
                    previousSong?.let {
                        playSong(it)

                        // Update database
                        songRepository.updateLastPlayed(it.id)
                        songRepository.incrementPlayCount(it.id)
                    }
                }
            }
        }
    }
}