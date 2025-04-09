package com.example.purrytify.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.ui.adapter.NewSongsAdapter
import com.example.purrytify.ui.adapter.RecentlyPlayedAdapter
import com.example.purrytify.data.model.Song

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val newSongs = listOf(
        Song(
            id = 1L,
            title = "Sunflower",
            artist = "Post Malone",
            coverUrl = "https://static.wikia.nocookie.net/postmalone/images/9/9e/Spider-Man_Into_the_Spider-Verse_Soundtrack_Cover.jpg/revision/latest?cb=20240630201211",
            filePath = "/storage/emulated/0/Music/sunflower.mp3",
            duration = 158000L,
            isLiked = true
        ),
        Song(
            id = 2L,
            title = "Levitating",
            artist = "Dua Lipa",
            coverUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTnDtiQ6QJ68UMZ39fb53dve3lFQOt03xsM6A&s",
            filePath = "/storage/emulated/0/Music/levitating.mp3",
            duration = 203000L
        ),
        Song(
            id = 3L,
            title = "Blinding Lights",
            artist = "The Weeknd",
            coverUrl = "https://i1.sndcdn.com/artworks-mdf2zXwXczSQlabz-COJvKg-t500x500.jpg",
            filePath = "/storage/emulated/0/Music/blinding_lights.mp3",
            duration = 200000L
        )
    )

    private val recentlyPlayed = listOf(
        Song(
            id = 4L,
            title = "Someone Like You",
            artist = "Adele",
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/7/7a/Adele_-_Someone_Like_You.png",
            filePath = "/storage/emulated/0/Music/someone_like_you.mp3",
            duration = 285000L
        ),
        Song(
            id = 5L,
            title = "Peaches",
            artist = "Justin Bieber",
            coverUrl = "https://i1.sndcdn.com/artworks-isHhIG7zKadDoJ9l-dt3kvQ-t500x500.jpg",
            filePath = "/storage/emulated/0/Music/peaches.mp3",
            duration = 198000L
        ),
        Song(
            id = 6L,
            title = "Shape of You",
            artist = "Ed Sheeran",
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/b/b4/Shape_Of_You_%28Official_Single_Cover%29_by_Ed_Sheeran.png",
            filePath = "/storage/emulated/0/Music/shape_of_you.mp3",
            duration = 233000L,
            isLiked = true
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvNewSongs.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = NewSongsAdapter(newSongs)
        }

        binding.rvRecentlyPlayed.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = RecentlyPlayedAdapter(recentlyPlayed) { song ->
                Toast.makeText(requireContext(), "Now playing: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
