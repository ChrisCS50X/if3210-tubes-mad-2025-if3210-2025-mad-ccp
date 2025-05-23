package com.example.purrytify.ui.editprofile

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.purrytify.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.*

class MapSelectionActivity : AppCompatActivity() {
    private lateinit var map: GoogleMap
    private var selectedLatLng: LatLng? = null
    private var selectedCountryCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_selection)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            map = googleMap

            map.setOnMapLongClickListener { latLng ->
                selectedLatLng = latLng
                map.clear()

                // Get country code from coordinates
                getCountryCodeFromLocation(latLng.latitude, latLng.longitude) { countryCode ->
                    selectedCountryCode = countryCode
                    val markerTitle = if (countryCode != null) {
                        "Selected Location ($countryCode)"
                    } else {
                        "Selected Location"
                    }
                    map.addMarker(MarkerOptions().position(latLng).title(markerTitle))
                }
            }
        }

        findViewById<Button>(R.id.btnConfirmLocation).setOnClickListener {
            handleLocationSelection()
        }
    }

    private fun getCountryCodeFromLocation(latitude: Double, longitude: Double, callback: (String?) -> Unit) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())

            // Use geocoder in a background thread to avoid blocking UI
            Thread {
                try {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val countryCode = addresses?.firstOrNull()?.countryCode

                    // Switch back to main thread for UI updates
                    runOnUiThread {
                        callback(countryCode)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        callback(null)
                        Toast.makeText(this@MapSelectionActivity, "Error getting country code", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()

        } catch (e: Exception) {
            callback(null)
            Toast.makeText(this, "Error getting country code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLocationSelection() {
        selectedLatLng?.let { latLng ->
            val countryCode = selectedCountryCode

            if (countryCode != null) {
                val resultIntent = Intent().apply {
                    putExtra("LATITUDE", latLng.latitude)
                    putExtra("LONGITUDE", latLng.longitude)
                    putExtra("COUNTRY_CODE", countryCode)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Unable to determine country. Please try selecting another location.", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
    }
}