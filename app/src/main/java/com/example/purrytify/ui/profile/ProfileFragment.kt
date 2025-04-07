package com.example.purrytify.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.databinding.FragmentProfileBinding
import com.example.purrytify.data.repository.UserRepository
import android.util.Log


class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel

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
//        val tokenManager = TokenManager.getInstance(requireContext().applicationContext)
//        Log.d("Token", "Token get: ${tokenManager.getToken()}")
//        val userRepository = UserRepository.getInstance(tokenManager)
//        val factory = ProfileViewModelFactory(userRepository)
//
//        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        observeProfile()
        viewModel.loadUserProfile()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
