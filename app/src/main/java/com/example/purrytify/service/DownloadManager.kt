package com.example.purrytify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val TAG = "DownloadManager"

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 200

        // Worker input data keys
        const val KEY_SONG_ID = "song_id"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_SONG_ARTIST = "song_artist"
        const val KEY_SONG_URL = "song_url"
        const val KEY_SONG_COVER_URL = "song_cover_url"
        const val KEY_SONG_DURATION = "song_duration"
    }

    fun enqueueDownload(song: Song): UUID {
        Log.d(TAG, "Enqueueing download for ${song.title}")

        // Create input data for worker
        val inputData = Data.Builder()
            .putLong(KEY_SONG_ID, song.id)
            .putString(KEY_SONG_TITLE, song.title)
            .putString(KEY_SONG_ARTIST, song.artist)
            .putString(KEY_SONG_URL, song.filePath)
            .putString(KEY_SONG_COVER_URL, song.coverUrl)
            .putLong(KEY_SONG_DURATION, song.duration)
            .build()

        // Create download work request
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // Enqueue download
        workManager.enqueue(downloadRequest)

        return downloadRequest.id
    }

    fun getDownloadStatus(workerId: UUID) = workManager.getWorkInfoByIdLiveData(workerId)

    // Create notification channel for downloads
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Song download notifications"
                enableVibration(false)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    class DownloadWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private val songRepository = SongRepository(
            com.example.purrytify.data.local.AppDatabase.getInstance(context).songDao(),
            context
        )

        private val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading song")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        override suspend fun doWork(): Result {
            val songId = inputData.getLong(KEY_SONG_ID, 0)
            val songTitle = inputData.getString(KEY_SONG_TITLE) ?: "Unknown"
            val songArtist = inputData.getString(KEY_SONG_ARTIST) ?: "Unknown"
            val songUrl = inputData.getString(KEY_SONG_URL) ?: return Result.failure()
            val songCoverUrl = inputData.getString(KEY_SONG_COVER_URL)
            val songDuration = inputData.getLong(KEY_SONG_DURATION, 0)

            Log.d("DownloadWorker", "Starting download for $songTitle")

            // Update notification
            val notification = notificationBuilder
                .setContentTitle("Downloading $songTitle")
                .setContentText("By $songArtist")
                .setProgress(100, 0, true)
                .build()

            notificationManager.notify(NOTIFICATION_ID + songId.toInt(), notification)

            return try {
                // Create download directory if it doesn't exist
                val downloadDir = File(applicationContext.filesDir, "downloads")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                // Generate a filename for the song
                val fileName = "${songId}_${songTitle.replace(" ", "_")}.mp3"
                val outputFile = File(downloadDir, fileName)

                // Download the file
                withContext(Dispatchers.IO) {
                    val url = URL(songUrl)
                    val connection = url.openConnection()
                    val contentLength = connection.contentLength

                    val input = url.openStream()
                    val output = FileOutputStream(outputFile)

                    val buffer = ByteArray(4 * 1024) // 4K buffer
                    var bytesRead: Int
                    var downloadedBytes = 0
                    var lastProgressUpdate = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress notification every 5%
                        val progress = if (contentLength > 0) {
                            (downloadedBytes * 100 / contentLength)
                        } else {
                            -1
                        }

                        if (progress >= lastProgressUpdate + 5 || progress == 100) {
                            lastProgressUpdate = progress
                            val progressNotification = notificationBuilder
                                .setContentTitle("Downloading $songTitle")
                                .setContentText("$progress%")
                                .setProgress(100, progress, false)
                                .build()

                            notificationManager.notify(NOTIFICATION_ID + songId.toInt(), progressNotification)
                        }
                    }

                    input.close()
                    output.close()
                }

                // Save to database after successful download
                val localFilePath = outputFile.absolutePath

                val downloadedSong = Song(
                    id = songId,
                    title = songTitle,
                    artist = songArtist,
                    coverUrl = songCoverUrl,
                    filePath = localFilePath,
                    duration = songDuration,
                    isLiked = false
                )

                // Update or insert the song in local database
                withContext(Dispatchers.IO) {
                    songRepository.saveSongFromOnline(downloadedSong)
                }

                // Show completion notification
                val completeNotification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_download_done)
                    .setContentTitle("Download Complete")
                    .setContentText("$songTitle has been downloaded")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID + songId.toInt(), completeNotification)

                Result.success()
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Download failed: ${e.message}", e)

                // Show error notification
                val errorNotification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_error)
                    .setContentTitle("Download Failed")
                    .setContentText("Failed to download $songTitle")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID + songId.toInt(), errorNotification)

                Result.failure()
            }
        }
    }
}