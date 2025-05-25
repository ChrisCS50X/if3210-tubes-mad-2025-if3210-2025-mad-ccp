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

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

class MusicPlayerViewModel(
    application: Application,
    private val songRepository: SongRepository
) : AndroidViewModel(application) {

    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false

    private val _isServiceConnected = MutableLiveData<Boolean>()
    val isServiceConnected: LiveData<Boolean> = _isServiceConnected

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> = _progress

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    private val _isShuffleEnabled = MutableLiveData<Boolean>(false)
    val isShuffleEnabled: LiveData<Boolean> = _isShuffleEnabled

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.OFF)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    private val _isCurrentSongLiked = MutableLiveData<Boolean>(false)
    val isCurrentSongLiked: LiveData<Boolean> = _isCurrentSongLiked

    private val _queue = LinkedList<Song>()

    private val _originalQueue = mutableListOf<Song>()

    private val _queueLiveData = MutableLiveData<List<Song>>(emptyList())
    val queueLiveData: LiveData<List<Song>> = _queueLiveData

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MusicPlayerViewModel", "Service connected")
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            bound = true
            _isServiceConnected.value = true

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
        bindService()
    }

    private fun observeServiceLiveData() {
        Log.d("MusicPlayerViewModel", "Setting up LiveData observers for service")
        mediaPlayerService?.let { service ->
            service.currentSong.observeForever { song ->
                Log.d("MusicPlayerViewModel", "Song updated from service: ${song?.title}")
                _currentSong.postValue(song)

                // Update like status saat lagu berubah
                song?.let { updateLikeStatus(it.id) }
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
            context.startService(intent)
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                songRepository.updateLastPlayed(song.id)
                songRepository.incrementPlayCount(song.id)
            } catch (e: Exception) {
                Log.e("MusicPlayerViewModel", "Error updating song stats: ${e.message}")
            }
        }

        mediaPlayerService?.playSong(song)
    }

    private fun updateLikeStatus(songId: Long) {
        viewModelScope.launch {
            try {
                val isLiked = songRepository.getLikedStatusBySongId(songId)
                Log.d("MusicPlayerViewModel", "Updating like status for song $songId: $isLiked")
                _isCurrentSongLiked.postValue(isLiked)
            } catch (e: Exception) {
                Log.e("MusicPlayerViewModel", "Error checking like status: ${e.message}")
                _isCurrentSongLiked.postValue(false)
            }
        }
    }

    fun toggleLikeStatus() {
        viewModelScope.launch {
            currentSong.value?.let { song ->
                try {
                    val currentStatus = _isCurrentSongLiked.value ?: false
                    val newStatus = !currentStatus

                    Log.d("MusicPlayerViewModel", "Toggling like status for ${song.title}: $currentStatus -> $newStatus")

                    songRepository.updateLikeStatus(song.id, newStatus)
                    _isCurrentSongLiked.postValue(newStatus)

                    Log.d("MusicPlayerViewModel", "Like status successfully updated: ${song.title} -> $newStatus")
                } catch (e: Exception) {
                    Log.e("MusicPlayerViewModel", "Error toggling like status: ${e.message}")
                    // Revert to current status on error
                    updateLikeStatus(song.id)
                }
            } ?: run {
                Log.w("MusicPlayerViewModel", "Cannot toggle like status: no current song")
            }
        }
    }

    fun refreshLikeStatus() {
        currentSong.value?.let { song ->
            updateLikeStatus(song.id)
        }
    }

    fun handleSongDeleted(songId: Long) {
        if (currentSong.value?.id == songId) {
            stopPlayback()

            _currentSong.postValue(null)

            _isPlaying.postValue(false)
            _progress.postValue(0)
            _duration.postValue(100)
            _isCurrentSongLiked.postValue(false) // Reset like status

            _queue.removeIf { it.id == songId }
            _originalQueue.removeIf { it.id == songId }
            _queueLiveData.postValue(_queue.toList())
        } else {
            _queue.removeIf { it.id == songId }
            _originalQueue.removeIf { it.id == songId }
            _queueLiveData.postValue(_queue.toList())
        }
    }

    private fun stopPlayback() {
        mediaPlayerService?.stopPlayback()
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
            if (_queue.isNotEmpty()) {
                _originalQueue.clear()
                _originalQueue.addAll(_queue)

                val tempList = _queue.toMutableList()
                tempList.shuffle()

                _queue.clear()
                _queue.addAll(tempList)
                _queueLiveData.value = _queue.toList()
            }
        } else {
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
            if (_repeatMode.value == RepeatMode.ONE) {
                currentSong.value?.let {
                    playSong(it)
                    return@launch
                }
            }

            if (_queue.isNotEmpty()) {
                val nextSong = _queue.removeFirst()
                if (_isShuffleEnabled.value == false && _originalQueue.isNotEmpty()) {
                    _originalQueue.remove(nextSong)
                }
                _queueLiveData.postValue(_queue.toList())

                playSong(nextSong)

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
            if (_repeatMode.value == RepeatMode.ONE) {
                currentSong.value?.let {
                    playSong(it)
                    return@launch
                }
            }

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
                    val previousSong = songRepository.getPreviousSong(currentSong.id)
                    previousSong?.let {
                        playSong(it)

                        songRepository.updateLastPlayed(it.id)
                        songRepository.incrementPlayCount(it.id)
                    }
                }
            }
        }
    }
}