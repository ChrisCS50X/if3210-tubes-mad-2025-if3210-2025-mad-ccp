package com.example.purrytify.ui.home

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.ui.adapter.NewSongsAdapter
import com.example.purrytify.ui.adapter.RecentlyPlayedAdapter
import androidx.core.net.toUri
import com.example.purrytify.data.model.Song
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSeekBar: Runnable

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        observeViewModel()
        setupMiniPlayerControls()
        setupSeekBarUpdater()
        viewModel.loadHomeData()
    }

    private fun setupViewModel() {
        val songDao = AppDatabase.getInstance(requireContext()).songDao()
        val songRepository = SongRepository(songDao)

        val viewModelFactory = HomeViewModelFactory(songRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[HomeViewModel::class.java]
    }

    private fun setupSeekBarUpdater() {
        updateSeekBar = object : Runnable {
            override fun run() {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            binding.miniPlayer.miniSeekBar.max = player.duration
                            binding.miniPlayer.miniSeekBar.progress = player.currentPosition
                        }
                    }
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                    binding.miniPlayer.miniSeekBar.progress = 0
                }
            }
        }
    }

    private fun setupMiniPlayerControls() {
        binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    binding.miniPlayer.btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    it.start()
                    binding.miniPlayer.btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
            }
        }

        binding.miniPlayer.miniSeekBar.setOnTouchListener { _, _ ->
            // Disable seeking from mini player
            true
        }

        binding.miniPlayer.root.setOnClickListener {
            viewModel.currentlyPlayingSong.value?.let { song ->
                val bundle = Bundle().apply {
                    putParcelable("song", song)
                    putBoolean("isPlaying", mediaPlayer?.isPlaying ?: false)
                }

                findNavController().navigate(R.id.action_navigation_home_to_now_playing, bundle)
            }
        }
    }

    private fun playSong(song: Song) {
        mediaPlayer?.stop()
        mediaPlayer?.release()

        try {
            val audioUri = song.filePath.toUri()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(requireContext(), audioUri)
                prepare()
                start()
            }

            viewModel.playSong(song)

            binding.miniPlayer.root.visibility = View.VISIBLE
            binding.miniPlayer.tvMiniTitle.text = song.title
            binding.miniPlayer.tvMiniArtist.text = song.artist
            binding.miniPlayer.btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            binding.miniPlayer.ivMiniCover.setImageURI(song.coverUrl?.toUri())

            binding.miniPlayer.miniSeekBar.max = mediaPlayer?.duration ?: 0
            handler.removeCallbacks(updateSeekBar)
            handler.postDelayed(updateSeekBar, 0)

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.rvNewSongs.visibility = View.VISIBLE
                binding.tvNewSongsEmpty.visibility = View.GONE

                binding.rvNewSongs.apply {
                    layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    adapter = NewSongsAdapter(songs) { song ->
                        viewModel.playSong(song)
                        playSong(song)
                    }
                }
            } else {
                binding.rvNewSongs.visibility = View.GONE
                binding.tvNewSongsEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.rvRecentlyPlayed.visibility = View.VISIBLE
                binding.tvRecentlyPlayedEmpty.visibility = View.GONE

                binding.rvRecentlyPlayed.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = RecentlyPlayedAdapter(songs) { song ->
                        viewModel.playSong(song)
                        playSong(song)
                    }
                }
            } else {
                binding.rvRecentlyPlayed.visibility = View.GONE
                binding.tvRecentlyPlayedEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateSeekBar)
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekBar)
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayer?.isPlaying == true) {
            handler.postDelayed(updateSeekBar, 0)
        }
    }
}