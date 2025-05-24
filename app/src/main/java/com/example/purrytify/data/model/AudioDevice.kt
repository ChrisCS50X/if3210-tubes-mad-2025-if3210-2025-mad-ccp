package com.example.purrytify.data.model

data class AudioDevice(
    val id: String,
    val name: String,
    val address: String,
    val isConnected: Boolean = false,
    val isActive: Boolean = false,
    val type: AudioDeviceType = AudioDeviceType.UNKNOWN
)

enum class AudioDeviceType {
    WIRED_HEADSET,
    BLUETOOTH,
    INTERNAL_SPEAKER,
    USB_AUDIO,
    UNKNOWN
}
