package com.example.purrytify.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.databinding.ItemRecentlyPlayedBinding
import com.example.purrytify.data.model.Song

class RecentlyPlayedAdapter(
    private val songs: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<RecentlyPlayedAdapter.RecentlyPlayedViewHolder>() {

    inner class RecentlyPlayedViewHolder(private val binding: ItemRecentlyPlayedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            Glide.with(binding.root)
                .load(song.coverUrl)
                .into(binding.ivCover)

            binding.root.setOnClickListener {
                onItemClick(song)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentlyPlayedViewHolder {
        val binding = ItemRecentlyPlayedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentlyPlayedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentlyPlayedViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size
}
