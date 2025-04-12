package com.example.purrytify.data.model

/**
 * Model untuk data profile user dari API.
 * Berisi informasi lengkap tentang user yang login.
 */
data class UserProfile(
    val id: Int,
    val username: String,
    val email: String,
    val profilePhoto: String,
    val location: String, // kode negara, misal "ID" untuk Indonesia
    val createdAt: String,
    val updatedAt: String
)