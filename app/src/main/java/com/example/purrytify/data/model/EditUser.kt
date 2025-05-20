package com.example.purrytify.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EditProfile(
    val username: String,
    val location: String,
    val profilePhoto: String
) : Parcelable