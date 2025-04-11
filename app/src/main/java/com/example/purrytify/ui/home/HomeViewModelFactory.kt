package com.example.purrytify.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.repository.SongRepository

class HomeViewModelFactory(private val songRepository: SongRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(songRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}