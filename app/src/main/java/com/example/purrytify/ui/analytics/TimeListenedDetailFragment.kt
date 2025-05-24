package com.example.purrytify.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.example.purrytify.databinding.FragmentAnalyticsDetailBinding
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TimeListenedDetailFragment : BaseAnalyticsDetailFragment() {

    private lateinit var barChart: BarChart
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }
    
    override fun setupUI() {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        
        binding.tvAnalyticsTitle.text = "Time Listened"
        binding.tvAnalyticsSubtitle.text = "Daily listening time for $monthName"
        binding.tvDataDescription.text = "This graph shows your daily listening time throughout the month. Each bar represents a day of the month."
        
        // Create and set up the chart programmatically
        barChart = BarChart(requireContext())
        binding.chartContainer.addView(barChart)
        setupChartStyle(barChart)
        
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.WHITE
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
        
        barChart.axisLeft.textColor = Color.WHITE
        barChart.axisRight.isEnabled = false
        barChart.animateY(1000)
    }
    
    override fun loadData() {
        userId?.let {
            viewModel.loadTimeListenedByDay(it, year, month)
        }
    }
    
    private fun setupObservers() {
        viewModel.timeListenedData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                updateChart(data)
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Handle loading state if needed
        }
    }
    
    private fun updateChart(data: List<Pair<Int, Long>>) {
        val entries = data.map { (day, duration) ->
            // Convert duration from milliseconds to minutes for better visualization
            val minutes = duration / (1000 * 60)
            BarEntry(day.toFloat(), minutes.toFloat())
        }
        
        val dataSet = BarDataSet(entries, "Minutes Listened")
        dataSet.color = Color.parseColor("#64B5F6")
        dataSet.valueTextColor = Color.WHITE
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.6f
        
        barChart.data = barData
        barChart.invalidate()
    }
}
