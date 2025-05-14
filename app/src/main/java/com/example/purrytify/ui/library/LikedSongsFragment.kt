package com.example.purrytify.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentLikedSongsBinding
import com.example.purrytify.ui.adapter.SongAdapter
import com.example.purrytify.ui.player.MusicPlayerViewModel
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class LikedSongsFragment : Fragment() {

    private var _binding: FragmentLikedSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter
    private var currentSearchQuery: String = ""
    private var originalSongs: List<Song> = emptyList()


    private val paddingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val padding = intent?.getIntExtra("padding", 0) ?: 0
            binding.recyclerViewLikedSongs.setPadding(
                binding.recyclerViewLikedSongs.paddingLeft,
                binding.recyclerViewLikedSongs.paddingTop,
                binding.recyclerViewLikedSongs.paddingRight,
                padding
            )
        }
    }

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModelFactory(requireActivity().application, requireContext().applicationContext)
    }

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
        _binding = FragmentLikedSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        // Register for padding updates
        val filter = IntentFilter("com.example.purrytify.UPDATE_BOTTOM_PADDING")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(paddingReceiver, filter)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            requireContext(),
            onAddToQueueListener = { song ->
                musicPlayerViewModel.addToQueue(song)
                Toast.makeText(requireContext(), "${song.title} added to queue", Toast.LENGTH_SHORT).show()
            },
            onEditListener = { song ->
                Toast.makeText(requireContext(), "Edit ${song.title}", Toast.LENGTH_SHORT).show()
            },
            onDeleteListener = { song ->
                musicPlayerViewModel.handleSongDeleted(song.id)

                viewModel.deleteSongById(song.id)
            }
        )

        binding.recyclerViewLikedSongs.apply {
            adapter = songAdapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }

        songAdapter.setOnItemClickListener { song ->
            viewModel.playSong(song)

            musicPlayerViewModel.playSong(song)
        }
    }

    private fun showPopupMenu(song: Song, anchor: View) {
        val popupMenu = PopupMenu(requireContext(), anchor)
        popupMenu.menuInflater.inflate(R.menu.menu_song_item, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    Toast.makeText(requireContext(), "Edit ${song.title}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_delete -> {
                    viewModel.deleteSongById(song.id)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun observeViewModel() {
        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            binding.progressBar.visibility = View.GONE
            originalSongs = songs

            if (currentSearchQuery.isEmpty()) {
                updateSongsList(songs)
            } else {
                filterSongs(currentSearchQuery)
            }
        }
    }

    fun updateSearch(query: String) {
        currentSearchQuery = query
        if (::songAdapter.isInitialized) {
            filterSongs(query)
        }
    }

    private fun filterSongs(query: String) {
        if (query.isEmpty()) {
            updateSongsList(originalSongs)
            return
        }

        val filteredList = originalSongs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true)
        }

        updateSongsList(filteredList)
    }

    private fun updateSongsList(songs: List<Song>) {
        if (songs.isEmpty()) {
            binding.textNoLikedSongs.visibility = View.VISIBLE
            binding.recyclerViewLikedSongs.visibility = View.GONE
        } else {
            binding.textNoLikedSongs.visibility = View.GONE
            binding.recyclerViewLikedSongs.visibility = View.VISIBLE
            songAdapter.setSongs(songs)
        }
    }

    override fun onDestroyView() {
        // Unregister receiver
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(paddingReceiver)
        } catch (e: Exception) {
            // Handle case where receiver wasn't registered
        }

        super.onDestroyView()
        _binding = null
    }
}