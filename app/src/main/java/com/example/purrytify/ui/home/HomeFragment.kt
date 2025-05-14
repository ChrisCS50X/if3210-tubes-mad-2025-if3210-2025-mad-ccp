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
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.Song
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.ui.adapter.ChartsAdapter
import com.example.purrytify.ui.adapter.NewSongsAdapter
import com.example.purrytify.ui.adapter.RecentlyPlayedAdapter
import com.example.purrytify.ui.player.MusicPlayerViewModel
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    private val paddingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val padding = intent?.getIntExtra("padding", 0) ?: 0

            // Apply padding to the recently played list (vertical scrolling list)
            binding.rvRecentlyPlayed.setPadding(
                binding.rvRecentlyPlayed.paddingLeft,
                binding.rvRecentlyPlayed.paddingTop,
                binding.rvRecentlyPlayed.paddingRight,
                padding // Dynamic padding based on mini player height
            )
        }
    }

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

        // Register for padding updates
        val filter = IntentFilter("com.example.purrytify.UPDATE_BOTTOM_PADDING")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(paddingReceiver, filter)
    }

    private fun setupViewModel() {
        val songDao = AppDatabase.getInstance(requireContext()).songDao()
        val songRepository = SongRepository(songDao, requireContext().applicationContext)

        val viewModelFactory = HomeViewModelFactory(songRepository, requireContext().applicationContext)
        homeViewModel = ViewModelProvider(this, viewModelFactory)[HomeViewModel::class.java]
    }

    private fun setupEmptyStateButtons() {
        binding.btnBrowseMusic.setOnClickListener {
            findNavController().navigate(com.example.purrytify.R.id.navigation_library)
        }
    }

    private fun observeViewModel() {
        // Observe charts
        homeViewModel.chartItems.observe(viewLifecycleOwner) { charts ->
            if (charts.isNotEmpty()) {
                binding.rvCharts.visibility = View.VISIBLE
                binding.layoutChartsEmpty.visibility = View.GONE

                binding.rvCharts.apply {
                    layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    adapter = ChartsAdapter(charts) { chart ->
                        navigateToChartDetail(chart.type, chart.id.split("_").getOrNull(1) ?: "ID")
                    }
                }
            } else {
                binding.rvCharts.visibility = View.GONE
                binding.layoutChartsEmpty.visibility = View.VISIBLE
            }
        }

        homeViewModel.chartsLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.rvCharts.visibility = View.GONE
                binding.layoutChartsEmpty.visibility = View.GONE
            }
        }

        // Observe new songs
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

        // Observe recently played
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

    private fun navigateToChartDetail(chartType: String, countryCode: String) {
        val action = HomeFragmentDirections.actionNavigationHomeToChartDetail(
            chartType = chartType,
            countryCode = countryCode
        )
        findNavController().navigate(action)
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