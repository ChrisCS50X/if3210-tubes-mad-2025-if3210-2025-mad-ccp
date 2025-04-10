package com.example.purrytify.ui.home

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel

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
        viewModel.loadHomeData()
    }

    private fun setupViewModel() {
        val songDao = AppDatabase.getInstance(requireContext()).songDao()
        val songRepository = SongRepository(songDao)

        val viewModelFactory = HomeViewModelFactory(songRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[HomeViewModel::class.java]
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

                        try {
                            val audioUri = song.filePath.toUri()
                            val mediaPlayer = MediaPlayer()
                            mediaPlayer.setDataSource(requireContext(), audioUri)
                            mediaPlayer.prepare()
                            mediaPlayer.start()

                            Toast.makeText(requireContext(), "Now playing: ${song.title}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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

                        try {
                            val audioUri = song.filePath.toUri()
                            val mediaPlayer = MediaPlayer()
                            mediaPlayer.setDataSource(requireContext(), audioUri)
                            mediaPlayer.prepare()
                            mediaPlayer.start()

                            Toast.makeText(requireContext(), "Now playing: ${song.title}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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
        _binding = null
    }
}