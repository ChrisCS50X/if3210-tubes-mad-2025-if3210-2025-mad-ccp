package com.example.purrytify.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.ItemQueueSongBinding
import java.util.concurrent.TimeUnit

class QueueAdapter(
    private val context: Context,
    private var songs: List<Song>,
    private val listener: QueueItemListener
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    interface QueueItemListener {
        fun onRemoveFromQueue(position: Int)
    }

    inner class QueueViewHolder(val binding: ItemQueueSongBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnRemove.setOnClickListener {
                val position = bindingAdapterPosition  // Changed from adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRemoveFromQueue(position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val song = songs[position]

        with(holder.binding) {
            textSongTitle.text = song.title
            textArtist.text = song.artist
            textPosition.text = (position + 1).toString() // Tampilkan posisi dalam queue

            // Load cover art jika ada
            if (!song.coverUrl.isNullOrEmpty()) {
                imageSongCover.load(song.coverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_image)
                    error(R.drawable.placeholder_image)
                }
            } else {
                imageSongCover.setImageResource(R.drawable.placeholder_image)
            }
        }
    }

    override fun getItemCount() = songs.size

    // Add this method to fix the error
    fun updateSongs(newSongs: List<Song>) {
        this.songs = newSongs
        notifyDataSetChanged()
    }
}