package com.ehan.speedometer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

data class SpeedoState(
    val tracking: Boolean = false,
    val hasFix: Boolean = false,
    val speedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val distanceM: Float = 0f,
    val movingTimeMs: Long = 0L,
    val accuracyM: Float? = null,
    val useMph: Boolean = false,
) {
    val avgSpeedMps: Float
        get() = if (movingTimeMs > 0) distanceM / (movingTimeMs / 1000f) else 0f
}

class SpeedometerViewModel(app: Application) : AndroidViewModel(app) {

    private val client = LocationServices.getFusedLocationProviderClient(app)

    private val _state = MutableStateFlow(SpeedoState())
    val state: StateFlow<SpeedoState> = _state.asStateFlow()

    private var lastLocation: Location? = null
    private var updating = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    fun startTracking() {
        _state.update { it.copy(tracking = true) }
        requestUpdates()
    }

    fun stopTracking() {
        removeUpdates()
        _state.update { it.copy(tracking = false, speedMps = 0f) }
    }

    /** Dipanggil saat app ke background: hentikan GPS tapi pertahankan niat "tracking". */
    fun pauseUpdates() {
        removeUpdates()
        _state.update { it.copy(speedMps = 0f) }
    }

    /** Dipanggil saat app kembali ke foreground. */
    fun resumeUpdates() {
        if (_state.value.tracking) requestUpdates()
    }

    fun reset() {
        lastLocation = null
        _state.update {
            it.copy(speedMps = 0f, maxSpeedMps = 0f, distanceM = 0f, movingTimeMs = 0L)
        }
    }

    fun setUseMph(useMph: Boolean) {
        _state.update { it.copy(useMph = useMph) }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (updating || !hasFineLocation()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        updating = true
    }

    private fun removeUpdates() {
        if (!updating) return
        client.removeLocationUpdates(callback)
        updating = false
        lastLocation = null
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun onLocation(loc: Location) {
        // Abaikan noise GPS saat diam
        val speed = if (loc.hasSpeed() && loc.speed >= MIN_SPEED_MPS) loc.speed else 0f

        val prev = lastLocation
        var deltaDistance = 0f
        var deltaTime = 0L
        if (prev != null) {
            deltaTime = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000L
            if (speed > 0f && loc.accuracy <= MAX_ACCURACY_M) {
                deltaDistance = prev.distanceTo(loc)
            }
        }
        lastLocation = loc

        _state.update { s ->
            s.copy(
                hasFix = true,
                speedMps = speed,
                maxSpeedMps = max(s.maxSpeedMps, speed),
                distanceM = s.distanceM + deltaDistance,
                movingTimeMs = s.movingTimeMs + if (speed > 0f) deltaTime else 0L,
                accuracyM = loc.accuracy,
            )
        }
    }

    override fun onCleared() {
        removeUpdates()
        super.onCleared()
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1000L
        const val MIN_UPDATE_INTERVAL_MS = 500L
        const val MIN_SPEED_MPS = 0.5f
        const val MAX_ACCURACY_M = 30f
    }
}
