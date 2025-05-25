package com.example.purrytify.ui.player

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.purrytify.R
import com.example.purrytify.data.model.AudioDevice
import com.example.purrytify.databinding.DialogAudioDevicesBinding
import com.example.purrytify.databinding.ItemAudioDeviceBinding

/**
 * Dialog that shows available audio output devices and allows user to select one.
 */
class AudioDeviceSelectionDialog : DialogFragment() {

    private var _binding: DialogAudioDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MusicPlayerViewModel
    private lateinit var adapter: AudioDeviceAdapter

    companion object {
        fun newInstance(viewModel: MusicPlayerViewModel): AudioDeviceSelectionDialog {
            val dialog = AudioDeviceSelectionDialog()
            dialog.viewModel = viewModel
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAudioDevicesBinding.inflate(LayoutInflater.from(context))
    
        val dialog = AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()
        
        dialog.window?.setBackgroundDrawableResource(R.color.black)
        
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        binding.refreshDevices.setOnClickListener {
            binding.loadingDevices.visibility = View.VISIBLE
            binding.noDevicesText.visibility = View.GONE
            viewModel.refreshAudioDevices()
        }
    }

    private fun setupRecyclerView() {
        adapter = AudioDeviceAdapter { device ->
            viewModel.setActiveAudioDevice(device.id)
            dismiss()
        }

        binding.devicesList.layoutManager = LinearLayoutManager(requireContext())
        binding.devicesList.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.availableAudioDevices.observe(viewLifecycleOwner, Observer { devices ->
            binding.loadingDevices.visibility = View.GONE
            
            if (devices.isEmpty()) {
                binding.noDevicesText.visibility = View.VISIBLE
                binding.devicesList.visibility = View.GONE
            } else {
                binding.noDevicesText.visibility = View.GONE
                binding.devicesList.visibility = View.VISIBLE
                adapter.submitList(devices)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for the audio devices list
 */
class AudioDeviceAdapter(
    private val onDeviceSelected: (AudioDevice) -> Unit
) : RecyclerView.Adapter<AudioDeviceAdapter.DeviceViewHolder>() {

    private var devices: List<AudioDevice> = emptyList()

    fun submitList(newDevices: List<AudioDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemAudioDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceSelected)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(
        private val binding: ItemAudioDeviceBinding,
        private val onDeviceSelected: (AudioDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: AudioDevice) {
            binding.deviceName.text = device.name
            
            // Show device type icon based on device type
            when (device.type) {
                com.example.purrytify.data.model.AudioDeviceType.BLUETOOTH -> {
                    binding.deviceIcon.setImageResource(R.drawable.ic_bluetooth_audio)
                }
                com.example.purrytify.data.model.AudioDeviceType.WIRED_HEADSET -> {
                    binding.deviceIcon.setImageResource(R.drawable.ic_headset)
                }
                com.example.purrytify.data.model.AudioDeviceType.INTERNAL_SPEAKER -> {
                    binding.deviceIcon.setImageResource(R.drawable.ic_speaker)
                }
                com.example.purrytify.data.model.AudioDeviceType.USB_AUDIO -> {
                    binding.deviceIcon.setImageResource(R.drawable.ic_usb_audio)
                }
                else -> {
                    binding.deviceIcon.setImageResource(R.drawable.ic_device_unknown)
                }
            }
            
            // Show active indicator if device is active
            binding.deviceActiveIndicator.visibility = if (device.isActive) View.VISIBLE else View.GONE
            
            // Set connection status
            binding.deviceStatus.text = if (device.isConnected) {
                binding.deviceStatus.setTextColor(binding.root.context.getColor(R.color.connected_green))
                "Connected"
            } else {
                binding.deviceStatus.setTextColor(binding.root.context.getColor(R.color.disconnected_red))
                "Disconnected"
            }
            
            // Only allow clicking on connected devices
            binding.root.isEnabled = device.isConnected
            binding.root.alpha = if (device.isConnected) 1.0f else 0.5f
            
            binding.root.setOnClickListener {
                if (device.isConnected) {
                    onDeviceSelected(device)
                }
            }
        }
    }
}
