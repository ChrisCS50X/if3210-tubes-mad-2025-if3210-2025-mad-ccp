package com.example.purrytify.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.DialogShareOptionsBinding
import com.example.purrytify.utils.SharingUtils

/**
 * Dialog fragment that shows sharing options for a song
 */
class ShareOptionsDialog : DialogFragment() {

    private var _binding: DialogShareOptionsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var song: Song

    companion object {
        private const val ARG_SONG = "song"

        fun newInstance(song: Song): ShareOptionsDialog {
            return ShareOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_SONG, song)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogShareOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Get the song from arguments
        @Suppress("DEPRECATION")
        song = arguments?.getParcelable(ARG_SONG) ?: throw IllegalArgumentException("Song must be provided")
        
        // Set song info
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist
        
        // Set click listeners
        binding.btnShareAsLink.setOnClickListener {
            // Share as text link (URL)
            SharingUtils.shareSong(requireContext(), song)
            dismiss()
        }
        
        binding.btnShareAsQR.setOnClickListener {
            // Open QR code dialog
            showQRCodeDialog()
        }
        
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }
    
    private fun showQRCodeDialog() {
        dismiss() // Close this dialog first
        
        val qrCodeDialog = QRCodeDialog.newInstance(song)
        qrCodeDialog.show(parentFragmentManager, "qr_code_dialog")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
