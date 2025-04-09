package com.example.purrytify.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SongRepository

    val allSongs: LiveData<List<Song>>
    val likedSongs: LiveData<List<Song>>

    init {
        val songDao = AppDatabase.getInstance(application).songDao()
        repository = SongRepository(songDao)

        allSongs = repository.allSongs.asLiveData()
        likedSongs = repository.likedSongs.asLiveData()
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            repository.incrementPlayCount(song.id)
            // Handle playback (will be implemented in another part)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            repository.toggleLikedStatus(song.id, !song.isLiked)
        }
    }

    fun addSong(song: Song) {
        viewModelScope.launch {
            repository.insertSong(song)
        }
    }
}

class LibraryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}