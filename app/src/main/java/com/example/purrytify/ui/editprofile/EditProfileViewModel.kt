package com.example.purrytify.ui.editprofile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class EditProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _updateState = MutableLiveData<UpdateProfileState>()
    val updateState: LiveData<UpdateProfileState> get() = _updateState

    // Update profile info
    fun updateProfile(location: String?, photoFile: File?) {
        _updateState.value = UpdateProfileState.Loading

        viewModelScope.launch {
            try {
                // Create multipart form data
                val parts = mutableListOf<MultipartBody.Part>()

                // Add location if provided
                if (!location.isNullOrEmpty()) {
                    val locationPart = MultipartBody.Part.createFormData("location", location)
                    parts.add(locationPart)
                }

                // Add photo if provided
                if (photoFile != null && photoFile.exists()) {
                    val photoRequestBody = photoFile.asRequestBody("image/*".toMediaTypeOrNull())
                    val photoPart = MultipartBody.Part.createFormData("profilePhoto", photoFile.name, photoRequestBody)
                    parts.add(photoPart)
                }

                // Call the API
                val result = userRepository.updateProfile(parts)

                // Handle the response using Result's fold method
                result.fold(
                    // Log the API response for debugging

                    onSuccess = { apiResponse ->
                        Log.d("API_RESPONSE", "Response: $apiResponse")
                        if (apiResponse.message == "Profile updated successfully") {
                            _updateState.value = UpdateProfileState.Success
                        } else {
                            _updateState.value = UpdateProfileState.Error("Failed to update profile: ${apiResponse.message}")
                        }
                    },
                    onFailure = { exception ->
                        _updateState.value = UpdateProfileState.Error("Error: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _updateState.value = UpdateProfileState.Error("Error: ${e.message}")
            }
        }
    }
}

// State for profile update process
sealed class UpdateProfileState {
    object Loading : UpdateProfileState()
    object Success : UpdateProfileState()
    data class Error(val message: String) : UpdateProfileState()
}