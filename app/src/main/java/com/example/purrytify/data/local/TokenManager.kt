package com.example.purrytify.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Class untuk nyimpen dan ngambil token secara aman.
 * Pake EncryptedSharedPreferences supaya data token terenkripsi di penyimpanan.
 */
class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Shared preferences yang dienkripsi - kunci dan nilai sama-sama dienkripsi
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Simpen email user yang login (Enkripsi)
     */
    fun saveEmail(email: String) {
        sharedPreferences.edit().putString(KEY_EMAIL, email).apply()
    }

    /**
     * Ambil email user yang login (Dekripsi)
     */
    fun getEmail(): String? {
        return sharedPreferences.getString(KEY_EMAIL, null)
    }

    /**
     * Simpen access token (JWT)
     */
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
    }

    /**
     * Ambil token yang tersimpan, atau null kalo belum login
     */
    fun getToken(): String? {
        return sharedPreferences.getString(TOKEN_KEY, null)
    }

    /**
     * Simpen refresh token untuk perpanjang sesi
     */
    fun saveRefreshToken(refreshToken: String) {
        sharedPreferences.edit().putString(REFRESH_TOKEN_KEY, refreshToken).apply()
    }

    /**
     * Ambil refresh token, atau null kalo belum ada
     */
    fun getRefreshToken(): String? {
        return sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
    }

    /**
     * Hapus semua token (logout)
     */
    fun clearTokens() {
        sharedPreferences.edit()
            .remove(TOKEN_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .apply()
    }

    /**
     * Cek apakah user sudah login (ada token tersimpan)
     */
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    companion object {
        private const val TOKEN_KEY = "auth_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val KEY_EMAIL = "key_email"
    }
}