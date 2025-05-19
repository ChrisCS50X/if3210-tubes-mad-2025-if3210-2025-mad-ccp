package com.example.purrytify.ui.dialog

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.purrytify.R
import com.example.purrytify.data.model.Song
import com.example.purrytify.databinding.DialogQrCodeBinding
import com.example.purrytify.utils.QRCodeUtils
import com.example.purrytify.utils.SharingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Dialog fragment to display and share a QR code for a song
 */
class QRCodeDialog : DialogFragment() {

    private var _binding: DialogQrCodeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var song: Song
    private var qrCodeBitmap: Bitmap? = null
    private var qrCodeFileUri: Uri? = null

    companion object {
        private const val ARG_SONG = "song"

        fun newInstance(song: Song): QRCodeDialog {
            return QRCodeDialog().apply {
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
        _binding = DialogQrCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Get the song from arguments
        @Suppress("DEPRECATION")
        song = arguments?.getParcelable(ARG_SONG) ?: throw IllegalArgumentException("Song must be provided")
        
        // Set song info
        binding.tvSongInfo.text = "${song.title} - ${song.artist}"
        
        // Generate QR code
        generateQRCode()
        
        // Set button click listeners
        binding.btnShare.setOnClickListener {
            shareQRCode()
        }
        
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }
    
    private fun generateQRCode() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Create deep link URI
            val deepLinkUri = SharingUtils.createSongDeepLink(song.id)
            
            // Generate QR code with song info
            qrCodeBitmap = QRCodeUtils.generateQRCodeWithInfo(
                deepLinkUri,
                song.title,
                song.artist
            )
            
            // Save QR code to file
            qrCodeBitmap?.let { bitmap ->
                qrCodeFileUri = createShareableImageUri(bitmap)
            }
            
            withContext(Dispatchers.Main) {
                qrCodeBitmap?.let { bitmap ->
                    binding.ivQrCode.setImageBitmap(bitmap)
                } ?: run {
                    Toast.makeText(
                        requireContext(),
                        "Failed to generate QR code",
                        Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                }
            }
        }
    }
    
    private fun shareQRCode() {
        qrCodeFileUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${song.title} - QR Code")
                putExtra(Intent.EXTRA_TEXT, getString(R.string.shared_song_message) + 
                        ": '${song.title}' by ${song.artist}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_qr_code)))
        } ?: run {
            Toast.makeText(
                requireContext(),
                "QR code not ready yet. Please wait.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun createShareableImageUri(bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            
            val file = File(cachePath, "shared_qr_${song.id}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
