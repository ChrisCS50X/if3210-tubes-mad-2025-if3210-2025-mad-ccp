package com.example.purrytify.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import kotlinx.coroutines.launch

class HomeViewModel(private val songRepository: SongRepository) : ViewModel() {

    private val _newSongs = MutableLiveData<List<Song>>()
    val newSongs: LiveData<List<Song>> = _newSongs

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadHomeData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _newSongs.value = songRepository.getNewSongs()
                _recentlyPlayed.value = songRepository.getRecentlyPlayed()
            } catch (e: Exception) {
                // Handle error, perhaps add an error state
            } finally {
                _isLoading.value = false
            }
        }
    }
}