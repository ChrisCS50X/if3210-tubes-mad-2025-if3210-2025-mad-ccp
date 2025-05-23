package com.example.purrytify.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.databinding.FragmentAnalyticsDetailBinding
import com.example.purrytify.data.local.dao.ListeningStatsDao.SongWithDuration
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TopSongDetailFragment : BaseAnalyticsDetailFragment() {

    private lateinit var barChart: HorizontalBarChart
    private var adapter: AnalyticsDataPointAdapter? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }
    
    override fun setupUI() {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        
        binding.tvAnalyticsTitle.text = "Top Songs"
        binding.tvAnalyticsSubtitle.text = "Your most played songs for $monthName"
        binding.tvDataDescription.text = "This chart shows your top songs based on listening time. The longer the bar, the more time you've spent listening to that song."
        
        // Create and set up the chart programmatically
        barChart = HorizontalBarChart(requireContext())
        binding.chartContainer.addView(barChart)
        setupChartStyle(barChart)
        
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawLabels(false)
        xAxis.textColor = Color.WHITE
        
        barChart.axisLeft.textColor = Color.WHITE
        barChart.axisRight.isEnabled = false
        barChart.legend.textColor = Color.WHITE
        barChart.axisLeft.setDrawGridLines(false)
        barChart.animateY(1000)
        
        // Set up the RecyclerView for detailed data
        binding.rvDataPoints.visibility = View.VISIBLE
        binding.rvDataPoints.layoutManager = LinearLayoutManager(requireContext())
        adapter = AnalyticsDataPointAdapter()
        binding.rvDataPoints.adapter = adapter
    }
    
    override fun loadData() {
        userId?.let {
            viewModel.loadTopSongs(it, year, month)
        }
    }
    
    private fun setupObservers() {
        viewModel.topSongsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                updateChart(data)
                updateDataPointsList(data)
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Handle loading state
        }
    }
    
    private fun updateChart(data: List<SongWithDuration>) {
        // Take top 5 for better visualization
        val topSongs = data.take(5)
        
        val entries = topSongs.mapIndexed { index, song ->
            // Convert duration from milliseconds to minutes
            val minutes = song.total / (1000 * 60)
            BarEntry(index.toFloat(), minutes.toFloat())
        }
        
        val dataSet = BarDataSet(entries, "Minutes Listened")
        dataSet.color = Color.parseColor("#FF9800")
        dataSet.valueTextColor = Color.WHITE
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.6f
        
        barChart.data = barData
        barChart.invalidate()
    }
    
    private fun updateDataPointsList(data: List<SongWithDuration>) {
        val dataPoints = data.map { song ->
            val durationMinutes = song.total / (1000 * 60)
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            
            val formattedDuration = if (hours > 0) {
                "$hours h $minutes min"
            } else {
                "$minutes min"
            }
            
            DataPoint(song.songTitle, formattedDuration)
        }
        
        adapter?.submitList(dataPoints)
    }
}
