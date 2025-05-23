package com.example.purrytify.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.data.repository.UserRepository

class ProfileViewModelFactory(
    private val userRepository: UserRepository,
    private val songRepository: SongRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(userRepository, songRepository, analyticsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}