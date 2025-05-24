package com.example.purrytify.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.databinding.FragmentAnalyticsDetailBinding
import com.example.purrytify.data.local.dao.ListeningStatsDao.ArtistWithDuration
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TopArtistDetailFragment : BaseAnalyticsDetailFragment() {

    private lateinit var pieChart: PieChart
    private var adapter: AnalyticsDataPointAdapter? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }
    
    override fun setupUI() {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        
        binding.tvAnalyticsTitle.text = "Top Artists"
        binding.tvAnalyticsSubtitle.text = "Your most listened artists for $monthName"
        binding.tvDataDescription.text = "This chart shows your top artists based on listening time. The bigger the slice, the more time you've spent listening to that artist."
        
        // Create and set up the chart programmatically
        pieChart = PieChart(requireContext())
        binding.chartContainer.addView(pieChart)
        setupChartStyle(pieChart)
        
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.parseColor("#222222"))
        pieChart.setTransparentCircleColor(Color.parseColor("#222222"))
        pieChart.setTransparentCircleAlpha(110)
        pieChart.holeRadius = 58f
        pieChart.transparentCircleRadius = 61f
        pieChart.setDrawCenterText(true)
        pieChart.centerText = "Top Artists"
        pieChart.setCenterTextColor(Color.WHITE)
        pieChart.setCenterTextSize(16f)
        pieChart.setUsePercentValues(true)
        
        // Set up the RecyclerView for detailed data
        binding.rvDataPoints.visibility = View.VISIBLE
        binding.rvDataPoints.layoutManager = LinearLayoutManager(requireContext())
        adapter = AnalyticsDataPointAdapter()
        binding.rvDataPoints.adapter = adapter
    }
    
    override fun loadData() {
        userId?.let {
            viewModel.loadTopArtists(it, year, month)
        }
    }
    
    private fun setupObservers() {
        viewModel.topArtistsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                updateChart(data)
                updateDataPointsList(data)
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Handle loading state
        }
    }
    
    private fun updateChart(data: List<ArtistWithDuration>) {
        val totalDuration = data.sumOf { it.total }
        
        // Take top 5 for chart, to avoid overcrowding
        val topEntries = data.take(5).map { artist ->
            val percentage = artist.total.toFloat() / totalDuration.toFloat() * 100f
            PieEntry(percentage, artist.artist)
        }
        
        val dataSet = PieDataSet(topEntries, "Artists")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter(pieChart))
        pieData.setValueTextSize(12f)
        pieData.setValueTextColor(Color.WHITE)
        
        pieChart.data = pieData
        pieChart.invalidate()
    }
    
    private fun updateDataPointsList(data: List<ArtistWithDuration>) {
        val dataPoints = data.map { artist ->
            val durationMinutes = artist.total / (1000 * 60)
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            
            val formattedDuration = if (hours > 0) {
                "$hours h $minutes min"
            } else {
                "$minutes min"
            }
            
            DataPoint(artist.artist, formattedDuration)
        }
        
        adapter?.submitList(dataPoints)
    }
}
