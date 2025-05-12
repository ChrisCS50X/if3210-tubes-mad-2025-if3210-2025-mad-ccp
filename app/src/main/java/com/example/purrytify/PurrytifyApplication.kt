package com.example.purrytify

import android.app.Application
import androidx.work.Configuration
import com.example.purrytify.service.DownloadManager

class PurrytifyApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        // Initialize download notification channel
        DownloadManager(this).createNotificationChannel()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
}