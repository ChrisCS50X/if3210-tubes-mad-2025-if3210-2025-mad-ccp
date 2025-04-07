package com.example.purrytify.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.purrytify.databinding.FragmentLikedSongsBinding
import com.example.purrytify.ui.adapter.SongAdapter

class LikedSongsFragment : Fragment() {

    private var _binding: FragmentLikedSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter
    private val viewModel: LibraryViewModel by viewModels { LibraryViewModelFactory(requireActivity().application) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLikedSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter()
        binding.recyclerViewLikedSongs.apply {
            adapter = songAdapter
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        songAdapter.setOnItemClickListener { song ->
            // Handle song click (play the song)
            Toast.makeText(requireContext(), "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
            viewModel.playSong(song)
        }
    }

    private fun observeViewModel() {
        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            binding.progressBar.visibility = View.GONE

            if (songs.isEmpty()) {
                binding.textNoLikedSongs.visibility = View.VISIBLE
                binding.recyclerViewLikedSongs.visibility = View.GONE
            } else {
                binding.textNoLikedSongs.visibility = View.GONE
                binding.recyclerViewLikedSongs.visibility = View.VISIBLE
                songAdapter.setSongs(songs)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}