package com.example.purrytify.ui.qrscan

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.purrytify.databinding.FragmentQrScannerBinding
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.google.zxing.ResultPoint
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream

class QrScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var barcodeView: DecoratedBarcodeView
    private var isScanning = true

    companion object {
        private const val PURRYTIFY_PREFIX = "purrytify://"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1002
    }

    // Activity Result Launcher untuk memilih gambar dari galeri
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri ->
            processImageFromGallery(selectedImageUri)
        }
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            if (!isScanning) return

            val qrCode = result.text
            if (qrCode.startsWith(PURRYTIFY_PREFIX)) {
                isScanning = false

                binding.tvInstructions.text = "Valid Purrytify code detected!"
                handleValidQrCode(qrCode)
            } else {
                binding.tvInstructions.text = "Invalid QR code. Please scan a valid Purrytify code."

                binding.root.postDelayed({
                    if (isScanning) {
                        binding.tvInstructions.text = "Point your camera at a Purrytify code."
                    }
                }, 2000)
            }
        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barcodeView = binding.barcodeScanner

        setupUI()
        checkAndRequestPermissions()
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectFromPhotos.setOnClickListener {
            checkAndLaunchImagePicker()
        }

        binding.tvInstructions.text = "Point your camera at a Purrytify code."
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        barcodeView.decodeContinuous(callback)
        barcodeView.resume()
    }

    private fun checkAndLaunchImagePicker() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Tidak perlu izin untuk API 29 ke atas
            selectImageLauncher.launch("image/*")
        } else {
            // Perlu meminta izin untuk API di bawah 29
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                selectImageLauncher.launch("image/*")
            } else {
                requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun processImageFromGallery(imageUri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val qrCodeText = decodeQRCodeFromBitmap(bitmap)

            if (qrCodeText != null) {
                if (qrCodeText.startsWith(PURRYTIFY_PREFIX)) {
                    isScanning = false
                    binding.tvInstructions.text = "Valid Purrytify code detected from image!"
                    handleValidQrCode(qrCodeText)
                } else {
                    binding.tvInstructions.text = "Invalid QR code found in image."
                    Toast.makeText(requireContext(), "Invalid Purrytify code in selected image", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.tvInstructions.text = "No QR code found in selected image."
                Toast.makeText(requireContext(), "No QR code found in selected image", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error processing selected image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeQRCodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)

            result.text
        } catch (e: Exception) {
            null
        }
    }

    private fun handleValidQrCode(qrCode: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(qrCode)
            }

            // Periksa apakah ada aplikasi yang dapat menangani intent ini
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "No application available to open this link", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error opening the link", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startScanning()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Camera permission is required to scan QR codes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isScanning = true
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        isScanning = false
        barcodeView.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
