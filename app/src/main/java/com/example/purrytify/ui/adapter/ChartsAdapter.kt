package com.example.purrytify.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.databinding.ItemChartBinding

data class ChartItem(
    val id: String,
    val title: String,
    val imageResId: Int,
    val type: String
)

class ChartsAdapter(
    private val charts: List<ChartItem>,
    private val onChartClick: (ChartItem) -> Unit
) : RecyclerView.Adapter<ChartsAdapter.ChartViewHolder>() {

    inner class ChartViewHolder(private val binding: ItemChartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chart: ChartItem) {
            binding.tvChartTitle.text = chart.title
            Glide.with(binding.root)
                .load(chart.imageResId)
                .into(binding.ivChartCover)

            binding.root.setOnClickListener {
                onChartClick(chart)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val binding = ItemChartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        holder.bind(charts[position])
    }

    override fun getItemCount() = charts.size
}