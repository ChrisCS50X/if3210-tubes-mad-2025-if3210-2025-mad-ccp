package com.example.purrytify.util

import com.example.purrytify.data.model.Song

interface MiniPlayerController {
    fun showMiniPlayer(song: Song)
    fun hideMiniPlayer()
    fun updateMiniPlayerState(isPlaying: Boolean)
}