package com.phototimestamp.pro.util

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.view.OrientationEventListener
import com.google.android.gms.location.*
import com.phototimestamp.pro.model.GpsLocationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class LocationAndSensorTracker(private val context: Context) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _gpsState = MutableStateFlow(GpsLocationState())
    val gpsState: StateFlow<GpsLocationState> = _gpsState.asStateFlow()

    // Physical device orientation in degrees: 0 (Portrait), 90 (Landscape Left), 180 (Upside Down), 270 (Landscape Right)
    private val _deviceOrientationDegrees = MutableStateFlow(0)
    val deviceOrientationDegrees: StateFlow<Int> = _deviceOrientationDegrees.asStateFlow()

    // Device roll angle for camera level / waterpass indicator
    private val _rollAngleDegrees = MutableStateFlow(0f)
    val rollAngleDegrees: StateFlow<Float> = _rollAngleDegrees.asStateFlow()

    private var lastGeocodedLat = 0.0
    private var lastGeocodedLon = 0.0
    private var lastGeocodeTimeMillis = 0L
    private var isLocationTracking = false

    private val orientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val current = _deviceOrientationDegrees.value
            // Use 20-degree hysteresis around 45/135/225/315 boundaries so rotation never flickers
            val next = when {
                orientation >= 325 || orientation < 35 -> 0
                orientation in 55..125 -> 90   // Clockwise tilt (top of phone on right)
                orientation in 145..215 -> 180
                orientation in 235..305 -> 270 // Counter-clockwise tilt (top of phone on left)
                else -> current
            }
            if (next != current) {
                _deviceOrientationDegrees.value = next
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc)
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(hasLocationPermission: Boolean) {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        if (hasLocationPermission && !isLocationTracking) {
            isLocationTracking = true
            runCatching {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) handleNewLocation(loc)
                }
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                    .setMinUpdateIntervalMillis(1200L)
                    .setMinUpdateDistanceMeters(1f)
                    .build()
                fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            }
        }
    }

    fun stopTracking() {
        orientationListener.disable()
        sensorManager.unregisterListener(this)
        if (isLocationTracking) {
            runCatching { fusedClient.removeLocationUpdates(locationCallback) }
            isLocationTracking = false
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshLocationNow(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) return
        runCatching {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) handleNewLocation(loc, forceGeocode = true)
                }
        }
    }

    private fun handleNewLocation(loc: Location, forceGeocode: Boolean = false) {
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val current = _gpsState.value
        _gpsState.value = current.copy(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = if (loc.hasAltitude()) loc.altitude else current.altitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else current.accuracyMeters,
            speedKmh = speedKmh,
            bearingAzimuth = if (loc.hasBearing() && loc.bearing != 0f) loc.bearing else current.bearingAzimuth,
            hasRealFix = true,
            lastUpdatedMillis = System.currentTimeMillis()
        )

        val now = System.currentTimeMillis()
        val movedEnough = abs(loc.latitude - lastGeocodedLat) > 0.00025 ||
            abs(loc.longitude - lastGeocodedLon) > 0.00025
        if (forceGeocode || movedEnough || (now - lastGeocodeTimeMillis > 20_000L)) {
            lastGeocodedLat = loc.latitude
            lastGeocodedLon = loc.longitude
            lastGeocodeTimeMillis = now
            reverseGeocodeAsync(loc.latitude, loc.longitude)
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeAsync(lat: Double, lon: Double) {
        scope.launch {
            runCatching {
                if (!Geocoder.isPresent()) return@launch
                val geocoder = Geocoder(context, Locale("id", "ID"))
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val addr = addresses?.firstOrNull() ?: return@launch

                val thoroughfare = addr.thoroughfare ?: addr.featureName ?: ""
                val subLocality = addr.subLocality ?: ""
                val street = listOf(thoroughfare, subLocality)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { addr.getAddressLine(0)?.substringBefore(",") ?: "Lokasi Terdeteksi" }

                val locality = addr.subAdminArea ?: addr.locality ?: ""
                val province = addr.adminArea ?: ""
                val cityLine = listOf(locality, province)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")

                val postal = addr.postalCode ?: ""

                _gpsState.value = _gpsState.value.copy(
                    streetAddress = street,
                    districtAndCity = cityLine.ifBlank { _gpsState.value.districtAndCity },
                    postalCode = postal
                )
            }
        }
    }

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val rollRad = orientationAngles[2]
            val azimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f)
            val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()

            val cur = _gpsState.value
            if (abs(azimuthDeg - cur.bearingAzimuth) > 2.5f) {
                _gpsState.value = cur.copy(bearingAzimuth = azimuthDeg)
            }
            if (abs(rollDeg - _rollAngleDegrees.value) > 0.8f) {
                _rollAngleDegrees.value = rollDeg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
