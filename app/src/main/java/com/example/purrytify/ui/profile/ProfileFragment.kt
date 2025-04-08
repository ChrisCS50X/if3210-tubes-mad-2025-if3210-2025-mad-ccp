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

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private val networkViewModel: NetworkViewModel by activityViewModels()

    private var snackbar: Snackbar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tokenManager = TokenManager(requireContext().applicationContext)
        val userRepository = UserRepository(tokenManager)
        val factory = ProfileViewModelFactory(userRepository)

        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        observeProfile()
        observeNetworkStatus()
    }


    private fun observeProfile() {
        viewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileState.Loading -> {
                    // Optional: tampilkan loading indicator
                }
                is ProfileState.Success -> {
                    val user = state.profile
                    binding.tvUsername.text = user.username
                    binding.tvLocation.text = user.location
                    Glide.with(this)
                        .load(user.profilePhoto)
                        .into(binding.ivProfile)
                }
                is ProfileState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                            params.topMargin = 16 // Tambahkan margin atas jika diperlukan
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




    override fun onDestroyView() {
        snackbar?.dismiss() // Pastikan Snackbar dihapus
        snackbar = null // Hindari referensi yang tidak diperlukan
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        snackbar?.dismiss() // Dismiss Snackbar saat fragment tidak aktif
        snackbar = null
    }
}
