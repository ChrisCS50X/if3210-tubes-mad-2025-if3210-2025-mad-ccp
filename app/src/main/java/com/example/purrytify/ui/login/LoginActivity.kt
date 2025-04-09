package com.example.purrytify.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.repository.UserRepository
import com.example.purrytify.databinding.ActivityLoginBinding
import com.example.purrytify.ui.main.MainActivity
import com.example.purrytify.ui.main.NetworkViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private lateinit var viewModelFactory: LoginViewModelFactory
    private lateinit var networkViewModel: NetworkViewModel

    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupListeners()
        observeViewModel()
        observeNetworkStatus()
    }

    private fun setupViewModel() {
        val tokenManager = TokenManager(applicationContext)
        val userRepository = UserRepository(tokenManager)
        viewModelFactory = LoginViewModelFactory(userRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[LoginViewModel::class.java]
        networkViewModel = ViewModelProvider(this)[NetworkViewModel::class.java]

        // Check if already logged in
        if (tokenManager.isLoggedIn()) {
            navigateToMain()
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is LoginState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    navigateToMain()
                }
                is LoginState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeNetworkStatus() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                    }
                    // Nonaktifkan tombol login jika tidak terhubung
                    binding.btnLogin.isEnabled = isConnected
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close LoginActivity
    }
}