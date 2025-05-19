package com.example.purrytify.ui.profile

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.databinding.FragmentProfileBinding
import com.example.purrytify.data.repository.UserRepository
import com.example.purrytify.ui.main.MainActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purrytify.ui.main.NetworkViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.example.purrytify.R
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.ui.login.LoginActivity
import com.example.purrytify.ui.player.MusicPlayerViewModelFactory

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
        val songRepository = SongRepository(AppDatabase.getInstance(requireContext()).songDao(), requireContext().applicationContext)
        val factory = ProfileViewModelFactory(userRepository, songRepository)

        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        // Initially hide all profile content since loading is shown
        hideProfileContent()

        observeProfile()
        observeNetworkStatus()
        setupLogoutButton()
        observeLogoutEvent()

        // Trigger loading the profile
        viewModel.loadUserProfile()
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
        binding.btnEditProfileMain.visibility = View.GONE
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
        binding.btnEditProfileMain.visibility = View.VISIBLE
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