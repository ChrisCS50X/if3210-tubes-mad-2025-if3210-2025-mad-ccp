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
    val coverUrl: String?,
    val filePath: String,
    val duration: Long,
    val isLiked: Boolean
) : Parcelable {
    // Constructor khusus buat baca data dari Parcel
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString(),
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readByte() != 0.toByte()
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