package com.example.purrytify.data.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Model domain untuk lagu yang ditampilin ke UI.
 * Implementasi Parcelable supaya bisa dikirim antar fragment/activity lewat bundle.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?, // Changed from artwork to coverUrl to match existing Song model
    val filePath: String, // Changed from url to filePath to match existing Song model
    val duration: Long, // Changed from String to Long to match existing Song model
    val isLiked: Boolean,
    val country: String? = null, // Added to match GET /songs/<song_id> response
    val rank: Int? = null, // Added to match GET /songs/<song_id> response
    val createdAt: String? = null, // Added to match GET /songs/<song_id> response
    val updatedAt: String? = null // Added to match GET /songs/<song_id> response
) : Parcelable {
    // Constructor khusus buat baca data dari Parcel
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString(),
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readByte() != 0.toByte(),
        parcel.readString(),
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readString(),
        parcel.readString()
    )

    // Write data ke Parcel buat dikirim antar komponen
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(coverUrl)
        parcel.writeString(filePath)
        parcel.writeLong(duration)
        parcel.writeByte(if (isLiked) 1 else 0)
        parcel.writeString(country)
        parcel.writeValue(rank)
        parcel.writeString(createdAt)
        parcel.writeString(updatedAt)
    }

    // Gak dipake sih, tapi wajib ada buat Parcelable
    override fun describeContents(): Int {
        return 0
    }

    // Object spesial buat bikin Song dari Parcel (kayak factory)
    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song {
            return Song(parcel)
        }

        override fun newArray(size: Int): Array<Song?> {
            return arrayOfNulls(size)
        }
    }
}