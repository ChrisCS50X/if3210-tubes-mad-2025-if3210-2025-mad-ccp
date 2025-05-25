package com.example.purrytify.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purrytify.data.model.AudioDevice
import com.example.purrytify.data.model.AudioDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages audio output devices for the music player.
 * Detects and maintains a list of available audio devices like bluetooth speakers,
 * headphones, and internal speaker.
 */
class AudioDeviceManager(private val context: Context) {
    private val TAG = "AudioDeviceManager"
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    private val _availableDevices = MutableLiveData<List<AudioDevice>>(emptyList())
    val availableDevices: LiveData<List<AudioDevice>> = _availableDevices
    
    private val _activeDevice = MutableLiveData<AudioDevice?>(null)
    val activeDevice: LiveData<AudioDevice?> = _activeDevice
    
    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> = _errorEvent

    // A unique ID for the internal speaker
    private val INTERNAL_SPEAKER_ID = "internal_speaker_${UUID.randomUUID()}"
    
    private var deviceCallback: AudioDeviceCallback? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var headsetReceiver: BroadcastReceiver? = null
    
    /**
     * Initialize the device manager and start monitoring audio devices.
     */
    fun initialize() {
        try {
            // Initial device scan
            Log.d(TAG, "Initializing AudioDeviceManager")
            
            // Add internal speaker as default
            val internalSpeaker = AudioDevice(
                id = INTERNAL_SPEAKER_ID,
                name = "Internal Speaker",
                address = "",
                isConnected = true,
                isActive = true,
                type = AudioDeviceType.INTERNAL_SPEAKER
            )
            
            val initialDevices = mutableListOf(internalSpeaker)
            _availableDevices.value = initialDevices
            _activeDevice.value = internalSpeaker
            
            // Set up device monitoring
            setupAudioDeviceMonitoring()
            setupBluetoothMonitoring()
            setupHeadsetMonitoring()
            
            // Perform initial scan for devices
            scanForConnectedDevices()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioDeviceManager", e)
            _errorEvent.value = "Failed to initialize audio device detection"
        }
    }
    
    /**
     * Scan for currently connected audio devices
     */
    private fun scanForConnectedDevices() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val devices = mutableListOf<AudioDevice>()
                
                // Always add internal speaker
                val internalSpeaker = AudioDevice(
                    id = INTERNAL_SPEAKER_ID,
                    name = "Internal Speaker",
                    address = "",
                    isConnected = true,
                    isActive = isInternalSpeakerActive(),
                    type = AudioDeviceType.INTERNAL_SPEAKER
                )
                devices.add(internalSpeaker)
                
