package com.example.purrytify.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentNowPlayingBinding
import java.util.concurrent.TimeUnit
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.purrytify.utils.BackgroundColorProvider
import com.example.purrytify.utils.SharingUtils
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.purrytify.service.DownloadManager
import com.example.purrytify.data.model.Song

class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val args: NowPlayingFragmentArgs by navArgs()
    private lateinit var songRepository: SongRepository
    private lateinit var downloadManager: DownloadManager

    private val musicPlayerViewModel: MusicPlayerViewModel by activityViewModels {
        MusicPlayerViewModelFactory(
            requireActivity().application,
            SongRepository(AppDatabase.getInstance(requireContext()).songDao(), requireContext().applicationContext)
        )
    }

    private val playNextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("NowPlayingFragment", "Received broadcast to play next song")
            if (intent?.action == "com.example.purrytify.PLAY_NEXT") {
                try {
                    musicPlayerViewModel.playNext()
                } catch (e: Exception) {
                    Log.e("NowPlayingFragment", "Error playing next song", e)
                }
            }
        }
    }

    private val checkRepeatModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("NowPlayingFragment", "Received request to check repeat mode")
            if (intent?.action == "com.example.purrytify.CHECK_REPEAT_MODE") {
                try {
                    if (musicPlayerViewModel.repeatMode.value == RepeatMode.ONE) {
                        Log.d("NowPlayingFragment", "RepeatMode.ONE active, replaying current song")
                        musicPlayerViewModel.currentSong.value?.let {
                            musicPlayerViewModel.playSong(it)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NowPlayingFragment", "Error handling repeat mode", e)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        songRepository = SongRepository(AppDatabase.getInstance(requireContext()).songDao(), requireContext().applicationContext)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize download manager
        downloadManager = DownloadManager(requireContext())
        downloadManager.createNotificationChannel()

        setupUI()
        setupControls()
        observeViewModel()
        observeAudioDevices()
    }
    
    override fun onResume() {
        super.onResume()
        try {
            val filterPlayNext = IntentFilter("com.example.purrytify.PLAY_NEXT")
            LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(playNextReceiver, filterPlayNext)
            Log.d("NowPlayingFragment", "Registered broadcast receiver for PLAY_NEXT")

            val filterCheckRepeatMode = IntentFilter("com.example.purrytify.CHECK_REPEAT_MODE")
            LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(checkRepeatModeReceiver, filterCheckRepeatMode)
            Log.d("NowPlayingFragment", "Registered broadcast receiver for CHECK_REPEAT_MODE")
        } catch (e: Exception) {
            Log.e("NowPlayingFragment", "Error registering receiver", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(playNextReceiver)
            Log.d("NowPlayingFragment", "Unregistered broadcast receiver for PLAY_NEXT")

            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(checkRepeatModeReceiver)
            Log.d("NowPlayingFragment", "Unregistered broadcast receiver for CHECK_REPEAT_MODE")
        } catch (e: Exception) {
            Log.e("NowPlayingFragment", "Error unregistering receiver", e)
        }
    }

    private fun setupUI() {
        Log.d("NowPlayingFragment", "Setting up UI")

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnShare.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                if (SharingUtils.canShareSong(song)) {
                    SharingUtils.showShareOptions(parentFragmentManager, song)
                } else {
                    Toast.makeText(requireContext(), "Only server songs can be shared", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        binding.btnAudioOutput.setOnClickListener {
            showAudioDeviceSelectionDialog()
        }

        binding.btnFavorite.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                lifecycleScope.launch {
                    try {
                        if (songRepository.getLikedStatusBySongId(song.id)) {
                            songRepository.updateLikeStatus(song.id, false)
                            binding.btnFavorite.setImageResource(R.drawable.ic_heart_outline)
                        } else {
                            songRepository.updateLikeStatus(song.id, true)
                            binding.btnFavorite.setImageResource(R.drawable.ic_heart_filled)
                        }
                    } catch (e: Exception) {
                        Log.e("NowPlayingFragment", "Error toggling liked status: ${e.message}")
                        Toast.makeText(requireContext(), "Error updating like status", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        args.song?.let { song ->
            updateSongInfo(song)
            lifecycleScope.launch {
                try {
                    val isLiked = songRepository.getLikedStatusBySongId(song.id)
                    binding.btnFavorite.setImageResource(
                        if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                    )
                } catch (e: Exception) {
                    Log.e("NowPlayingFragment", "Error checking initial liked status: ${e.message}")
                    binding.btnFavorite.setImageResource(R.drawable.ic_heart_outline)
                }
            }
        }
    }

    private fun updateSongInfo(song: Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist

        // Add try-catch around liked status check
        lifecycleScope.launch {
            try {
                val isLiked = songRepository.getLikedStatusBySongId(song.id)
                binding.btnFavorite.setImageResource(
                    if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
            } catch (e: Exception) {
                Log.e("NowPlayingFragment", "Error checking liked status: ${e.message}")
                binding.btnFavorite.setImageResource(R.drawable.ic_heart_outline)
            }
        }

        Glide.with(this)
            .load(song.coverUrl)
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivAlbumCover)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(song.duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(song.duration) -
                TimeUnit.MINUTES.toSeconds(minutes)
        binding.tvTotalDuration.text = String.format("%02d:%02d", minutes, seconds)

        if (isAdded && !isDetached) {
            try {
                val gradient = BackgroundColorProvider.createGradientBackground(requireContext(), song)
                binding.layoutNowPlaying.background = gradient
            } catch (e: Exception) {
                Log.e("NowPlayingFragment", "Error setting gradient background: ${e.message}")
                binding.layoutNowPlaying.setBackgroundColor(android.graphics.Color.BLACK)
            }
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            musicPlayerViewModel.togglePlayPause()
        }

        binding.btnPrevious.setOnClickListener {
            val currentPosition = musicPlayerViewModel.getCurrentPosition()
            if (currentPosition > 3000) {
                musicPlayerViewModel.seekTo(0)
            } else {
                musicPlayerViewModel.playPrevious()
            }
        }

        binding.btnNext.setOnClickListener {
            musicPlayerViewModel.playNext()
        }

        binding.btnShuffle.setOnClickListener {
            musicPlayerViewModel.toggleShuffle()
        }

        binding.btnQueue.setOnClickListener {
            showQueueBottomSheet()
        }

        binding.btnRepeat.setOnClickListener {
            musicPlayerViewModel.toggleRepeatMode()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicPlayerViewModel.seekTo(progress)
                    updateCurrentTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showQueueBottomSheet() {
        val bottomSheet = QueueBottomSheetFragment()
        bottomSheet.show(childFragmentManager, "QueueBottomSheet")
    }

    private fun observeViewModel() {
        // Observe audio devices
        observeAudioDevices()
        
        musicPlayerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                updateSongInfo(it)

                // Show loading indicator for online songs
                if (song.filePath.startsWith("http")) {
                    binding.loadingIndicator.visibility = View.VISIBLE
                    binding.btnPlayPause.visibility = View.INVISIBLE
                }

                updateDownloadButtonVisibility(it)
            }
        }

        musicPlayerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlayingValue ->
            val isPlaying = isPlayingValue ?: false
            val icon = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
            binding.btnPlayPause.setImageResource(icon)
            binding.btnPlayPause.visibility = View.VISIBLE

            // Only hide the loading indicator if we have a definite state
            if (isPlayingValue != null) {
                binding.loadingIndicator.visibility = View.GONE
            }
        }

        musicPlayerViewModel.isShuffleEnabled.observe(viewLifecycleOwner) { isShuffleEnabled ->
            Log.d("NowPlayingFragment", "Shuffle status changed to: $isShuffleEnabled")
            val shuffleIcon = if (isShuffleEnabled) {
                Log.d("NowPlayingFragment", "Setting shuffle icon to active")
                R.drawable.ic_shuffle_active
            } else {
                Log.d("NowPlayingFragment", "Setting shuffle icon to default")
                R.drawable.ic_shuffle
            }
            binding.btnShuffle.setImageResource(shuffleIcon)
        }

        musicPlayerViewModel.repeatMode.observe(viewLifecycleOwner) { repeatMode ->
            val repeatIcon = when (repeatMode) {
                RepeatMode.OFF -> R.drawable.ic_repeat
                RepeatMode.ALL -> R.drawable.ic_repeat_all
                RepeatMode.ONE -> R.drawable.ic_repeat_one
            }
            binding.btnRepeat.setImageResource(repeatIcon)
        }

        musicPlayerViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.seekBar.progress = progress
            updateCurrentTime(progress.toLong())
        }

        musicPlayerViewModel.duration.observe(viewLifecycleOwner) { duration ->
            binding.seekBar.max = duration
        }
    }

    private fun updateCurrentTime(position: Long) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(position)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(position) -
                TimeUnit.MINUTES.toSeconds(minutes)
        binding.tvCurrentTime.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateDownloadButtonVisibility(song: Song) {
        lifecycleScope.launch {
            try {
                val songRepository = SongRepository(
                    AppDatabase.getInstance(requireContext()).songDao(),
                    requireContext()
                )

                val isOnlineSong = song.filePath.startsWith("http")
                val isDownloaded = if (isOnlineSong) {
                    songRepository.isDownloaded(song.id)
                } else {
                    true // Local songs are already "downloaded"
                }

                // Only show download button for online songs
                binding.btnDownload.visibility = if (isOnlineSong) {
                    if (isDownloaded) {
                        binding.btnDownload.setImageResource(R.drawable.ic_download_done)
                    } else {
                        binding.btnDownload.setImageResource(R.drawable.ic_download)
                    }
                    View.VISIBLE
                } else {
                    View.GONE
                }

                // Set up download click listener
                binding.btnDownload.setOnClickListener {
                    if (!isDownloaded) {
                        Toast.makeText(context,
                            "Downloading ${song.title}...",
                            Toast.LENGTH_SHORT).show()
                        downloadManager.enqueueDownload(song)
                        binding.btnDownload.isEnabled = false // Prevent multiple clicks
                    } else {
                        Toast.makeText(context,
                            "Song already downloaded",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("NowPlayingFragment", "Error updating download button: ${e.message}")
                binding.btnDownload.visibility = View.GONE
            }
        }
    }

    private fun showAudioDeviceSelectionDialog() {
        val dialog = AudioDeviceSelectionDialog.newInstance(musicPlayerViewModel)
        dialog.show(childFragmentManager, "AudioDeviceSelectionDialog")
    }
    
    private fun observeAudioDevices() {
        // Observe active audio device changes
        musicPlayerViewModel.activeAudioDevice.observe(viewLifecycleOwner) { device ->
            // Update UI when active device changes
            device?.let { updateAudioDeviceIndicator(it) }
        }
        
        // Observe audio device errors
        musicPlayerViewModel.audioDeviceError.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                musicPlayerViewModel.clearAudioDeviceError()
            }
        }
    }
    
    private fun updateAudioDeviceIndicator(device: com.example.purrytify.data.model.AudioDevice) {
        val activity = activity as? com.example.purrytify.ui.main.MainActivity
        activity?.let { mainActivity ->
            // Try to update mini player
            val audioIndicator = mainActivity.binding.miniPlayer.audioOutputIndicator
            val deviceIcon = mainActivity.binding.miniPlayer.audioDeviceIcon
            val deviceName = mainActivity.binding.miniPlayer.audioDeviceName
            
            // Make indicator visible
            audioIndicator.visibility = View.VISIBLE
            
            // Set device name
            deviceName.text = device.name
            
            // Set appropriate icon based on device type
            when (device.type) {
                com.example.purrytify.data.model.AudioDeviceType.BLUETOOTH -> {
                    deviceIcon.setImageResource(R.drawable.ic_bluetooth_audio)
                }
                com.example.purrytify.data.model.AudioDeviceType.WIRED_HEADSET -> {
                    deviceIcon.setImageResource(R.drawable.ic_headset)
                }
                com.example.purrytify.data.model.AudioDeviceType.INTERNAL_SPEAKER -> {
                    deviceIcon.setImageResource(R.drawable.ic_speaker)
                }
                com.example.purrytify.data.model.AudioDeviceType.USB_AUDIO -> {
                    deviceIcon.setImageResource(R.drawable.ic_usb_audio)
                }
                else -> {
                    deviceIcon.setImageResource(R.drawable.ic_audio_output)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}