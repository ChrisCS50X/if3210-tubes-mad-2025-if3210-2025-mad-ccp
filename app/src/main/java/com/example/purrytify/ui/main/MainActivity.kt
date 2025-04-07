package com.example.purrytify.ui.main

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupNetworkSensing()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop monitoring network when the activity is destroyed
        networkMonitor.stopMonitoring()
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun setupNetworkSensing() {
        // Initialize the network monitor
        networkMonitor = NetworkMonitor(applicationContext)
        networkMonitor.isConnected.observe(this) { isConnected ->
            handleNetworkChange(isConnected)
        }
    }

    private fun handleNetworkChange(isConnected: Boolean) {
        if (!isConnected) {
            Toast.makeText(
                this,
                "No Internet Connection. Some features may not work.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Internet Connected", Toast.LENGTH_SHORT).show()
        }
    }
}
