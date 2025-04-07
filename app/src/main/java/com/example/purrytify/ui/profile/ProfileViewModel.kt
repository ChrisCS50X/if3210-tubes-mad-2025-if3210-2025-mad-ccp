package com.example.purrytify.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.UserProfile
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider

class ProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _profileState = MutableLiveData<ProfileState>()
    val profileState: LiveData<ProfileState> = _profileState

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
}

sealed class ProfileState {
    data object Loading : ProfileState()
    data class Success(val profile: UserProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModelFactory(
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
