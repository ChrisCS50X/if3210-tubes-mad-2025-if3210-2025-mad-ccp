package com.example.purrytify.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentNowPlayingBinding
import java.util.concurrent.TimeUnit

class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val args: NowPlayingFragmentArgs by navArgs()

    // Shared music player ViewModel
    private val musicPlayerViewModel: MusicPlayerViewModel by activityViewModels {
        MusicPlayerViewModelFactory(
            requireActivity().application,
            SongRepository(AppDatabase.getInstance(requireContext()).songDao())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupControls()
        observeViewModel()
    }

    private fun setupUI() {
        // Set up back button
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // If we received a song via arguments, display it
        args.song?.let { song ->
            updateSongInfo(song)
        }
    }

    private fun updateSongInfo(song: com.example.purrytify.data.model.Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist

        // Load album art with Glide
        Glide.with(this)
            .load(song.coverUrl)
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .into(binding.ivAlbumCover)

        // Format duration
        val minutes = TimeUnit.MILLISECONDS.toMinutes(song.duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(song.duration) -
                TimeUnit.MINUTES.toSeconds(minutes)
        binding.tvTotalDuration.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun setupControls() {
        // Play/Pause button
        binding.btnPlayPause.setOnClickListener {
            musicPlayerViewModel.togglePlayPause()
        }

        // Previous button - seek to start or previous song
        binding.btnPrevious.setOnClickListener {
            val currentPosition = musicPlayerViewModel.getCurrentPosition()
            if (currentPosition > 3000) {
                // If more than 3 seconds played, seek to beginning
                musicPlayerViewModel.seekTo(0)
            } else {
                // Otherwise go to previous song if implemented
                // For now, just seek to beginning
                musicPlayerViewModel.seekTo(0)
            }
        }

        // Next button - future implementation could go to next song
        binding.btnNext.setOnClickListener {
            // For now, just seek to beginning
            musicPlayerViewModel.seekTo(0)
        }

        // SeekBar interaction
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

    private fun observeViewModel() {
        // Update UI when current song changes
        musicPlayerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let { updateSongInfo(it) }
        }

        // Update play/pause button
        musicPlayerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause // Create this resource
            } else {
                R.drawable.ic_play // Create this resource
            }
            binding.btnPlayPause.setImageResource(icon)
        }

        // Update seek bar progress
        musicPlayerViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.seekBar.progress = progress
            updateCurrentTime(progress.toLong())
        }

        // Update seek bar max value
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}