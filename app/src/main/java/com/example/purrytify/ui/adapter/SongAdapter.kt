package com.example.purrytify.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.ItemSongBinding
import java.util.concurrent.TimeUnit

class SongAdapter : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var songs = emptyList<Song>()
    private var onItemClickListener: ((Song) -> Unit)? = null

    fun setSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (Song) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount() = songs.size

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.invoke(songs[position])
                }
            }
        }

        fun bind(song: Song) {
            binding.textSongTitle.text = song.title
            binding.textArtist.text = song.artist
            binding.textDuration.text = formatDuration(song.duration)

            // Load image if available
            song.coverUrl?.let { url ->
                Glide.with(binding.root)
                    .load(url)
                    .placeholder(R.drawable.placeholder_album)
                    .into(binding.imageSong)
            } ?: run {
                // Set default image if no cover
                binding.imageSong.setImageResource(R.drawable.placeholder_album)
            }
        }

        private fun formatDuration(durationMs: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                    TimeUnit.MINUTES.toSeconds(minutes)
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}