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
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.example.purrytify.utils.findFragment
import androidx.fragment.app.Fragment

class ChartSongsAdapter(
    private var songs: List<ChartSong>,
    private val onSongClick: (ChartSong) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val songRepository: SongRepository,
    private val downloadManager: DownloadManager
) : RecyclerView.Adapter<ChartSongsAdapter.ChartSongViewHolder>() {

    private val observers = mutableMapOf<Int, Observer<WorkInfo>>()

    inner class ChartSongViewHolder(private val binding: ItemChartSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: ChartSong, position: Int) {
            binding.tvRank.text = song.rank.toString()
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
                    val isDownloadedById = songRepository.isDownloaded(convertedSong.id)
                    val isDuplicate = songRepository.isSongAlreadyDownloaded(song.title, song.artist)

                    val isDownloaded = isDownloadedById || isDuplicate

                    btnDownload.setImageResource(
                        if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download
                    )

                    btnDownload.setOnClickListener {
                        if (!isDownloaded && !downloadManager.isDownloading(convertedSong.id)) {
                            Toast.makeText(
                                binding.root.context,
                                "Downloading ${song.title}...",
                                Toast.LENGTH_SHORT
                            ).show()

                            val downloadId = downloadManager.enqueueDownload(convertedSong)

                            // Create and store an observer for this download
                            observers[position]?.let { oldObserver ->
                                downloadManager.getDownloadStatusBySongId(convertedSong.id)
                                    ?.removeObserver(oldObserver)
                            }

                            val observer = Observer<WorkInfo> { workInfo ->
                                when (workInfo.state) {
                                    WorkInfo.State.SUCCEEDED -> {
                                        btnDownload.setImageResource(R.drawable.ic_download_done)
                                        downloadManager.clearDownload(convertedSong.id)
                                        observers.remove(position)
                                    }
                                    WorkInfo.State.RUNNING -> {
                                        // Show a "downloading" icon if you have one
                                        btnDownload.isEnabled = false
                                    }
                                    WorkInfo.State.FAILED -> {
                                        btnDownload.setImageResource(R.drawable.ic_download)
                                        btnDownload.isEnabled = true
                                        downloadManager.clearDownload(convertedSong.id)
                                        observers.remove(position)
                                    }
                                    else -> {}
                                }
                            }

                            observers[position] = observer

                            // Start observing the download status
                            downloadManager.getDownloadStatusBySongId(convertedSong.id)
                                ?.observe(binding.root.findFragment<Fragment>().viewLifecycleOwner, observer)
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
        holder.bind(songs[position], position)
    }

    override fun getItemCount() = songs.size

    fun updateSongs(newSongs: List<ChartSong>) {
        songs = newSongs
        // Clear any existing observers when the dataset changes
        observers.clear()
        notifyDataSetChanged()
    }

    // Clean up observers when adapter is detached
    fun clearObservers() {
        observers.clear()
    }

    fun refreshDownloadStates() {
        Log.d("ChartSongsAdapter", "Refreshing download states for all songs")
        notifyDataSetChanged()
    }
}