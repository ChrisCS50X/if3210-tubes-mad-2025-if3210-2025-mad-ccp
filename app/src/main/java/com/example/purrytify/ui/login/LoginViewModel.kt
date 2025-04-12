package com.example.purrytify.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val userRepository: UserRepository) : ViewModel() {
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Email and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = userRepository.login(email, password)
            _loginState.value = when {
                result.isSuccess -> LoginState.Success
                else -> {
                    val rawMessage = result.exceptionOrNull()?.message
                    val sanitizedMessage = mapServerErrorToMessage(rawMessage)
                    LoginState.Error(sanitizedMessage)
                }
            }
        }
    }

    private fun mapServerErrorToMessage(rawMessage: String?): String {
        return when {
            rawMessage?.contains("401", ignoreCase = true) == true -> {
                "Email or password is incorrect. Please try again."
            }
            rawMessage?.contains("500", ignoreCase = true) == true -> {
                "Server is currently unavailable. Please try again later."
            }
            else -> "An unexpected error occurred. Please check your connection or try again."
        }
    }
}

sealed class LoginState {
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}