package com.example.purrytify.data.repository

import android.content.Context
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.local.dao.SongDao
import com.example.purrytify.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.purrytify.data.mapper.toDomainModel
import com.example.purrytify.data.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log


/**
 * Repository untuk managemen data lagu.
 * Bertanggung jawab buat komunikasi antara UI dan data source (database lokal).
 * Semua operasi database dibungkus di kelas ini.
 */
class SongRepository(private val songDao: SongDao, private val context: Context) {
    private val tokenManager = TokenManager(context)

    /**
     * Ambil semua lagu user yang lagi login, dalam bentuk Flow.
     * Flow ini akan otomatis update UI kalo ada perubahan data.
     */
    val allSongs = songDao.getAllSongsByUserId(tokenManager.getEmail()).map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * Ambil semua lagu yang di-like user, dalam bentuk Flow.
     * Buat nampilin "Liked Songs" playlist.
     */
    val likedSongs = songDao.getLikedSongs().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * Tambahin counter berapa kali lagu udah diputar.
     */
    suspend fun incrementPlayCount(songId: Long) {
        songDao.incrementPlayCount(songId)
    }

    /**
     * Hapus lagu dari library.
     */
    suspend fun deleteSong(songId: Long) {
        songDao.deleteSongById(songId)
    }

    /**
     * Ubah status like/unlike lagu.
     */
    suspend fun toggleLikedStatus(songId: Long, isLiked: Boolean) {
        songDao.updateLikedStatus(songId, isLiked)
    }

    /**
     * Tambahin lagu baru ke database.
     * @return ID lagu yang baru ditambahin
     */
    suspend fun insertSong(song: Song, context: Context): Long {
        return songDao.insertSong(song.toEntity(context))
    }

    /**
     * Update data lagu yang udah ada.
     * @return Jumlah row yang berhasil diupdate
     */
    suspend fun updateSong(song: Song, context: Context): Int {
        return songDao.updateSong(song.toEntity(context))
    }

