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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupEmptyStateButtons()
        observeViewModel()
        homeViewModel.loadHomeData()
    }

    private fun setupViewModel() {
        val songDao = AppDatabase.getInstance(requireContext()).songDao()
        val songRepository = SongRepository(songDao, requireContext().applicationContext)

        val viewModelFactory = HomeViewModelFactory(songRepository,requireContext().applicationContext)
        homeViewModel = ViewModelProvider(this, viewModelFactory)[HomeViewModel::class.java]
    }

    private fun setupEmptyStateButtons() {
        binding.btnBrowseMusic.setOnClickListener {
            // Pindah ke halaman library
            findNavController().navigate(R.id.navigation_library)
        }
    }

    private fun observeViewModel() {
        homeViewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.rvNewSongs.visibility = View.VISIBLE
                binding.layoutNewSongsEmpty.visibility = View.GONE

                binding.rvNewSongs.apply {
                    layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    adapter = NewSongsAdapter(songs) { song ->
                        musicPlayerViewModel.playSong(song)
                    }
                }
            } else {
                binding.rvNewSongs.visibility = View.GONE
                binding.layoutNewSongsEmpty.visibility = View.VISIBLE
            }
        }

        homeViewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.rvRecentlyPlayed.visibility = View.VISIBLE
                binding.layoutRecentlyPlayedEmpty.visibility = View.GONE

                binding.rvRecentlyPlayed.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = RecentlyPlayedAdapter(songs) { song ->
                        musicPlayerViewModel.playSong(song)
                    }
                }
            } else {
                binding.rvRecentlyPlayed.visibility = View.GONE
                binding.layoutRecentlyPlayedEmpty.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}