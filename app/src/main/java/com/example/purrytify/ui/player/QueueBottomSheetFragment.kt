package com.example.purrytify.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentQueueBottomSheetBinding
import com.example.purrytify.ui.adapter.QueueAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


/**
 * Fragment buat nampilin dan ngatur antrian lagu
 */
class QueueBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentQueueBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val musicPlayerViewModel: MusicPlayerViewModel by activityViewModels()
    private lateinit var queueAdapter: QueueAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()

        binding.btnClearQueue.setOnClickListener {
            musicPlayerViewModel.clearQueue()
        }
    }

    private fun setupRecyclerView() {
        queueAdapter = QueueAdapter(
            requireContext(),
            emptyList(),
            object : QueueAdapter.QueueItemListener {
                override fun onRemoveFromQueue(position: Int) {
                    val songs = musicPlayerViewModel.queueLiveData.value ?: emptyList()
                    if (position < songs.size) {
                        musicPlayerViewModel.removeFromQueue(songs[position])
                    }
                }
            }
        )

        binding.recyclerViewQueue.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
        }
    }

    private fun setupObservers() {
        musicPlayerViewModel.queueLiveData.observe(viewLifecycleOwner) { songs ->
            queueAdapter.updateSongs(songs)

            // Tampilkan pesan jika queue kosong
            if (songs.isEmpty()) {
                binding.textNoSongs.visibility = View.VISIBLE
            } else {
                binding.textNoSongs.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}