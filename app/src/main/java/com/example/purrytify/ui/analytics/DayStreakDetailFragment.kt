package com.example.purrytify.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.databinding.FragmentAnalyticsDetailBinding
import com.example.purrytify.data.local.dao.ListeningStatsDao.SongWithStreak
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DayStreakDetailFragment : BaseAnalyticsDetailFragment() {

    private lateinit var lineChart: LineChart
    private var adapter: AnalyticsDataPointAdapter? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }
    
    override fun setupUI() {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        
        binding.tvAnalyticsTitle.text = "Day Streaks"
        binding.tvAnalyticsSubtitle.text = "Your listening streaks for $monthName"
        binding.tvDataDescription.text = "This chart shows your consistency in listening to the same songs over consecutive days. Higher values indicate longer streaks."
        
        // Create and set up the chart programmatically
        lineChart = LineChart(requireContext())
        binding.chartContainer.addView(lineChart)
        setupChartStyle(lineChart)
        
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.WHITE
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
        
        lineChart.axisLeft.textColor = Color.WHITE
        lineChart.axisRight.isEnabled = false
        lineChart.description.isEnabled = false
        lineChart.legend.textColor = Color.WHITE
        lineChart.animateX(1000)
        
        // Set up the RecyclerView for detailed data
        binding.rvDataPoints.visibility = View.VISIBLE
        binding.rvDataPoints.layoutManager = LinearLayoutManager(requireContext())
        adapter = AnalyticsDataPointAdapter()
        binding.rvDataPoints.adapter = adapter
    }
    
    override fun loadData() {
        userId?.let {
            viewModel.loadStreakData(it, year, month)
        }
    }
    
    private fun setupObservers() {
        viewModel.streakData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                updateChart(data)
                updateDataPointsList(data)
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Handle loading state
        }
    }
    
    private fun updateChart(data: List<SongWithStreak>) {
        // Take top 5 for better visualization
        val topStreaks = data.take(5)
        
        val entries = topStreaks.mapIndexed { index, streak ->
            Entry(index.toFloat(), streak.streak.toFloat())
        }
        
        val dataSet = LineDataSet(entries, "Days in Streak")
        dataSet.color = Color.parseColor("#4CAF50")
        dataSet.setCircleColor(Color.parseColor("#4CAF50"))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.valueTextColor = Color.WHITE
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#4CAF50")
        dataSet.fillAlpha = 50
        
        val lineData = LineData(dataSet)
        
        lineChart.data = lineData
        lineChart.invalidate()
    }
    
    private fun updateDataPointsList(data: List<SongWithStreak>) {
        val dataPoints = data.map { streak ->
            DataPoint(streak.songTitle, "${streak.streak} days")
        }
        
        adapter?.submitList(dataPoints)
    }
}
