package com.example.purrytify.ui.library

import android.app.Application
import android.content.Context
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

class LibraryViewModel(application: Application, context: Context) : AndroidViewModel(application) {

    private val repository: SongRepository

    val allSongs: LiveData<List<Song>>
    val likedSongs: LiveData<List<Song>>
    val downloadedSongs: LiveData<List<Song>>

    init {
        val songDao = AppDatabase.getInstance(application).songDao()
        repository = SongRepository(songDao, context)

        allSongs = repository.allSongs.asLiveData()
        likedSongs = repository.likedSongs.asLiveData()
        downloadedSongs = repository.downloadedSongs.asLiveData()
    }

    fun deleteSongById(songId: Long) {
        viewModelScope.launch {
            repository.deleteSong(songId)
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            repository.incrementPlayCount(song.id)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            repository.toggleLikedStatus(song.id, !song.isLiked)
        }
    }

    fun addSong(song: Song, context: Context) {
        viewModelScope.launch {
            repository.insertSong(song, context)
        }
    }

    fun updateSong(song: Song, context: Context) {
        viewModelScope.launch {
            repository.updateSong(song, context)
        }
    }
}

class LibraryViewModelFactory(private val application: Application, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(application, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}