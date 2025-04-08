package com.example.purrytify.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.databinding.ItemNewSongBinding
import com.example.purrytify.data.model.Song

class NewSongsAdapter(private val songs: List<Song>) :
    RecyclerView.Adapter<NewSongsAdapter.NewSongViewHolder>() {

    inner class NewSongViewHolder(private val binding: ItemNewSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            Glide.with(binding.root)
                .load(song.coverUrl)
                .into(binding.ivCover)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewSongViewHolder {
        val binding = ItemNewSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewSongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewSongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size
}
