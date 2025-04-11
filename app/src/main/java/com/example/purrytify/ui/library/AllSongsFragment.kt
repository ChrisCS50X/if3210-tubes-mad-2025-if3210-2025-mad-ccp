package com.example.purrytify.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.purrytify.databinding.FragmentAllSongsBinding
import com.example.purrytify.ui.adapter.SongAdapter

class AllSongsFragment : Fragment() {

    private var _binding: FragmentAllSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter
    private val viewModel: LibraryViewModel by viewModels { LibraryViewModelFactory(requireActivity().application, requireContext().applicationContext) }

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
            // Handle song click (play the song)
            Toast.makeText(requireContext(), "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
            viewModel.playSong(song)
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