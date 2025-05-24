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

    @Query("UPDATE songs SET filePath = :filePath WHERE id = :id AND userId = :userId")
    suspend fun updateSongFilePath(id: Long, filePath: String, userId: String)

    /**
     * Ambil 5 rekomendasi lagu terbaik berdasarkan kombinasi semua faktor:
     * - Trending (banyak diputar belakangan)
     * - User preference (berdasarkan histori user)
     * - Artist similarity (artis yang sama dengan yang sering diputar user)
     * - Likes dari user lain
     * - PlayCount tinggi
     *
     * @param userId ID user yang mau dikasih rekomendasi
     * @param sinceTimestamp waktu untuk menentukan "trending" berdasarkan 7 hari terakhir
     * @return List maksimal 5 lagu rekomendasi dengan skor tertinggi
     */
    @Query("""
    WITH user_stats AS (
        -- Analisa preferensi user berdasarkan histori
        SELECT 
            SUBSTR(title, 1, 3) as title_pattern,
            COUNT(*) as pattern_count,
            AVG(playCount) as avg_play_preference
        FROM songs 
        WHERE userId = :userId AND playCount > 0
        GROUP BY SUBSTR(title, 1, 3)
    ),
    candidate_songs AS (
        SELECT s.*,
            -- Skor trending: lagu yang banyak diputar belakangan (25%)
            CASE 
                WHEN s.lastPlayedAt > :sinceTimestamp THEN s.playCount * 0.25
                ELSE 0 
            END as trending_score,
            
            -- Skor user preference: berdasarkan pola judul yang sering diputar user (20%)
            COALESCE(
                (SELECT us.pattern_count * 0.20 
                 FROM user_stats us 
                 WHERE us.title_pattern = SUBSTR(s.title, 1, 3)), 
                0
            ) as preference_score,
            
            -- Skor likes: lagu yang banyak di-like (20%)
            CASE WHEN s.isLiked = 1 THEN 20 ELSE 0 END as likes_score,
            
            -- Skor playCount: popularitas umum lagu (25%)
            (s.playCount * 0.25) as popularity_score,
            
            -- Bonus skor untuk diversity: random factor untuk variasi (10%)
            (ABS(RANDOM()) % 10) as diversity_score
            
        FROM songs s
        WHERE s.userId != :userId 
        AND s.id NOT IN (
            -- Exclude lagu yang udah pernah diputar atau di-like user
            SELECT id FROM songs 
            WHERE userId = :userId 
            AND (isLiked = 1 OR playCount > 0)
        )
    )
    SELECT *,
        (trending_score + preference_score + likes_score + popularity_score + diversity_score) as total_score
    FROM candidate_songs
    WHERE total_score > 0  -- Hanya ambil yang ada skornya
    ORDER BY total_score DESC, playCount DESC, RANDOM()
    LIMIT 5
""")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getSmartRecommendations(
        userId: String?,
        sinceTimestamp: Long = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 hari terakhir
    ): List<SongEntity>
}