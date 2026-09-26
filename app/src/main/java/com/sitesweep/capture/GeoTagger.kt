package com.sitesweep.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
    val locationKey: String
)

/**
 * Non-blocking geotagger using cached last-known-location from FusedLocationProviderClient.
 * As mandated by AGENTS.md, geotagging NEVER blocks camera or capture execution.
 * When GPS is cold or in airplane mode, it uses the cached location or demo venue baseline.
 */
open class GeoTagger(private val context: Context? = null) {

    private val fusedLocationClient: FusedLocationProviderClient? by lazy {
        context?.let { LocationServices.getFusedLocationProviderClient(it) }
    }

    // Default baseline coordinates for iQOO Hackathon venue (Hyderabad: 17.44858, 78.37582)
    companion object {
        const val DEFAULT_LAT = 17.44858
        const val DEFAULT_LNG = 78.37582
        val DEFAULT_LOCATION_KEY = Geohash.encode(DEFAULT_LAT, DEFAULT_LNG, precision = 8)
    }

    @Volatile
    private var cachedCoordinates: GeoCoordinates = GeoCoordinates(
        latitude = DEFAULT_LAT,
        longitude = DEFAULT_LNG,
        locationKey = DEFAULT_LOCATION_KEY
    )

    init {
        if (context != null) {
            refreshCachedLocationAsync()
        }
    }

    /**
     * Immediately returns the current cached location and locationKey without blocking.
     */
    open fun getCachedLocation(): GeoCoordinates {
        if (context != null) {
            refreshCachedLocationAsync()
        }
        return cachedCoordinates
    }

    /**
     * Checks FusedLocationProviderClient last-known-location in background to update cache.
     */
    @SuppressLint("MissingPermission")
    fun refreshCachedLocationAsync() {
        val ctx = context ?: return
        val client = fusedLocationClient ?: return

        val hasFine = ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return
        }

        try {
            client.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val key = Geohash.encode(location.latitude, location.longitude, precision = 8)
                    cachedCoordinates = GeoCoordinates(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        locationKey = key
                    )
                }
            }.addOnFailureListener { e ->
                Log.w("GeoTagger", "Failed to retrieve last known location: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w("GeoTagger", "Error querying fused location: ${e.message}")
        }
    }

    /**
     * Allows forcing a specific location (used by demo seeder or simulation).
     */
    fun setManualCoordinates(latitude: Double, longitude: Double) {
        val key = Geohash.encode(latitude, longitude, precision = 8)
        cachedCoordinates = GeoCoordinates(latitude, longitude, key)
    }
}
