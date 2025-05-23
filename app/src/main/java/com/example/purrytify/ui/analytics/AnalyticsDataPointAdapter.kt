package com.example.purrytify.ui.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.purrytify.databinding.ItemAnalyticsDataPointBinding

/**
 * Data class representing an analytics data point
 */
data class DataPoint(
    val label: String,
    val value: String
)

/**
 * Adapter for the analytics data points list
 */
class AnalyticsDataPointAdapter : ListAdapter<DataPoint, AnalyticsDataPointAdapter.ViewHolder>(DataPointDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnalyticsDataPointBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemAnalyticsDataPointBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dataPoint: DataPoint) {
            binding.tvDataLabel.text = dataPoint.label
            binding.tvDataValue.text = dataPoint.value
        }
    }

    class DataPointDiffCallback : DiffUtil.ItemCallback<DataPoint>() {
        override fun areItemsTheSame(oldItem: DataPoint, newItem: DataPoint): Boolean {
            return oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: DataPoint, newItem: DataPoint): Boolean {
            return oldItem == newItem
        }
    }
}
