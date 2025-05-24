package com.example.purrytify.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Entity class for storing listening statistics in the database
 */
@Entity(
    tableName = "listening_stats",
    indices = [
        Index(value = ["userId", "songId", "date"], unique = true)
    ]
)
data class ListeningStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val songId: Long,
    val songTitle: String,
    val artist: String,
    val date: String, // Stored as ISO date format (YYYY-MM-DD)
    val listeningDurationMillis: Long, // Duration listened in milliseconds
    val timestamp: Long = System.currentTimeMillis() // When this record was created
)