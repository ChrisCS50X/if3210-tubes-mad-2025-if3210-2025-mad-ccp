package com.example.purrytify.ui.profile

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.databinding.FragmentProfileBinding
import com.example.purrytify.data.repository.UserRepository
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purrytify.ui.main.NetworkViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.model.EditProfile
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.ui.analytics.MonthSpinnerAdapter
import com.example.purrytify.ui.editprofile.EditProfileDialogFragment
import com.example.purrytify.ui.login.LoginActivity
import java.io.File

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private val networkViewModel: NetworkViewModel by activityViewModels()

    private var snackbar: Snackbar? = null

    // Reference to the splash screen view
    private lateinit var loadingView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        // Get the root view as a frame layout
        val rootView = FrameLayout(requireContext())

        // Add the main content view to the root
        rootView.addView(binding.root)

        // Inflate and add the splash screen on top
        loadingView = inflater.inflate(R.layout.splash, rootView, false)
        rootView.addView(loadingView)

        // Initially show loading
        loadingView.visibility = View.VISIBLE

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tokenManager = TokenManager(requireContext().applicationContext)
        val userRepository = UserRepository(tokenManager)
        val appDatabase = AppDatabase.getInstance(requireContext())
        val songRepository = SongRepository(appDatabase.songDao(), requireContext().applicationContext)
        val analyticsRepository = AnalyticsRepository(appDatabase.listeningStatsDao())
        val factory = ProfileViewModelFactory(userRepository, songRepository, analyticsRepository)

        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        // Initially hide all profile content since loading is shown
        hideProfileContent()

        observeProfile()
        observeNetworkStatus()
        setupLogoutButton()
        setupEditProfileButton()
        setupQrScanButton() // Add QR scan button setup
        observeLogoutEvent()
        setupSoundCapsule()

        // Trigger loading the profile
        viewModel.loadUserProfile()
    }

    private fun setupQrScanButton() {
        binding.btnQrScan.setOnClickListener {
            // Navigate to QR Scanner Fragment
            findNavController().navigate(R.id.navigation_qr_scanner)
        }
    }

    private fun observeProfile() {
        val tokenManager = TokenManager(requireContext().applicationContext)
        val repository = SongRepository(AppDatabase.getInstance(requireContext()).songDao(), requireContext().applicationContext)
        viewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileState.Loading -> {
                    // Show loading screen
                    showLoading(true)
                }
                is ProfileState.Success -> {
                    // Hide loading screen
                    showLoading(false)

                    val user = state.profile
                    binding.tvUsername.text = user.username
                    binding.tvLocation.text = user.location
                    viewModel.setUserId(tokenManager.getEmail())
                    lifecycleScope.launch {
                        val songsCount = repository.getOwnedSongsCount(tokenManager.getEmail())
                        binding.tvSongsCount.text = "$songsCount"
                        val heardsCount = repository.getHeardSongsCount(tokenManager.getEmail())
                        binding.tvListenedCount.text = "$heardsCount"
                    }
                    Glide.with(this)
                        .load("http://34.101.226.132:3000/uploads/profile-picture/${user.profilePhoto}")
                        .into(binding.ivProfile)
                }
                is ProfileState.Error -> {
                    // Hide loading screen
                    showLoading(false)
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewModel.likedCount.observe(viewLifecycleOwner) { likedCount ->
            binding.tvLikedCount.text = "$likedCount"
        }

        // Force loading state immediately to show splash screen
        if (viewModel.profileState.value !is ProfileState.Success) {
            showLoading(true)
        }
    }

    /**
     * Shows or hides the loading screen
     */
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            // Show loading screen
            loadingView.visibility = View.VISIBLE
            // Hide content
            hideProfileContent()
        } else {
            // Hide loading screen
            loadingView.visibility = View.GONE
            // Show content
            showProfileContent()
        }
    }

    private fun setupSoundCapsule() {
        val analyticsViewModel = viewModel.analyticsViewModel
        val soundCapsuleLayout = binding.soundCapsuleLayout

        // Initialize views using binding directly
        val spinnerMonth = soundCapsuleLayout.spinnerMonth
        val tvTimeListened = soundCapsuleLayout.tvTimeListened
        val tvTopArtist = soundCapsuleLayout.tvTopArtist
        val tvTopSong = soundCapsuleLayout.tvTopSong
        val tvDayStreak = soundCapsuleLayout.tvDayStreak
        val tvNoData = soundCapsuleLayout.tvNoData
        val dataContainer = soundCapsuleLayout.dataContainer
        val btnExportData = soundCapsuleLayout.btnExportData

        // Set up click listeners for analytics items
        soundCapsuleLayout.timeListenedContainer.setOnClickListener {
            navigateToAnalyticsDetail("timeListened")
        }

        soundCapsuleLayout.topArtistContainer.setOnClickListener {
            navigateToAnalyticsDetail("topArtist")
        }

        soundCapsuleLayout.topSongContainer.setOnClickListener {
            navigateToAnalyticsDetail("topSong")
        }

        soundCapsuleLayout.dayStreakContainer.setOnClickListener {
            navigateToAnalyticsDetail("dayStreak")
        }

        // Set up spinner
        analyticsViewModel.months.observe(viewLifecycleOwner) { months ->
            val adapter = MonthSpinnerAdapter(requireContext(), months)
            spinnerMonth.adapter = adapter

            // Load data for the current month
            if (months.isNotEmpty()) {
                val userId = TokenManager(requireContext()).getEmail()
                if (!userId.isNullOrEmpty()) {
                    analyticsViewModel.loadAnalyticsForMonth(userId, months[0])
                }
            }
        }

        // Handle month selection
        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMonth = analyticsViewModel.months.value?.get(position)
                val userId = TokenManager(requireContext()).getEmail()

                if (selectedMonth != null && !userId.isNullOrEmpty()) {
                    analyticsViewModel.loadAnalyticsForMonth(userId, selectedMonth)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Observe analytics data
        analyticsViewModel.currentAnalytics.observe(viewLifecycleOwner) { analytics ->
            if (analytics != null && analytics.hasData()) {
                // Show data
                tvNoData.visibility = View.GONE
                dataContainer.visibility = View.VISIBLE
                btnExportData.isEnabled = true

                // Update UI
                tvTimeListened.text = analytics.getFormattedTimeListened()
                tvTopArtist.text = analytics.topArtist ?: "-"
                tvTopSong.text = analytics.topSong ?: "-"

                if (analytics.dayStreakCount > 0 && analytics.dayStreakSong != null) {
                    val streakText = if (analytics.dayStreakCount == 1) {
                        "${analytics.dayStreakSong} (1 day - Streak started!)"
                    } else {
                        "${analytics.dayStreakSong} (${analytics.dayStreakCount} days)"
                    }
                    tvDayStreak.text = streakText
                } else {
                    tvDayStreak.text = "-"
                }
            } else {
                // Show no data message
                tvNoData.visibility = View.VISIBLE
                dataContainer.visibility = View.GONE
                btnExportData.isEnabled = false
            }
        }

        // Handle export button
        btnExportData.setOnClickListener {
            val userId = TokenManager(requireContext()).getEmail()
            if (!userId.isNullOrEmpty()) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val file = analyticsViewModel.exportAnalyticsToCsv(userId, downloadsDir)

                if (file != null) {
                    shareExportedFile(file)
                } else {
                    Toast.makeText(requireContext(), "Error exporting analytics data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareExportedFile(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "com.example.purrytify.provider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/csv"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Analytics Data"))
    }

    /**
     * Navigate to the appropriate analytics detail screen based on the clicked item
     */
    private fun navigateToAnalyticsDetail(detailType: String) {
        val userId = TokenManager(requireContext()).getEmail() ?: return

        // Get the currently selected month and year
        val selectedMonthPosition = binding.soundCapsuleLayout.spinnerMonth.selectedItemPosition
        val selectedMonth = viewModel.analyticsViewModel.months.value?.getOrNull(selectedMonthPosition) ?: return

        when (detailType) {
            "timeListened" -> {
                findNavController().navigate(
                    ProfileFragmentDirections.actionNavigationProfileToTimeListenedDetailFragment(
                        userId, selectedMonth.year, selectedMonth.month
                    )
                )
            }
            "topArtist" -> {
                findNavController().navigate(
                    ProfileFragmentDirections.actionNavigationProfileToTopArtistDetailFragment(
                        userId, selectedMonth.year, selectedMonth.month
                    )
                )
            }
            "topSong" -> {
                findNavController().navigate(
                    ProfileFragmentDirections.actionNavigationProfileToTopSongDetailFragment(
                        userId, selectedMonth.year, selectedMonth.month
                    )
                )
            }
            "dayStreak" -> {
                findNavController().navigate(
                    ProfileFragmentDirections.actionNavigationProfileToDayStreakDetailFragment(
                        userId, selectedMonth.year, selectedMonth.month
                    )
                )
            }
        }
    }

    /**
     * Hide all profile content elements
     */
    private fun hideProfileContent() {
        binding.ivProfile.visibility = View.GONE
        binding.tvUsername.visibility = View.GONE
        binding.tvLocation.visibility = View.GONE
        binding.tvSongsCount.visibility = View.GONE
        binding.tvListenedCount.visibility = View.GONE
        binding.tvLikedCount.visibility = View.GONE
        binding.btnLogout.visibility = View.GONE
        binding.btnQrScan.visibility = View.GONE // Add QR scan button
        binding.btnEditProfileMain.visibility = View.GONE
        binding.soundCapsuleLayout.root.visibility = View.GONE
    }

    /**
     * Show all profile content elements
     */
    private fun showProfileContent() {
        binding.ivProfile.visibility = View.VISIBLE
        binding.tvUsername.visibility = View.VISIBLE
        binding.tvLocation.visibility = View.VISIBLE
        binding.tvSongsCount.visibility = View.VISIBLE
        binding.tvListenedCount.visibility = View.VISIBLE
        binding.tvLikedCount.visibility = View.VISIBLE
        binding.btnLogout.visibility = View.VISIBLE
        binding.btnQrScan.visibility = View.VISIBLE // Add QR scan button
        binding.btnEditProfileMain.visibility = View.VISIBLE
        binding.soundCapsuleLayout.root.visibility = View.VISIBLE
    }

    private fun observeNetworkStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkViewModel.isConnected.collectLatest { isConnected ->
                    if (!isConnected) {
                        if (snackbar == null) {
                            snackbar = Snackbar.make(
                                binding.root,
                                "No internet connection. Some features may not work.",
                                Snackbar.LENGTH_INDEFINITE
                            )
                            val snackbarView = snackbar!!.view
                            val params = snackbarView.layoutParams as FrameLayout.LayoutParams
                            params.gravity = Gravity.TOP // Set posisi ke atas
                            params.topMargin = 16
                            snackbarView.layoutParams = params
                            snackbar!!.show()
                        }
                    } else {
                        snackbar?.dismiss()
                        snackbar = null
                        viewModel.loadUserProfile()
                    }
                    // Nonaktifkan tombol edit jika tidak terhubung
                    binding.btnEditProfileMain.isEnabled = isConnected
                }
            }
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setupEditProfileButton() {
        binding.btnEditProfileMain.setOnClickListener {
            showEditProfileDialog()
        }
    }

    private fun showEditProfileDialog() {
        val currentUserProfile = viewModel.profileState.value
        if (currentUserProfile is ProfileState.Success) {
            // Create EditProfile object from current user data
            val editableProfile = EditProfile(
                username = currentUserProfile.profile.username,
                location = currentUserProfile.profile.location,
                profilePhoto = currentUserProfile.profile.profilePhoto
            )

            // Create and show the edit profile dialog
            val editProfileDialog = EditProfileDialogFragment.newInstance(editableProfile)

            // Set callback to refresh profile when dialog is dismissed after successful update
            editProfileDialog.onProfileUpdated = {
                viewModel.loadUserProfile()
            }

            editProfileDialog.show(childFragmentManager, "EditProfileDialog")
        } else {
            Toast.makeText(requireContext(), "Unable to edit profile at this time", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeLogoutEvent() {
        viewModel.logoutEvent.observe(viewLifecycleOwner) { shouldLogout ->
            if (shouldLogout) {
                // Pindah ke halaman login
                navigateToLogin()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }


    override fun onDestroyView() {
        snackbar?.dismiss() // Memastikan snackbar terhapus
        snackbar = null // Menghindari memory leak
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        snackbar?.dismiss() // Dismiss Snackbar saat fragment tidak aktif
        snackbar = null
    }
}