package com.example.purrytify.ui.addsong

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.DialogAddSongBinding
import com.example.purrytify.ui.library.LibraryViewModel
import com.example.purrytify.ui.library.LibraryViewModelFactory
import java.util.concurrent.TimeUnit

class AddSongDialogFragment : DialogFragment() {

    private var _binding: DialogAddSongBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModelFactory(requireActivity().application, requireContext().applicationContext)
    }

    private var selectedAudioUri: Uri? = null
    private var selectedArtworkUri: Uri? = null
    private var songDuration: Long = 0

    private val audioResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAudioUri = uri
                binding.textSelectedFile.text = getFileName(uri)
                extractMetadata(uri)
            }
        }
    }

    private val imageResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedArtworkUri = uri
                Glide.with(requireContext())
                    .load(uri)
                    .into(binding.imageArtwork)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppTheme_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddSongBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.buttonSelectFile.setOnClickListener {
            selectAudioFile()
        }

        binding.textSelectArtwork.setOnClickListener {
            selectArtwork()
        }

        binding.imageArtwork.setOnClickListener {
            selectArtwork()
        }

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        binding.buttonSave.setOnClickListener {
            saveSong()
        }
    }

    private fun selectAudioFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        audioResultLauncher.launch(intent)
    }

    private fun selectArtwork() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        imageResultLauncher.launch(intent)
    }

    private fun extractMetadata(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            songDuration = durationStr?.toLong() ?: 0
            binding.textDuration.text = "Duration: ${formatDuration(songDuration)}"

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrBlank()) {
                binding.editTitle.setText(title)
            }

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!artist.isNullOrBlank()) {
                binding.editArtist.setText(artist)
            }

            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to extract metadata", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSong() {
        val title = binding.editTitle.text.toString().trim()
        val artist = binding.editArtist.text.toString().trim()

        if (selectedAudioUri == null) {
            Toast.makeText(requireContext(), "Please select an audio file", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isEmpty()) {
            binding.inputLayoutTitle.error = "Title is required"
            return
        } else {
            binding.inputLayoutTitle.error = null
        }

        if (artist.isEmpty()) {
            binding.inputLayoutArtist.error = "Artist is required"
            return
        } else {
            binding.inputLayoutArtist.error = null
        }

        try {
            requireContext().contentResolver.takePersistableUriPermission(
                selectedAudioUri!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            selectedArtworkUri?.let {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Log.w("AddSongDialog", "Couldn't take persistable permission for artwork: ${e.message}")
                }
            }

            val newSong = Song(
                id = 0,
                title = title,
                artist = artist,
                coverUrl = selectedArtworkUri?.toString(),
                filePath = selectedAudioUri.toString(),
                duration = songDuration,
                isLiked = false
            )

            viewModel.addSong(newSong, requireContext().applicationContext)
            Toast.makeText(requireContext(), "Song added successfully", Toast.LENGTH_SHORT).show()
            dismiss()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error saving song: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "Unknown file"
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}