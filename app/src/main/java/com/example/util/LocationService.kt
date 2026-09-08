package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Result state representing location capture or error status.
 */
sealed class LocationState {
    object Idle : LocationState()
    object Loading : LocationState()

    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double = 0.0,
        val timestamp: Long = System.currentTimeMillis(),
        val isMock: Boolean = false
    ) : LocationState()

    data class PermissionDenied(
        val message: String = "Location permission denied. UERI offline mesh fallback active."
    ) : LocationState()

    data class GpsDisabled(
        val message: String = "Device GPS is turned off. Using cell/Wi-Fi beacon fallback."
    ) : LocationState()

    data class Error(
        val message: String
    ) : LocationState()
}

/**
 * LocationService leveraging Google Play Services FusedLocationProviderClient.
 * Gracefully handles missing permissions, GPS provider disabled, timeout, and offline fallback.
 */
class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeCallback: LocationCallback? = null

    /**
     * Checks if either FINE or COARSE location permission has been granted.
     */
    fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }

    /**
     * Checks whether location services / GPS providers are enabled on the device.
     */
    fun isGpsProviderEnabled(): Boolean {
        return try {
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            isGpsEnabled || isNetworkEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking location provider state", e)
            false
        }
    }

    /**
     * Captures a single one-shot location using FusedLocationProviderClient with high accuracy.
     * If permissions are not granted, returns [LocationState.PermissionDenied].
     * If GPS is disabled, returns [LocationState.GpsDisabled].
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationState {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted. Returning PermissionDenied state.")
            return LocationState.PermissionDenied()
        }

        if (!isGpsProviderEnabled()) {
            Log.w(TAG, "Location provider is disabled. Returning GpsDisabled state.")
            return LocationState.GpsDisabled()
        }

        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }

            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        continuation.resume(
                            LocationState.Success(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                altitude = location.altitude,
                                timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                                isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    location.isMock
                                } else {
                                    @Suppress("DEPRECATION")
                                    location.isFromMockProvider
                                }
                            )
                        )
                    } else {
                        // Fallback to last known location if getCurrentLocation returned null
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                continuation.resume(
                                    LocationState.Success(
                                        latitude = lastLoc.latitude,
                                        longitude = lastLoc.longitude,
                                        accuracy = lastLoc.accuracy,
                                        altitude = lastLoc.altitude,
                                        timestamp = lastLoc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                                        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            lastLoc.isMock
                                        } else {
                                            @Suppress("DEPRECATION")
                                            lastLoc.isFromMockProvider
                                        }
                                    )
                                )
                            } else {
                                continuation.resume(
                                    LocationState.Error("Location fix unavailable. Signal obstructed.")
                                )
                            }
                        }.addOnFailureListener { ex ->
                            continuation.resume(
                                LocationState.Error(ex.localizedMessage ?: "Failed to get last known location")
                            )
                        }
                    }
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "getCurrentLocation failed", exception)
                    continuation.resume(
                        LocationState.Error(exception.localizedMessage ?: "GPS location request failed")
                    )
                }
            } catch (securityEx: SecurityException) {
                Log.e(TAG, "SecurityException during getCurrentLocation", securityEx)
                continuation.resume(LocationState.PermissionDenied())
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected exception during getCurrentLocation", ex)
                continuation.resume(LocationState.Error(ex.localizedMessage ?: "Unknown location error"))
            }
        }
    }

    /**
     * Provides continuous location updates as a Kotlin Coroutines Flow.
     * Emits [LocationState.PermissionDenied] if permissions are revoked,
     * or [LocationState.GpsDisabled] if GPS is turned off.
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 5000L, minDistanceMeters: Float = 2f): Flow<LocationState> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(LocationState.PermissionDenied())
            close()
            return@callbackFlow
        }

        if (!isGpsProviderEnabled()) {
            trySend(LocationState.GpsDisabled())
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.lastLocation ?: return
                val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    latest.isMock
                } else {
                    @Suppress("DEPRECATION")
                    latest.isFromMockProvider
                }

                trySend(
                    LocationState.Success(
                        latitude = latest.latitude,
                        longitude = latest.longitude,
                        accuracy = latest.accuracy,
                        altitude = latest.altitude,
                        timestamp = latest.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        isMock = isMock
                    )
                )
            }
        }

        activeCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                Log.e(TAG, "requestLocationUpdates failed", e)
                trySend(LocationState.Error(e.localizedMessage ?: "Failed to request location updates"))
            }
        } catch (sec: SecurityException) {
            Log.e(TAG, "SecurityException during requestLocationUpdates", sec)
            trySend(LocationState.PermissionDenied())
        } catch (e: Exception) {
            Log.e(TAG, "Exception in requestLocationUpdates", e)
            trySend(LocationState.Error(e.localizedMessage ?: "Unknown location tracking error"))
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing location updates", e)
            }
        }
    }

    /**
     * Explicitly stops any active location callbacks.
     */
    fun stopLocationUpdates() {
        activeCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping location updates", e)
            }
            activeCallback = null
        }
    }

    companion object {
        private const val TAG = "LocationService"
    }
}
