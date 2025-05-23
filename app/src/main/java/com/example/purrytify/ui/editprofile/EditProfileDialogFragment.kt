package com.example.purrytify.ui.editprofile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.data.local.TokenManager
import com.example.purrytify.data.model.EditProfile
import com.example.purrytify.data.repository.UserRepository
import com.example.purrytify.databinding.DialogEditProfileBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.io.FileOutputStream
import java.util.*

class EditProfileDialogFragment : DialogFragment() {

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: EditProfileViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedPhotoFile: File? = null
    private var currentLocation: String? = null
    private var photoUri: Uri? = null

    var onProfileUpdated: () -> Unit = {}

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            attributes = params
        }

        val tokenManager = TokenManager(requireContext().applicationContext)
        val userRepository = UserRepository(tokenManager)
        viewModel = ViewModelProvider(
            this,
            EditProfileViewModelFactory(userRepository)
        )[EditProfileViewModel::class.java]

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val currentUser = arguments?.getParcelable<EditProfile>("currentUser")
        currentUser?.let { user ->
            currentLocation = user.location
            binding.etLocation.setText(user.location)

            Glide.with(requireContext())
                .load("http://34.101.226.132:3000/uploads/profile-picture/${user.profilePhoto}")
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .into(binding.ivEditProfile)
        }

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.tvSelectPhoto.setOnClickListener {
            showPhotoSelectionOptions()
        }

        binding.btnGetLocation.setOnClickListener {
            showLocationOptions()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            saveProfile()
        }

        // Make location field non-editable (read-only)
        binding.etLocation.isFocusable = false
        binding.etLocation.isClickable = false
        binding.etLocation.isCursorVisible = false
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        viewModel.updateState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UpdateProfileState.Loading -> {
                    binding.btnSave.isEnabled = false
                    binding.btnSave.text = "Saving..."
                }
                is UpdateProfileState.Success -> {
                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    onProfileUpdated()
                    dismiss()
                }
                is UpdateProfileState.Error -> {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "SAVE"
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPhotoSelectionOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun showLocationOptions() {
        val options = arrayOf("Use Current Location", "Choose on Map")
        AlertDialog.Builder(requireContext())
            .setTitle("Set Location")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkLocationPermissionAndFetch()
                    1 -> openMapForLocationSelection()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            openCamera()
        }
    }

    private fun checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            fetchCurrentLocation()
        }
    }

    private fun openCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Profile Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
        }
        photoUri = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        takePictureLauncher.launch(cameraIntent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun openMapForLocationSelection() {
        val intent = Intent(requireContext(), MapSelectionActivity::class.java)
        mapLocationLauncher.launch(intent)
    }

    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    getAddressFromLocation(it.latitude, it.longitude)
                } ?: run {
                    Toast.makeText(requireContext(), "Unable to get location. Please try again or enter manually.", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Error getting location. Please enter manually.", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val locationName = addresses?.get(0)?.getAddressLine(0) ?: "Unknown Location"

            currentLocation = locationName
            binding.etLocation.setText(locationName)

            Toast.makeText(requireContext(), "Location set to: $locationName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error getting location. Try again.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveProfile() {
        // Get the current text from location field
        currentLocation = binding.etLocation.text.toString().trim()

        if (currentLocation.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please enter a location", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updateProfile(currentLocation, selectedPhotoFile)
    }

    private fun processSelectedImage(uri: Uri) {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")

        inputStream?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        selectedPhotoFile = file

        Glide.with(requireContext())
            .load(uri)
            .into(binding.ivEditProfile)
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied. You can still use Google Maps to select location.", Toast.LENGTH_LONG).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoUri?.let { uri ->
                processSelectedImage(uri)
            }
        }
    }

    private val mapLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val countryCode = result.data?.getStringExtra("COUNTRY_CODE")

            countryCode?.let { code ->
                currentLocation = code
                binding.etLocation.setText(code)
                Toast.makeText(requireContext(), "Location set to: $code", Toast.LENGTH_SHORT).show()
            } ?: run {
                // Fallback to coordinates if country code not available
                val latitude = result.data?.getDoubleExtra("LATITUDE", 0.0)
                val longitude = result.data?.getDoubleExtra("LONGITUDE", 0.0)
                latitude?.let { lat ->
                    longitude?.let { lon ->
                        getCountryCodeFromLocation(lat, lon)
                    }
                }
            }
        }
    }

    private fun getCountryCodeFromLocation(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())

            Thread {
                try {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val countryCode = addresses?.firstOrNull()?.countryCode ?: "Unknown"

                    requireActivity().runOnUiThread {
                        currentLocation = countryCode
                        binding.etLocation.setText(countryCode)
                        Toast.makeText(requireContext(), "Location set to: $countryCode", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error getting country code. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error getting country code. Try again.", Toast.LENGTH_SHORT).show()
        }
    }


    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                processSelectedImage(uri)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(currentUser: EditProfile): EditProfileDialogFragment {
            val args = Bundle().apply {
                putParcelable("currentUser", currentUser)
            }
            return EditProfileDialogFragment().apply {
                arguments = args
            }
        }
    }
}