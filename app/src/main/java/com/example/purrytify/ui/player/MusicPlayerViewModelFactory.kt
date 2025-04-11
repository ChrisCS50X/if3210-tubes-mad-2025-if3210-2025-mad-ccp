package com.example.purrytify.ui.player

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.repository.SongRepository

class MusicPlayerViewModelFactory(
    private val application: Application,
    private val songRepository: SongRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicPlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicPlayerViewModel(application, songRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}