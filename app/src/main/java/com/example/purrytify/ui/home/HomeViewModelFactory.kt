package com.example.purrytify.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.ChartRepository
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.data.repository.UserRepository

class HomeViewModelFactory(
    private val songRepository: SongRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val tokenManager = TokenManager(context)
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                songRepository,
                context,
                UserRepository(tokenManager),
                ChartRepository(tokenManager, songRepository)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}