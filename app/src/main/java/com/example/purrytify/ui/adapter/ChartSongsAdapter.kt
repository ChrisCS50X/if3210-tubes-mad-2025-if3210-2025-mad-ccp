package com.example.purrytify.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.model.ChartSong
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.databinding.ItemChartSongBinding
import com.example.purrytify.service.DownloadManager
import kotlinx.coroutines.launch
import android.util.Log

class ChartSongsAdapter(
    private var songs: List<ChartSong>,
    private val onSongClick: (ChartSong) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val songRepository: SongRepository,
    private val downloadManager: DownloadManager
) : RecyclerView.Adapter<ChartSongsAdapter.ChartSongViewHolder>() {

    inner class ChartSongViewHolder(private val binding: ItemChartSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: ChartSong) {
            binding.tvRank.text = "#${song.rank}"
            binding.tvSongTitle.text = song.title
            binding.tvArtist.text = song.artist

            Glide.with(binding.root)
                .load(song.artwork)
                .placeholder(R.drawable.placeholder_album)
                .error(R.drawable.placeholder_album)
                .into(binding.ivSongCover)

            // Handle download button
            val btnDownload = binding.btnDownload
            val convertedSong = song.toSong()

            lifecycleScope.launch {
                try {
                    val isDownloaded = songRepository.isDownloaded(convertedSong.id)

                    btnDownload.setImageResource(
                        if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download
                    )

                    btnDownload.setOnClickListener {
                        if (!isDownloaded) {
                            Toast.makeText(
                                binding.root.context,
                                "Downloading ${song.title}...",
                                Toast.LENGTH_SHORT
                            ).show()
                            downloadManager.enqueueDownload(convertedSong)
                        } else {
                            Toast.makeText(
                                binding.root.context,
                                "Song already downloaded",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChartSongsAdapter", "Error checking download status: ${e.message}")
                }
            }

            binding.root.setOnClickListener {
                onSongClick(song)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartSongViewHolder {
        val binding = ItemChartSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChartSongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChartSongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount() = songs.size

    fun updateSongs(newSongs: List<ChartSong>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}