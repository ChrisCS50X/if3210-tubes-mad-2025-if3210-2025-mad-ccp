package com.example.purrytify.ui.player

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.FragmentNowPlayingBinding
import java.util.concurrent.TimeUnit

class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

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

        arguments?.let { args ->
            currentSong = args.getParcelable("song")
            isPlaying = args.getBoolean("isPlaying", false)
        }

        setupUI()
        setupControls()
        initializeMediaPlayer()
    }

    private fun setupUI() {
        currentSong?.let { song ->
            binding.tvSongTitle.text = song.title
            binding.tvArtistName.text = song.artist
            binding.ivAlbumCover.setImageURI(song.coverUrl?.toUri())

            val minutes = TimeUnit.MILLISECONDS.toMinutes(song.duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(song.duration) -
                    TimeUnit.MINUTES.toSeconds(minutes)
            binding.tvTotalDuration.text = String.format("%02d:%02d", minutes, seconds)
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            togglePlayback()
        }

        binding.btnPrevious.setOnClickListener {
            mediaPlayer?.let {
                if (it.currentPosition > 3000) {
                    it.seekTo(0)
                } else {
                    it.seekTo(0)
                }
                updateSeekBar()
            }
        }

        binding.btnNext.setOnClickListener {
            mediaPlayer?.seekTo(0)
            updateSeekBar()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    updateCurrentTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    private fun initializeMediaPlayer() {
        currentSong?.let { song ->
            try {
                if (mediaPlayer == null) {
                    val audioUri = song.filePath.toUri()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(requireContext(), audioUri)
                        prepare()

                        binding.seekBar.max = duration
                    }
                }

                if (isPlaying) {
                    mediaPlayer?.start()
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }

                updateSeekBar()
            } catch (e: Exception) {
                binding.tvSongTitle.text = "Error playing song"
                binding.tvArtistName.text = e.message ?: "Unknown error"
            }
        }
    }

    private fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                it.start()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
    }

    private fun updateSeekBar() {
        mediaPlayer?.let {
            binding.seekBar.progress = it.currentPosition
            updateCurrentTime(it.currentPosition.toLong())

            runnable = Runnable { updateSeekBar() }
            handler.postDelayed(runnable, 1000)
        }
    }

    private fun updateCurrentTime(position: Long) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(position)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(position) -
                TimeUnit.MINUTES.toSeconds(minutes)
        binding.tvCurrentTime.text = String.format("%02d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.let {
            if (it.isPlaying) {
                updateSeekBar()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnable)
        _binding = null
    }
}