package com.example.purrytify.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Menghandle semua konfigurasi untuk koneksi API ke server
 * Dibuat jadi object singleton supaya cukup satu instance untuk seluruh aplikasi
 */

object NetworkModule {
    // URL dasar API kita untuk ngakses server backend
    private const val BASE_URL = "http://34.101.226.132:3000"

    // Interceptor untuk debugging
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Konfigurasi OkHttpClient dengan timeout yang cukup buat koneksi lambat (setiap 30
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Bikin instance Retrofit dengan semua konfigurasi yang dibutuhin dan langsung create instance dari PurrytifyAPI untuk dipake di app
    val apiService: PurrytifyAPI = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())  // Convert JSON ke object Kotlin
        .build()
        .create(PurrytifyAPI::class.java)
}