package com.example.purrytify.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.ui.adapter.NewSongsAdapter
import com.example.purrytify.ui.adapter.RecentlyPlayedAdapter
import com.example.purrytify.ui.player.MusicPlayerViewModel
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        observeViewModel()
        setupMiniPlayer()
        homeViewModel.loadHomeData()
    }

    private fun setupViewModel() {
        val songDao = AppDatabase.getInstance(requireContext()).songDao()
        val songRepository = SongRepository(songDao)

        val viewModelFactory = HomeViewModelFactory(songRepository)
        homeViewModel = ViewModelProvider(this, viewModelFactory)[HomeViewModel::class.java]
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
            musicPlayerViewModel.togglePlayPause()
        }

        binding.miniPlayer.root.setOnClickListener {
            musicPlayerViewModel.currentSong.value?.let { song ->
                navigateToNowPlaying(song)
            }
        }

        // Update mini player seek bar with current progress
        musicPlayerViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.miniPlayer.miniSeekBar.progress = progress
        }

        // Update seek bar max value when duration changes
        musicPlayerViewModel.duration.observe(viewLifecycleOwner) { duration ->
            binding.miniPlayer.miniSeekBar.max = duration
        }
    }

    private fun observeViewModel() {
        // Observe home data
        homeViewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.rvNewSongs.visibility = View.VISIBLE
                binding.tvNewSongsEmpty.visibility = View.GONE

                binding.rvNewSongs.apply {
                    layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    adapter = NewSongsAdapter(songs) { song ->
                        musicPlayerViewModel.playSong(song)
                    }
                }
            } else {
                binding.rvNewSongs.visibility = View.GONE
                binding.tvNewSongsEmpty.visibility = View.VISIBLE
            }
        }

        homeViewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.rvRecentlyPlayed.visibility = View.VISIBLE
                binding.tvRecentlyPlayedEmpty.visibility = View.GONE

                binding.rvRecentlyPlayed.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = RecentlyPlayedAdapter(songs) { song ->
                        musicPlayerViewModel.playSong(song)
                    }
                }
            } else {
                binding.rvRecentlyPlayed.visibility = View.GONE
                binding.tvRecentlyPlayedEmpty.visibility = View.VISIBLE
            }
        }

        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe music player
        musicPlayerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                updateMiniPlayer(it)
                binding.miniPlayer.root.visibility = View.VISIBLE
            } ?: run {
                binding.miniPlayer.root.visibility = View.GONE
            }
        }

        musicPlayerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause // Create this resource
            } else {
                R.drawable.ic_play // Create this resource
            }
            binding.miniPlayer.btnMiniPlayPause.setImageResource(icon)
        }
    }

    private fun updateMiniPlayer(song: Song) {
        binding.miniPlayer.tvMiniTitle.text = song.title
        binding.miniPlayer.tvMiniArtist.text = song.artist

        // Load cover art with Glide
        Glide.with(this)
            .load(song.coverUrl)
            .placeholder(R.drawable.placeholder_album) // Create this resource
            .error(R.drawable.placeholder_album)
            .into(binding.miniPlayer.ivMiniCover)
    }

    private fun navigateToNowPlaying(song: Song) {
        val action = HomeFragmentDirections.actionNavigationHomeToNowPlaying(
            song = song,
            isPlaying = musicPlayerViewModel.isPlaying.value ?: false
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}