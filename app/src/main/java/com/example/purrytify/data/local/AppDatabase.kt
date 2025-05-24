package com.example.purrytify.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.purrytify.data.local.dao.ListeningStatsDao
import com.example.purrytify.data.local.dao.SongDao
import com.example.purrytify.data.local.entity.ListeningStatsEntity
import com.example.purrytify.data.local.entity.SongEntity

/**
 * Class database utama untuk aplikasi ini.
 * Menggunakan Room sebagai abstraksi SQLite.
 */
@Database(
    entities = [SongEntity::class, ListeningStatsEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // DAO untuk akses tabel songs
    abstract fun songDao(): SongDao
    
    // DAO untuk akses tabel listening_stats
    abstract fun listeningStatsDao(): ListeningStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Fungsi singleton buat dapetin instance database.
         * Pastiin kita cuma punya satu koneksi database di seluruh aplikasi.
         * Pake destructive migration (hapus & buat ulang) kalo ada perubahan skema.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purrytify_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}