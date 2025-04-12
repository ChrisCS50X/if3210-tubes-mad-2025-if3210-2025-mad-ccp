package com.example.purrytify.ui.player

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
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.purrytify.utils.BackgroundColorProvider

class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val args: NowPlayingFragmentArgs by navArgs()

    private val musicPlayerViewModel: MusicPlayerViewModel by activityViewModels {
        MusicPlayerViewModelFactory(
            requireActivity().application,
            SongRepository(AppDatabase.getInstance(requireContext()).songDao(), requireContext().applicationContext)
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
        Log.d("NowPlayingFragment", "Setting up UI")

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        args.song?.let { song ->
            updateSongInfo(song)
        }
    }

    private fun updateSongInfo(song: com.example.purrytify.data.model.Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist

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
            val backgroundColor = BackgroundColorProvider.getColorForSong(song)
            BackgroundColorProvider.applyColorSafely(binding.layoutNowPlaying, backgroundColor)
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
                musicPlayerViewModel.playPreviousSong()
            }
        }

        binding.btnNext.setOnClickListener {
            musicPlayerViewModel.playNextSong()
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

    private fun observeViewModel() {
        musicPlayerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let { updateSongInfo(it) }
        }

        musicPlayerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
            binding.btnPlayPause.setImageResource(icon)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}