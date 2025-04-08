package com.example.purrytify.ui.main


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tubesmobile.purrytify.util.NetworkMonitor

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val networkManager = NetworkMonitor(application)
    val isConnected = networkManager.isConnected

    init {
        networkManager.startMonitoring()
    }

    override fun onCleared() {
        networkManager.stopMonitoring()
        super.onCleared()
    }
}