    /**
     * Ambil lagu-lagu terbaru yang ditambahin user.
     * Buat nampilin di section "New Songs".
     */
    suspend fun getNewSongs( userId: String?, limit: Int = 10): List<Song> {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getLatestSongsByUserId(userId,limit)
            songEntities.map { it.toDomainModel() }
        }
    }

    /**
     * Ambil lagu-lagu yang baru-baru ini diputar.
     * Buat nampilin di section "Recently Played".
     */
    suspend fun getRecentlyPlayed(userId: String?, limit: Int = 10): List<Song> {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getRecentlyPlayedByUserId(userId, limit)
            songEntities.map { it.toDomainModel() }
        }
    }

    /**
     * Update waktu terakhir lagu diputar (jadi now).
     * Dipanggil setiap kali mulai muter lagu.
     */
    suspend fun updateLastPlayed(songId: Long) {
        withContext(Dispatchers.IO) {
            songDao.updateLastPlayed(songId, System.currentTimeMillis())
        }
    }

    /**
     * Update status like/unlike lagu.
     */
    suspend fun updateLikeStatus(songId: Long, isLiked: Boolean) {
        withContext(Dispatchers.IO) {
            songDao.updateLikedStatus(songId, isLiked)
        }
    }

    /**
     * Cek apakah lagu sudah di-like atau belum.
     */
    suspend fun getLikedStatusBySongId(songId: Long): Boolean {
        return try {
            // Add null safety to prevent NPE
            songDao.getLikedStatusBySongId(songId) ?: false
        } catch (e: Exception) {
            Log.e("SongRepository", "Error checking like status for song $songId: ${e.message}")
            false // Default to "not liked" if there's any error
        }
    }

    /**
     * Ambil jumlah lagu yang di-like sebagai Flow.
     * Flow supaya UI otomatis update kalo ada perubahan.
     */
    suspend fun getLikedSongsCount(userId: String?): Flow<Int> {
        return songDao.getLikedSongsCountByUserId(userId)
    }

    /**
     * Hitung jumlah total lagu yang dimiliki user.
     */
    suspend fun getOwnedSongsCount(userId: String?): Int {
        return songDao.getOwnedSongsCountByUserId(userId)
    }

    /**
     * Hitung jumlah lagu yang pernah diputar/didengar.
     */
    suspend fun getHeardSongsCount(userId: String?): Int {
        return songDao.getHeardSongsCountByUserId(userId)
    }

    /**
     * Ambil semua lagu user, diurutin berdasarkan judul.
     * Buat playlist atau daftar lengkap koleksi.
     */
    suspend fun getAllSongsOrdered(): List<Song> {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getAllSongsByUserIdOrdered(tokenManager.getEmail())
            songEntities.map { it.toDomainModel() }
        }
    }

    /**
     * Ambil lagu berikutnya dari urutan playlist.
     * Untuk tombol "Next" di player.
     * Bakal loop ke awal kalo udah sampe lagu terakhir.
     */
    suspend fun getNextSong(currentSongId: Long): Song? {
        return withContext(Dispatchers.IO) {
            val allSongs = getAllSongsOrdered()
            if (allSongs.isEmpty()) return@withContext null

            val currentIndex = allSongs.indexOfFirst { it.id == currentSongId }
            if (currentIndex == -1) return@withContext allSongs.firstOrNull()

            return@withContext if (currentIndex < allSongs.size - 1) {
                allSongs[currentIndex + 1]
            } else {
                // Loop balik ke awal
                allSongs.firstOrNull()
            }
        }
    }

    /**
     * Ambil lagu sebelumnya dari urutan playlist.
     * Untuk tombol "Previous" di player.
     * Bakal loop ke akhir kalo dipake di lagu pertama.
     */
    suspend fun getPreviousSong(currentSongId: Long): Song? {
        return withContext(Dispatchers.IO) {
            val allSongs = getAllSongsOrdered()
            if (allSongs.isEmpty()) return@withContext null

            val currentIndex = allSongs.indexOfFirst { it.id == currentSongId }
            if (currentIndex == -1) return@withContext allSongs.lastOrNull()

            return@withContext if (currentIndex > 0) {
                allSongs[currentIndex - 1]
            } else {
                // Loop balik ke akhir
                allSongs.lastOrNull()
            }
        }
    }
    /**
     * Save a song from online source after downloading
     */
    suspend fun saveSongFromOnline(song: Song): Long {
        return withContext(Dispatchers.IO) {
            // Check if the song already exists in the database
            val existingSong = songDao.getSongById(song.id)
            if (existingSong != null) {
                // Update the existing song with the new local file path
                songDao.updateSongFilePath(song.id, song.filePath, tokenManager.getEmail() ?: "")
                song.id
            } else {
                // Insert as a new song
                val entity = song.toEntity(context).copy(userId = tokenManager.getEmail())
                songDao.insertSong(entity)
            }
        }
    }

    /**
     * Check if a song is downloaded (has local file)
     */
    suspend fun isDownloaded(songId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val song = songDao.getSongById(songId)
            song?.filePath?.startsWith("/") == true || song?.filePath?.startsWith("file://") == true
        }
    }

    /**
     * Get song by ID
     */
    suspend fun getSongById(songId: Long): Song? {
        return withContext(Dispatchers.IO) {
            val entity = songDao.getSongById(songId)
            entity?.toDomainModel()
        }
    }

    /**
     * Ambil 5 rekomendasi lagu terbaik berdasarkan kombinasi semua faktor:
     * - Trending songs (25%): Lagu yang banyak diputar dalam 7 hari terakhir
     * - User preference (20%): Berdasarkan pola judul lagu yang sering diputar user
     * - Likes (20%): Lagu yang banyak di-like user lain
     * - Popularity (25%): Berdasarkan total playCount
     * - Diversity (10%): Random factor untuk variasi
     *
     * @param daysBack berapa hari ke belakang untuk menentukan "trending" (default 7 hari)
     * @return List maksimal 5 lagu rekomendasi dengan skor tertinggi
     */
    suspend fun getSmartRecommendations(daysBack: Int = 7): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = tokenManager.getEmail()
                val sinceTimestamp = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)

                val songEntities = songDao.getSmartRecommendations(userId, sinceTimestamp)
                songEntities.map { it.toDomainModel() }

            } catch (e: Exception) {
                Log.e("SongRepository", "Error getting smart recommendations: ${e.message}")
                emptyList()
            }
        }
    }
}