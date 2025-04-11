package com.example.purrytify.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.UserProfile
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.SongRepository

class ProfileViewModel(private val userRepository: UserRepository, private val songRepository: SongRepository) : ViewModel() {

    private val _profileState = MutableLiveData<ProfileState>()
    val profileState: LiveData<ProfileState> = _profileState
    private val _logoutEvent = MutableLiveData<Boolean>()
    val logoutEvent: LiveData<Boolean> = _logoutEvent
    private val _likedCount = MutableLiveData<Int>()
    val likedCount: LiveData<Int> get() = _likedCount
    private val _userId = MutableLiveData<String?>()


    fun loadUserProfile() {
        _profileState.value = ProfileState.Loading

        viewModelScope.launch {
            val result = userRepository.getUserProfile()
            _profileState.value = when {
                result.isSuccess -> ProfileState.Success(result.getOrThrow())
                else -> ProfileState.Error(result.exceptionOrNull()?.message ?: "Failed to load profile")
            }
        }
    }

    fun setUserId(userId: String?) {
        _userId.value = userId
        loadLikedCount(userId)
    }

    private fun loadLikedCount(userId: String?) {
        viewModelScope.launch {
            songRepository.getLikedSongsCount(userId)
                .collect { count ->
                    _likedCount.postValue(count)
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
            _logoutEvent.postValue(true)
        }
    }
}

sealed class ProfileState {
    data object Loading : ProfileState()
    data class Success(val profile: UserProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModelFactory(
    private val userRepository: UserRepository,
    private val songRepository: SongRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(userRepository, songRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
