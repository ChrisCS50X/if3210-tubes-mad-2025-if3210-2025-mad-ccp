package com.example.purrytify.ui.charts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.ChartRepository
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentChartDetailBinding
import com.example.purrytify.service.DownloadManager
import com.example.purrytify.ui.adapter.ChartSongsAdapter
import com.example.purrytify.ui.player.MusicPlayerViewModel
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class ChartDetailFragment : Fragment() {

    private var _binding: FragmentChartDetailBinding? = null
    private val binding get() = _binding!!

    private val paddingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val padding = intent?.getIntExtra("padding", 0) ?: 0
            binding.rvChartSongs.setPadding(
                binding.rvChartSongs.paddingLeft,
                binding.rvChartSongs.paddingTop,
                binding.rvChartSongs.paddingRight,
                padding
            )
        }
    }

    private val args: ChartDetailFragmentArgs by navArgs()
    private val viewModel: ChartDetailViewModel by viewModels {
        ChartDetailViewModelFactory(
            ChartRepository(TokenManager(requireContext())),
            args.chartType,
            args.countryCode
        )
    }

    private val musicPlayerViewModel: MusicPlayerViewModel by activityViewModels {
        MusicPlayerViewModelFactory(
            requireActivity().application,
            SongRepository(AppDatabase.getInstance(requireContext()).songDao(), requireContext().applicationContext)
        )
    }

    private lateinit var adapter: ChartSongsAdapter
    private lateinit var downloadManager: DownloadManager
    private lateinit var songRepository: SongRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize download manager and repository
        downloadManager = DownloadManager(requireContext())
        downloadManager.createNotificationChannel()

        songRepository = SongRepository(
            AppDatabase.getInstance(requireContext()).songDao(),
            requireContext()
        )

        setupUI()
        setupRecyclerView()
        observeViewModel()
        viewModel.loadChartSongs()

        // Register for padding updates
        val filter = IntentFilter("com.example.purrytify.UPDATE_BOTTOM_PADDING")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(paddingReceiver, filter)
    }

    private fun setupUI() {
        binding.tvChartTitle.text = when (args.chartType) {
            "global" -> "Top 50 Global"
            "local" -> "Top 10 ${args.countryCode}"
            else -> "Chart"
        }

        val bannerImageResource = when (args.chartType) {
            "global" -> R.drawable.global_chart_cover
            "local" -> R.drawable.local_chart_cover
            else -> R.drawable.placeholder_album
        }

        Glide.with(requireContext())
            .load(bannerImageResource)
            .into(binding.ivChartBanner)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChartSongsAdapter(
            emptyList(),
            { chartSong ->
                val song = chartSong.toSong()
                musicPlayerViewModel.playSong(song)
            },
            lifecycleScope,
            songRepository,
            downloadManager
        )

        binding.rvChartSongs.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.chartState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ChartState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.rvChartSongs.visibility = View.GONE
                    binding.tvNoSongs.visibility = View.GONE
                }
                is ChartState.Success -> {
                    binding.progressBar.visibility = View.GONE

                    if (state.songs.isNotEmpty()) {
                        binding.rvChartSongs.visibility = View.VISIBLE
                        binding.tvNoSongs.visibility = View.GONE
                        adapter.updateSongs(state.songs)
                    } else {
                        binding.rvChartSongs.visibility = View.GONE
                        binding.tvNoSongs.visibility = View.VISIBLE
                    }
                }
                is ChartState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.rvChartSongs.visibility = View.GONE
                    binding.tvNoSongs.visibility = View.VISIBLE
                    binding.tvNoSongs.text = state.message

                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
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

        adapter.clearObservers()
        _binding = null
        super.onDestroyView()
    }
}