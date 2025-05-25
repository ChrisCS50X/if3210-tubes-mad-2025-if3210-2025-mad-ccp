package com.example.purrytify.data.local.dao

import androidx.room.*
import androidx.room.RewriteQueriesToDropUnusedColumns
import kotlinx.coroutines.flow.Flow
import com.example.purrytify.data.local.entity.SongEntity

/**
 * Data Access Object buat lagu-lagu di database lokal.
 * Interface ini ngehandle semua operasi database yang berhubungan dengan lagu,
 * dari nampilin lagu, update status liked, sampe tracking lagu yang sering diputar.
 */

@Dao
interface SongDao {
    /**
     * Ambil semua lagu dari user tertentu, diurutin berdasarkan judul.
     */
    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY title ASC")
    fun getAllSongsByUserId(userId: String?): Flow<List<SongEntity>>

    /**
     * Ambil semua lagu yang udah di-like user.
     * Buat nampilin di playlist "Liked Songs".
     */
    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY title ASC")
    fun getLikedSongs(): Flow<List<SongEntity>>

    /**
     * Nambahin lagu baru ke database.
     * @return ID lagu yang baru ditambahin
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    /**
     * Update data lagu yang udah ada.
     * @return Jumlah row yang berhasil di-update (harusnya 1)
     */
    @Update
    suspend fun updateSong(songEntity: SongEntity): Int

    /**
     * Hapus lagu berdasarkan ID-nya.
     */
    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    /**
     * Update status like/unlike lagu.
     */
    @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
    suspend fun updateLikedStatus(songId: Long, isLiked: Boolean)

    /**
     * Nambahin counter berapa kali lagu udah diputar.
     * Dipanggil setiap kali user selesai dengerin lagu yang dibedain pake idnya.
     */
    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long)

    /**
     * Ambil lagu-lagu terbaru yang dibuat user, disort berdasarkan id lagunya karena semakin besar idnya berarti semakin baru.
     */
    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY id DESC LIMIT :limit")
    suspend fun getLatestSongsByUserId(userId: String?, limit: Int): List<SongEntity>

    /**
     * Ambil lagu-lagu yang baru-baru ini diputar user.
     * Buat nampilin di bagian "Recently Played".
     * Diurutkan berdasarkan waktu terakhir diputar (lastPlayedAt).
     */
    @Query("SELECT * FROM songs WHERE userId = :userId AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayedByUserId(userId: String?, limit: Int): List<SongEntity>

    /**
     * Update waktu terakhir lagu diputar.
     * Dipanggil setiap kali user mulai muter lagu.
     */
    @Query("UPDATE songs SET lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun updateLastPlayed(songId: Long, timestamp: Long)

    /**
     * Cek apakah lagu sudah di-like atau belum.
     * Buat nampilin status like di halaman pemutaran.
     */
    @Query("SELECT isLiked FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getLikedStatusBySongId(songId: Long): Boolean?

    /**
     * Hitung jumlah lagu yang di-like user.
     * Buat nampilin status di profile.
     */
    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId AND isLiked = 1")
    fun getLikedSongsCountByUserId(userId: String?): Flow<Int>

    /**
     * Hitung total lagu yang dimiliki user.
     * Buat nampilin status di profile.
     */
    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId")
    suspend fun getOwnedSongsCountByUserId(userId: String?): Int

    /**
     * Hitung jumlah lagu yang pernah didengar user.
     * Buat nampilin status di profile.
     */
    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId AND playCount > 0")
    suspend fun getHeardSongsCountByUserId(userId: String?): Int

    /**
     * Ambil semua lagu user berdasarkan judul (A-Z).
     * Buat nampilin daftar lengkap koleksi.
     */
    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY title ASC")
    suspend fun getAllSongsByUserIdOrdered(userId: String?): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT COUNT(*) FROM songs WHERE title = :title AND artist = :artist")
    suspend fun getSongCountByTitleAndArtist(title: String, artist: String): Int

    @Query("UPDATE songs SET filePath = :filePath WHERE id = :id AND userId = :userId")
    suspend fun updateSongFilePath(id: Long, filePath: String, userId: String)

    @Query("SELECT * FROM songs WHERE filePath LIKE '/%' OR filePath LIKE 'file://%' ORDER BY title ASC")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    @Query("""
    WITH user_stats AS (
        SELECT 
            artist,
            AVG(playCount) AS avg_play_preference,
            SUM(isLiked) AS total_likes
        FROM songs 
        WHERE userId = :userId
        GROUP BY artist
    ),
    candidate_songs AS (
        SELECT s.*,
            (s.playCount * 0.25) AS popularity_score,
            CASE 
                WHEN s.lastPlayedAt > :sinceTimestamp THEN s.playCount * 0.25
                ELSE 0 
            END AS trending_score,
            COALESCE(
                (SELECT us.avg_play_preference * 0.20 
                 FROM user_stats us 
                 WHERE us.artist = s.artist), 
                0
            ) AS artist_preference_score,
            CASE WHEN s.isLiked = 1 THEN 20 ELSE 0 END AS likes_score
        FROM songs s
        WHERE s.userId = :userId
    )
    SELECT *, 
        (popularity_score + trending_score + artist_preference_score + likes_score) AS total_score
    FROM candidate_songs
    ORDER BY total_score DESC
    LIMIT 5
    """)
    @RewriteQueriesToDropUnusedColumns
    suspend fun getSmartRecommendations(
        userId: String?,
        sinceTimestamp: Long = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
    ): List<SongEntity>

}