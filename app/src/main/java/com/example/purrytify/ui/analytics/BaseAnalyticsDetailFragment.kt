package com.example.purrytify.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.local.AppDatabase
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.databinding.FragmentAnalyticsDetailBinding
import com.github.mikephil.charting.charts.Chart

/**
 * Base class for all analytics detail fragments
 */
abstract class BaseAnalyticsDetailFragment : Fragment() {

    protected lateinit var binding: FragmentAnalyticsDetailBinding
    protected lateinit var viewModel: AnalyticsDetailViewModel
    protected var userId: String? = null
    protected var year: Int = 0
    protected var month: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("userId")
            year = it.getInt("year")
            month = it.getInt("month")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAnalyticsDetailBinding.inflate(inflater, container, false)
        
        val appDatabase = AppDatabase.getInstance(requireContext())
        val analyticsRepository = AnalyticsRepository(appDatabase.listeningStatsDao())
        val viewModelFactory = AnalyticsDetailViewModelFactory(analyticsRepository)
        
        viewModel = ViewModelProvider(this, viewModelFactory)[AnalyticsDetailViewModel::class.java]
        
        setupUI()
        loadData()
        
        return binding.root
    }
    
    /**
     * Set up the UI elements specific to each detail screen
     */
    protected abstract fun setupUI()
    
    /**
     * Load data specific to each analytics type
     */
    protected abstract fun loadData()
    
    /**
     * Set up the chart with proper styling
     */
    protected fun setupChartStyle(chart: Chart<*>) {
        chart.description.isEnabled = false
        chart.legend.textColor = requireContext().getColor(android.R.color.white)
        chart.setNoDataText("Loading data...")
        chart.setNoDataTextColor(requireContext().getColor(android.R.color.white))
    }
}
