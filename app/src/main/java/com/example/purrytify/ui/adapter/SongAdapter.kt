package com.example.purrytify.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.ItemSongBinding
import com.example.purrytify.utils.SharingUtils
import java.util.concurrent.TimeUnit

class SongAdapter(
    private val context: Context,
    private val onAddToQueueListener: (Song) -> Unit,
    private val onEditListener: (Song) -> Unit,
    private val onDeleteListener: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

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
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.invoke(songs[position])
                }
            }

            binding.imageMenu.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showPopupMenu(view, songs[position])
                }
            }
        }

        private fun showPopupMenu(view: View, song: Song) {
            val popup = PopupMenu(context, view)
            popup.menuInflater.inflate(R.menu.menu_song_item, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_add_to_queue -> {
                        onAddToQueueListener(song)
                        true
                    }
                    R.id.action_share -> {
                        if (SharingUtils.canShareSong(song)) {
                            // Show share options dialog
                            SharingUtils.showShareOptions((context as androidx.fragment.app.FragmentActivity).supportFragmentManager, song)
                        } else {
                            Toast.makeText(context, "Only server songs can be shared", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.action_edit -> {
                        onEditListener(song)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteListener(song)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

        fun bind(song: Song) {
            binding.textSongTitle.text = song.title
            binding.textArtist.text = song.artist
            binding.textDuration.text = formatDuration(song.duration)

            song.coverUrl?.let { url ->
                Glide.with(binding.root)
                    .load(url)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(binding.imageSong)
            } ?: run {
                binding.imageSong.setImageResource(R.drawable.placeholder_image)
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