                // Get modern API devices if available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (device in audioDevices) {
                        if (isRelevantAudioDevice(device)) {
                            val audioDevice = createAudioDeviceFromInfo(device)
                            if (!devices.any { it.id == audioDevice.id }) {
                                devices.add(audioDevice)
                            }
                        }
                    }
                }
                
                // Get bluetooth devices
                scanForBluetoothDevices(devices)
                
                withContext(Dispatchers.Main) {
                    val currentActive = _activeDevice.value
                    
                    // Update connection status for devices
                    val updatedDevices = devices.map { device ->
                        if (device.id == currentActive?.id) {
                            device.copy(isActive = true)
                        } else {
                            device.copy(isActive = false)
                        }
                    }
                    
                    _availableDevices.value = updatedDevices
                    
                    // If we don't have an active device, set the internal speaker
                    if (currentActive == null || !updatedDevices.any { it.id == currentActive.id }) {
                        _activeDevice.value = updatedDevices.firstOrNull { it.type == AudioDeviceType.INTERNAL_SPEAKER }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning for devices", e)
            }
        }
    }
    
    /**
     * Set up monitoring for audio device changes using modern API
     */
    private fun setupAudioDeviceMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            deviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    Log.d(TAG, "Audio devices added: ${addedDevices.size}")
                    val relevantDevices = addedDevices.filter { isRelevantAudioDevice(it) }
                    if (relevantDevices.isNotEmpty()) {
                        handleAudioDevicesAdded(relevantDevices)
                    }
                }
                
                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    Log.d(TAG, "Audio devices removed: ${removedDevices.size}")
                    val relevantDevices = removedDevices.filter { isRelevantAudioDevice(it) }
                    if (relevantDevices.isNotEmpty()) {
                        handleAudioDevicesRemoved(relevantDevices)
                    }
                }
            }
            
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        }
    }
    
    /**
     * Set up monitoring for Bluetooth device connection changes
     */
    private fun setupBluetoothMonitoring() {
        try {
            // Try to get Bluetooth adapter
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            
            // Create the receiver for Bluetooth connection events
            bluetoothReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            val device = getBluetoothDeviceFromIntent(intent)
                            if (device != null) {
                                Log.d(TAG, "Bluetooth device connected: ${device.name}")
                                handleBluetoothDeviceConnected(device)
                            }
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            val device = getBluetoothDeviceFromIntent(intent)
                            if (device != null) {
                                Log.d(TAG, "Bluetooth device disconnected: ${device.name}")
                                handleBluetoothDeviceDisconnected(device)
                            }
                        }
                        BluetoothAdapter.ACTION_STATE_CHANGED -> {
                            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                            if (state == BluetoothAdapter.STATE_ON) {
                                Log.d(TAG, "Bluetooth turned on, scanning for devices")
                                scanForConnectedDevices()
                            } else if (state == BluetoothAdapter.STATE_OFF) {
                                Log.d(TAG, "Bluetooth turned off")
                                handleBluetoothTurnedOff()
                            }
                        }
                    }
                }
            }
            
            // Register the Bluetooth receiver
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            
            context.registerReceiver(bluetoothReceiver, filter)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Bluetooth monitoring", e)
        }
    }
    
    /**
     * Set up monitoring for wired headset connection changes
     */
    private fun setupHeadsetMonitoring() {
        try {
            headsetReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                        val state = intent.getIntExtra("state", -1)
                        val name = intent.getStringExtra("name") ?: "Wired Headset"
                        
                        if (state == 1) {
                            // Headset plugged in
                            Log.d(TAG, "Wired headset plugged in: $name")
                            handleWiredHeadsetConnected(name)
                        } else if (state == 0) {
                            // Headset unplugged
                            Log.d(TAG, "Wired headset unplugged")
                            handleWiredHeadsetDisconnected()
                        }
                    }
                }
            }
            
            // Register the headset receiver
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            context.registerReceiver(headsetReceiver, filter)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up headset monitoring", e)
        }
    }
    
    /**
     * Handle when new audio devices are detected
     */
    private fun handleAudioDevicesAdded(devices: List<AudioDeviceInfo>) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            var devicesChanged = false
            
            for (deviceInfo in devices) {
                val audioDevice = createAudioDeviceFromInfo(deviceInfo)
                if (!currentDevices.any { it.id == audioDevice.id }) {
                    currentDevices.add(audioDevice)
                    devicesChanged = true
                    Log.d(TAG, "Added audio device: ${audioDevice.name}, type: ${audioDevice.type}")
                }
            }
            
            if (devicesChanged) {
                _availableDevices.value = currentDevices
            }
        }
    }
    
    /**
     * Handle when audio devices are removed
     */
    private fun handleAudioDevicesRemoved(devices: List<AudioDeviceInfo>) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            var devicesChanged = false
            var activeDeviceRemoved = false
            
            for (deviceInfo in devices) {
                // Create a device ID to match with our stored devices
                val deviceId = "audio_${deviceInfo.id}"
                
                // Find and remove the device
                val deviceToRemove = currentDevices.find { it.id == deviceId }
                if (deviceToRemove != null) {
                    currentDevices.remove(deviceToRemove)
                    devicesChanged = true
                    Log.d(TAG, "Removed audio device: ${deviceToRemove.name}")
                    
                    // Check if this was the active device
                    if (_activeDevice.value?.id == deviceToRemove.id) {
                        activeDeviceRemoved = true
                    }
                }
            }
            
            if (devicesChanged) {
                _availableDevices.value = currentDevices
            }
            
            // If the active device was removed, fall back to internal speaker
            if (activeDeviceRemoved) {
                handleActivateDeviceDisconnected()
            }
        }
    }
    
    /**
     * Handle when a Bluetooth device connects
     */
    private fun handleBluetoothDeviceConnected(device: BluetoothDevice) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            
            // Check if the device is already in our list
            val existingDevice = currentDevices.find { it.address == device.address }
            if (existingDevice != null) {
                // Update connection status
                val updatedDevice = existingDevice.copy(isConnected = true)
                val index = currentDevices.indexOf(existingDevice)
                currentDevices[index] = updatedDevice
            } else {
                // Add new device
                val newDevice = AudioDevice(
                    id = "bt_${device.address.replace(":", "_")}",
                    name = device.name ?: "Bluetooth Device",
                    address = device.address,
                    isConnected = true,
                    type = AudioDeviceType.BLUETOOTH
                )
                currentDevices.add(newDevice)
            }
            
            _availableDevices.value = currentDevices
        }
    }
    
    /**
     * Handle when a Bluetooth device disconnects
     */
    private fun handleBluetoothDeviceDisconnected(device: BluetoothDevice) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            var activeDeviceRemoved = false
            
            // Find the device in our list
            val deviceId = "bt_${device.address.replace(":", "_")}"
            val existingDevice = currentDevices.find { it.id == deviceId }
            
            if (existingDevice != null) {
                // Remove the device
                currentDevices.remove(existingDevice)
                
                // Check if this was the active device
                if (_activeDevice.value?.id == existingDevice.id) {
                    activeDeviceRemoved = true
                }
                
                _availableDevices.value = currentDevices
            }
            
            // If the active device was removed, fall back to internal speaker
            if (activeDeviceRemoved) {
                handleActivateDeviceDisconnected()
            }
        }
    }
    
    /**
     * Handle when Bluetooth is turned off
     */
    private fun handleBluetoothTurnedOff() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            var activeDeviceRemoved = false
            
            // Remove all Bluetooth devices
            val bluetoothDevices = currentDevices.filter { it.type == AudioDeviceType.BLUETOOTH }
            for (device in bluetoothDevices) {
                currentDevices.remove(device)
                
                // Check if this was the active device
                if (_activeDevice.value?.id == device.id) {
                    activeDeviceRemoved = true
                }
            }
            
            if (bluetoothDevices.isNotEmpty()) {
                _availableDevices.value = currentDevices
            }
            
            // If the active device was removed, fall back to internal speaker
            if (activeDeviceRemoved) {
                handleActivateDeviceDisconnected()
            }
        }
    }
    
    /**
     * Handle when a wired headset is connected
     */
    private fun handleWiredHeadsetConnected(name: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            
            // Create a unique ID for the headset
            val headsetId = "wired_headset"
            
            // Check if a wired headset is already in our list
            val existingDevice = currentDevices.find { it.id == headsetId }
            if (existingDevice != null) {
                // Update connection status
                val updatedDevice = existingDevice.copy(isConnected = true)
                val index = currentDevices.indexOf(existingDevice)
                currentDevices[index] = updatedDevice
            } else {
                // Add new device
                val newDevice = AudioDevice(
                    id = headsetId,
                    name = name,
                    address = "",
                    isConnected = true,
                    type = AudioDeviceType.WIRED_HEADSET
                )
                currentDevices.add(newDevice)
            }
            
            _availableDevices.value = currentDevices
        }
    }
    
    /**
     * Handle when a wired headset is disconnected
     */
    private fun handleWiredHeadsetDisconnected() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value?.toMutableList() ?: mutableListOf()
            var activeDeviceRemoved = false
            
            // Find the headset in our list
            val headsetId = "wired_headset"
            val existingDevice = currentDevices.find { it.id == headsetId }
            
            if (existingDevice != null) {
                // Remove the device
                currentDevices.remove(existingDevice)
                
                // Check if this was the active device
                if (_activeDevice.value?.id == existingDevice.id) {
                    activeDeviceRemoved = true
                }
                
                _availableDevices.value = currentDevices
            }
            
            // If the active device was removed, fall back to internal speaker
            if (activeDeviceRemoved) {
                handleActivateDeviceDisconnected()
            }
        }
    }
    
    /**
     * Handle when the active device is disconnected.
     * Falls back to internal speaker and notifies user.
     */
    private fun handleActivateDeviceDisconnected() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentDevices = _availableDevices.value ?: emptyList()
            
            // Find internal speaker
            val internalSpeaker = currentDevices.find { it.type == AudioDeviceType.INTERNAL_SPEAKER }
            if (internalSpeaker != null) {
                // Switch to internal speaker
                _activeDevice.value = internalSpeaker
                
                // Update devices list to mark internal speaker as active
                val updatedDevices = currentDevices.map { device ->
                    if (device.id == internalSpeaker.id) {
                        device.copy(isActive = true)
                    } else {
                        device.copy(isActive = false)
                    }
                }
                
                _availableDevices.value = updatedDevices
                
                // Notify about disconnection
                _errorEvent.value = "Audio device disconnected. Switched to internal speaker."
            } else {
                // No internal speaker found, create one
                val newInternalSpeaker = AudioDevice(
                    id = INTERNAL_SPEAKER_ID,
                    name = "Internal Speaker",
                    address = "",
                    isConnected = true,
                    isActive = true,
                    type = AudioDeviceType.INTERNAL_SPEAKER
                )
                
                _activeDevice.value = newInternalSpeaker
                
                // Add to device list
                val updatedDevices = currentDevices.map { it.copy(isActive = false) }.toMutableList()
                updatedDevices.add(newInternalSpeaker)
                
                _availableDevices.value = updatedDevices
                
                // Notify about disconnection
                _errorEvent.value = "Audio device disconnected. Switched to internal speaker."
            }
        }
    }
    
    /**
     * Scan for connected Bluetooth audio devices and add them to the list
     */
    private fun scanForBluetoothDevices(devices: MutableList<AudioDevice>) {
        try {
            if (bluetoothAdapter == null) return
            
            // Check if Bluetooth is enabled
            if (bluetoothAdapter?.isEnabled != true) {
                Log.d(TAG, "Bluetooth is disabled")
                return
            }
            
            // Get connected A2DP devices (audio)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                
                for (device in audioDevices) {
                    if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        val audioDevice = createAudioDeviceFromInfo(device)
                        if (!devices.any { it.id == audioDevice.id }) {
                            devices.add(audioDevice)
                        }
                    }
                }
            } else {
                // On older devices, we need to use the BluetoothAdapter directly
                bluetoothAdapter?.bondedDevices?.forEach { device ->
                    // Only add audio devices (headsets, speakers)
                    if (device.bluetoothClass.majorDeviceClass == BluetoothProfile.A2DP) {
                        val audioDevice = AudioDevice(
                            id = "bt_${device.address.replace(":", "_")}",
                            name = device.name ?: "Bluetooth Device",
                            address = device.address,
                            isConnected = true,
                            type = AudioDeviceType.BLUETOOTH
                        )
                        
                        if (!devices.any { it.id == audioDevice.id }) {
                            devices.add(audioDevice)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for Bluetooth devices", e)
        }
    }
    
    /**
     * Check if the internal speaker is the active output device
     */
    private fun isInternalSpeakerActive(): Boolean {
        return _activeDevice.value?.type == AudioDeviceType.INTERNAL_SPEAKER || _activeDevice.value == null
    }
    
    /**
     * Get a BluetoothDevice from an Intent
     */
    private fun getBluetoothDeviceFromIntent(intent: Intent): BluetoothDevice? {
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
    
    /**
     * Create an AudioDevice object from an AudioDeviceInfo
     */
    private fun createAudioDeviceFromInfo(deviceInfo: AudioDeviceInfo): AudioDevice {
        val deviceType = when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceType.BLUETOOTH
            
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED_HEADSET
            
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceType.USB_AUDIO
            
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.INTERNAL_SPEAKER
            
            else -> AudioDeviceType.UNKNOWN
        }
        
        return AudioDevice(
            id = "audio_${deviceInfo.id}",
            name = deviceInfo.productName.toString(),
            address = "",
            isConnected = true,
            type = deviceType
        )
    }
    
    /**
     * Check if an AudioDeviceInfo is relevant for our audio routing
     */
    private fun isRelevantAudioDevice(deviceInfo: AudioDeviceInfo): Boolean {
        // Only include output devices
        if (!deviceInfo.isSink) {
            return false
        }
        
        // Check device type
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> true
            else -> false
        }
    }
    
    /**
     * Set the active audio device
     */
    fun setActiveDevice(deviceId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val devices = _availableDevices.value ?: emptyList()
            val selectedDevice = devices.find { it.id == deviceId }
            
            if (selectedDevice != null && selectedDevice.isConnected) {
                Log.d(TAG, "Setting active device to: ${selectedDevice.name}")
                
                // Set as active
                _activeDevice.value = selectedDevice
                
                // Update devices list with new active status
                val updatedDevices = devices.map { device ->
                    if (device.id == deviceId) {
                        device.copy(isActive = true)
                    } else {
                        device.copy(isActive = false)
                    }
                }
                
                _availableDevices.value = updatedDevices
                
                // Perform actual audio routing based on device type
                routeAudioToDevice(selectedDevice)
            } else {
                Log.e(TAG, "Could not find device with ID: $deviceId or device is disconnected")
                _errorEvent.value = "Could not connect to the selected audio device"
            }
        }
    }
    
    /**
     * Route audio to the selected device
     */
    private fun routeAudioToDevice(device: AudioDevice) {
        try {
            when (device.type) {
                AudioDeviceType.BLUETOOTH -> routeAudioToBluetooth(device)
                AudioDeviceType.WIRED_HEADSET -> routeAudioToWiredHeadset()
                AudioDeviceType.USB_AUDIO -> routeAudioToUsbDevice(device)
                AudioDeviceType.INTERNAL_SPEAKER -> routeAudioToSpeaker()
                else -> {
                    Log.w(TAG, "Unknown device type: ${device.type}, trying generic routing")
                    routeAudioGeneric(device)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to device: ${device.name}", e)
            _errorEvent.value = "Failed to route audio to ${device.name}"
        }
    }
    
    /**
     * Route audio to Bluetooth device
     */
    private fun routeAudioToBluetooth(device: AudioDevice) {
        Log.d(TAG, "Routing audio to Bluetooth device: ${device.name}")
        
        try {
            // Force audio to Bluetooth
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = true
            
            // Find the actual device using modern API if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (audioDevice in audioDevices) {
                    if ("audio_${audioDevice.id}" == device.id || 
                        (device.address.isNotEmpty() && audioDevice.address?.equals(device.address) == true)) {
                        Log.d(TAG, "Found matching Bluetooth device in system")
                        // On Android 11+ we could use audioManager.setCommunicationDevice(audioDevice)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to Bluetooth", e)
            throw e
        }
    }
    
    /**
     * Route audio to wired headset
     */
    private fun routeAudioToWiredHeadset() {
        Log.d(TAG, "Routing audio to wired headset")
        
        try {
            // Standard routing for wired headset
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to wired headset", e)
            throw e
        }
    }
    
    /**
     * Route audio to USB device
     */
    private fun routeAudioToUsbDevice(device: AudioDevice) {
        Log.d(TAG, "Routing audio to USB device: ${device.name}")
        
        try {
            // Standard routing
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = false
            
            // On newer Android versions, we could use specific APIs for USB devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (audioDevice in audioDevices) {
                    if ("audio_${audioDevice.id}" == device.id) {
                        Log.d(TAG, "Found matching USB device in system")
                        // On Android 11+ we could use audioManager.setCommunicationDevice(audioDevice)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to USB device", e)
            throw e
        }
    }
    
    /**
     * Route audio to internal speaker
     */
    private fun routeAudioToSpeaker() {
        Log.d(TAG, "Routing audio to internal speaker")
        
        try {
            // Route to speaker
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            audioManager.isBluetoothScoOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to speaker", e)
            throw e
        }
    }
    
    /**
     * Generic audio routing for unknown device types
     */
    private fun routeAudioGeneric(device: AudioDevice) {
        Log.d(TAG, "Attempting generic audio routing for device: ${device.name}")
        
        try {
            // Try standard routing first
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = false
            
            // Try to find the device in system devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (audioDevice in audioDevices) {
                    if ("audio_${audioDevice.id}" == device.id) {
                        Log.d(TAG, "Found matching device in system")
                        // On Android 11+ we could use audioManager.setCommunicationDevice(audioDevice)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error with generic audio routing", e)
            throw e
        }
    }
    
    /**
     * Clear any error message
     */
    fun clearErrorEvent() {
        _errorEvent.value = null
    }
    
    /**
     * Clean up resources when no longer needed
     */
    fun cleanup() {
        try {
            // Unregister callbacks and receivers
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                deviceCallback?.let {
                    audioManager.unregisterAudioDeviceCallback(it)
                }
            }
            
            bluetoothReceiver?.let {
                context.unregisterReceiver(it)
            }
            
            headsetReceiver?.let {
                context.unregisterReceiver(it)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
