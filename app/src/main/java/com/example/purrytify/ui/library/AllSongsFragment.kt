package com.example.purrytify.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentAllSongsBinding
import com.example.purrytify.ui.adapter.SongAdapter
import com.example.purrytify.ui.player.MusicPlayerViewModel
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory

class AllSongsFragment : Fragment() {

    private var _binding: FragmentAllSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter

    // Library ViewModel for data loading
    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModelFactory(requireActivity().application, requireContext().applicationContext)
    }

    // Shared music player ViewModel for playback
    private val musicPlayerViewModel: MusicPlayerViewModel by activityViewModels {
        MusicPlayerViewModelFactory(
            requireActivity().application,
            SongRepository(
                AppDatabase.getInstance(requireContext()).songDao(),
                requireContext().applicationContext
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter()
        binding.recyclerViewAllSongs.apply {
            adapter = songAdapter
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        songAdapter.setOnItemClickListener { song ->
            // First increment play count using LibraryViewModel
            viewModel.playSong(song)

            // Then play the song using MusicPlayerViewModel
            musicPlayerViewModel.playSong(song)
        }
    }

    private fun observeViewModel() {
        viewModel.allSongs.observe(viewLifecycleOwner) { songs ->
            binding.progressBar.visibility = View.GONE

            if (songs.isEmpty()) {
                binding.textNoSongs.visibility = View.VISIBLE
                binding.recyclerViewAllSongs.visibility = View.GONE
            } else {
                binding.textNoSongs.visibility = View.GONE
                binding.recyclerViewAllSongs.visibility = View.VISIBLE
                songAdapter.setSongs(songs)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}