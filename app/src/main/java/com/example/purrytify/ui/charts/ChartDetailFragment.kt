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
import android.graphics.Color
import com.example.purrytify.utils.BackgroundColorProvider
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.delay
import androidx.appcompat.app.AlertDialog
import com.example.purrytify.data.model.ChartSong

class ChartDetailFragment : Fragment() {

    private val TAG = "ChartDetailFragment"

    private var _binding: FragmentChartDetailBinding? = null
    private val binding get() = _binding!!

    private var downloadReceiver: BroadcastReceiver? = null

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

    private val countryNameMap = mapOf(
        "ID" to "Indonesia",
        "MY" to "Malaysia",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CH" to "Switzerland",
        "DE" to "Germany",
        "BR" to "Brazil"
    )

    private val args: ChartDetailFragmentArgs by navArgs()
    private val viewModel: ChartDetailViewModel by viewModels {
        ChartDetailViewModelFactory(
            ChartRepository(TokenManager(requireContext()), songRepository),
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

        // Register for download completion updates
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Download completion broadcast received")
                adapter.refreshDownloadStates()
            }
        }

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                downloadReceiver!!,
                IntentFilter("com.example.purrytify.DOWNLOAD_COMPLETE")
            )
    }

    private fun setupUI() {
        // Country codes supported by the server
        val supportedCountries = listOf("ID", "MY", "US", "GB", "CH", "DE", "BR")

        // Set chart title and description based on type
        when (args.chartType) {
            "global" -> {
                binding.tvChartTitle.text = "Top 50 Global"
                binding.tvChartDescription.text = "Your daily update of the most played tracks right now - Global."
                binding.ivChartCover.setImageResource(R.drawable.global_chart_cover)
                setupDynamicBackground(R.drawable.global_chart_cover)
            }
            "local" -> {
                val countryCode = args.countryCode.uppercase()
                val countryName = countryNameMap[countryCode] ?: countryCode

                binding.tvChartTitle.text = "Top 10 ${countryName}"
                binding.tvChartDescription.text = "Most popular tracks in ${countryName} right now."

                // Set chart cover based on country code
                val resourceId = when (countryCode) {
                    "ID" -> R.drawable.id_chart_cover
                    "MY" -> R.drawable.my_chart_cover
                    "US" -> R.drawable.us_chart_cover
                    "GB" -> R.drawable.gb_chart_cover
                    "CH" -> R.drawable.ch_chart_cover
                    "DE" -> R.drawable.de_chart_cover
                    "BR" -> R.drawable.br_chart_cover
                    else -> R.drawable.unknown_chart_cover
                }

                binding.ivChartCover.setImageResource(resourceId)
                setupDynamicBackground(resourceId)

                // Show unsupported overlay if needed
                if (countryCode !in supportedCountries && countryCode != "GLOBAL") {
                    binding.unsupportedCountryOverlay.visibility = View.VISIBLE
                    val supportedCountryNames = supportedCountries.map {
                        countryNameMap[it] ?: it
                    }.joinToString(", ")
                    binding.tvSupportedCountries.text = "Available in: ${supportedCountryNames}"
                } else {
                    binding.unsupportedCountryOverlay.visibility = View.GONE
                }
            }
            "yours" -> {
                binding.tvChartTitle.text = "Top Mixes"
                binding.tvChartDescription.text = "Personalized mixes based on your listening habits."
                binding.ivChartCover.setImageResource(R.drawable.your_top_song)
                setupDynamicBackground(R.drawable.your_top_song)
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnPlayAll.setOnClickListener {
            viewModel.chartState.value?.let { state ->
                if (state is ChartState.Success && state.songs.isNotEmpty()) {
                    // Clear any existing queue first
                    musicPlayerViewModel.clearQueue()

                    // Get all songs from the chart
                    val chartSongs = state.songs

                    // Show a loading toast while preparing
                    Toast.makeText(
                        requireContext(),
                        "Loading ${chartSongs.size} songs from ${binding.tvChartTitle.text}...",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Play the first song immediately
                    val firstSong = chartSongs.first().toSong()
                    musicPlayerViewModel.playSong(firstSong)

                    // Add the remaining songs to the queue
                    if (chartSongs.size > 1) {
                        lifecycleScope.launch {
                            // Add a small delay to ensure first song starts properly
                            delay(300)

                            // Add remaining songs to queue
                            for (i in 1 until chartSongs.size) {
                                val song = chartSongs[i].toSong()
                                musicPlayerViewModel.addToQueue(song)
                            }

                            // Confirm queue completion with a new toast
                            Toast.makeText(
                                requireContext(),
                                "Added ${chartSongs.size - 1} more songs to queue",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No songs available to play",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Replace the existing btnDownloadAll.setOnClickListener with this:
        binding.btnDownloadAll.setOnClickListener {
            viewModel.chartState.value?.let { state ->
                if (state is ChartState.Success && state.songs.isNotEmpty()) {
                    lifecycleScope.launch {
                        // First, identify which songs need downloading
                        val songsToDownload = mutableListOf<ChartSong>()

                        Log.d(TAG, "Checking download status for ${state.songs.size} songs")

                        for (chartSong in state.songs) {
                            val song = chartSong.toSong()

                            // Check both ID and title/artist
                            val isDownloadedById = songRepository.isDownloaded(song.id)
                            val isDuplicate = songRepository.isSongAlreadyDownloaded(chartSong.title, chartSong.artist)

                            val isDownloaded = isDownloadedById || isDuplicate

                            Log.d(TAG, "Song ${song.title} (ID: ${song.id}): byId=$isDownloadedById, byTitleArtist=$isDuplicate, final=$isDownloaded")

                            if (!isDownloaded) {
                                songsToDownload.add(chartSong)
                            }
                        }

                        if (songsToDownload.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                "All songs already downloaded",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        // Ask for confirmation with accurate count
                        val alertDialog = AlertDialog.Builder(requireContext())
                            .setTitle("Download Songs")
                            .setMessage("Download ${songsToDownload.size} songs from ${binding.tvChartTitle.text}?")
                            .setPositiveButton("Download") { _, _ ->
                                // Start downloads
                                for (chartSong in songsToDownload) {
                                    val song = chartSong.toSong()
                                    downloadManager.enqueueDownload(song)
                                }

                                Toast.makeText(
                                    requireContext(),
                                    "Downloading ${songsToDownload.size} songs from ${binding.tvChartTitle.text}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .create()

                        alertDialog.show()
                    }
                } else {
                    Toast.makeText(requireContext(), "No songs available to download", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePlayAllButtonState(songCount: Int) {
        if (songCount > 0) {
            binding.btnPlayAll.isEnabled = true
            binding.btnPlayAll.alpha = 1.0f

            // Optional: Show song count on long press
            binding.btnPlayAll.setOnLongClickListener {
                Toast.makeText(
                    requireContext(),
                    "Play all $songCount songs",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        } else {
            binding.btnPlayAll.isEnabled = false
            binding.btnPlayAll.alpha = 0.5f
        }
    }

    private fun setupDynamicBackground(imageResId: Int) {
        try {
            // Updated colors to better match chart covers
            val baseColor = when (imageResId) {
                R.drawable.id_chart_cover -> Color.parseColor("#ec1e32")
                R.drawable.my_chart_cover -> Color.parseColor("#4100f5")
                R.drawable.us_chart_cover -> Color.parseColor("#b6156e")
                R.drawable.gb_chart_cover -> Color.parseColor("#880E4F")
                R.drawable.ch_chart_cover -> Color.parseColor("#f56a79")
                R.drawable.de_chart_cover -> Color.parseColor("#f25743")
                R.drawable.br_chart_cover -> Color.parseColor("#f59b23")
                R.drawable.your_top_song -> Color.parseColor("#f5532b")
                R.drawable.global_chart_cover -> Color.parseColor("#1E3264")
                else -> Color.parseColor("#FFFFFF")
            }

            // Apply the gradient background using existing provider
            val gradientDrawable = BackgroundColorProvider.createGradientDrawable(baseColor)
            binding.backgroundGradient.background = gradientDrawable

        } catch (e: Exception) {
            Log.e(TAG, "Error setting dynamic background: ${e.message}")
            // Fallback to black background
            binding.rootLayout.setBackgroundColor(Color.BLACK)
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
                    updatePlayAllButtonState(0)
                }
                is ChartState.Success -> {
                    binding.progressBar.visibility = View.GONE

                    if (state.songs.isNotEmpty()) {
                        binding.rvChartSongs.visibility = View.VISIBLE
                        binding.tvNoSongs.visibility = View.GONE
                        adapter.updateSongs(state.songs)
                        updatePlayAllButtonState(state.songs.size)
                    } else {
                        binding.rvChartSongs.visibility = View.GONE
                        binding.tvNoSongs.visibility = View.VISIBLE
                        updatePlayAllButtonState(0)
                    }
                }
                is ChartState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.rvChartSongs.visibility = View.GONE
                    binding.tvNoSongs.visibility = View.VISIBLE
                    binding.tvNoSongs.text = state.message
                    updatePlayAllButtonState(0)

                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(paddingReceiver)

            downloadReceiver?.let {
                LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers: ${e.message}")
        }

        adapter.clearObservers()
        _binding = null
        super.onDestroyView()
    }
}