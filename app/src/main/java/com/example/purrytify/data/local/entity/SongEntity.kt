package com.example.purrytify.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * Entity class yang mewakili tabel lagu di database lokal.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String?,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val filePath: String,
    val duration: Long,
    val isLiked: Boolean = false,
    val playCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0
)