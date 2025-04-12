package com.example.purrytify.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.ItemRecentlyPlayedBinding
import java.util.concurrent.TimeUnit

class RecentlyPlayedAdapter(
    private val songs: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<RecentlyPlayedAdapter.RecentlyPlayedViewHolder>() {

    inner class RecentlyPlayedViewHolder(private val binding: ItemRecentlyPlayedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.textSongTitle.text = song.title
            binding.textArtist.text = song.artist

            val minutes = TimeUnit.MILLISECONDS.toMinutes(song.duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(song.duration) -
                    TimeUnit.MINUTES.toSeconds(minutes)
            binding.textDuration.text = String.format("%d:%02d", minutes, seconds)

            Glide.with(binding.root)
                .load(song.coverUrl)
                .placeholder(R.drawable.placeholder_album)
                .error(R.drawable.placeholder_album)
                .into(binding.imageSong)

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