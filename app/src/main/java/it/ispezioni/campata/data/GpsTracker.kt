package it.ispezioni.campata.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Usa direttamente LocationManager: il provider GPS funziona senza rete.
 * Il provider "network" viene usato solo se disponibile, come aiuto iniziale.
 */
class GpsTracker(context: Context) {

    private val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _satellites = MutableStateFlow(0)
    val satellites: StateFlow<Int> = _satellites.asStateFlow()

    // Tutti i metodi implementati esplicitamente: su Android 8-10 non sono "default".
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isBetter(location, _location.value)) _location.value = location
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _satellites.value = used
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        val providers = lm.allProviders
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (p !in providers) continue
            try {
                lm.getLastKnownLocation(p)?.let { if (isBetter(it, _location.value)) _location.value = it }
            } catch (_: Exception) { }
            try {
                lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) { }
        }
        try {
            @Suppress("DEPRECATION")
            lm.registerGnssStatusCallback(gnssCallback, handler)
        } catch (_: Exception) { }
    }

    fun stop() {
        if (!running) return
        running = false
        try { lm.removeUpdates(listener) } catch (_: Exception) { }
        try { lm.unregisterGnssStatusCallback(gnssCallback) } catch (_: Exception) { }
    }

    fun isGpsEnabled(): Boolean = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }

    private fun isBetter(n: Location, o: Location?): Boolean {
        if (o == null) return true
        val dt = (n.elapsedRealtimeNanos - o.elapsedRealtimeNanos) / 1e9
        if (dt > 10) return true
        if (dt < -10) return false
        if (!o.hasAccuracy()) return true
        if (!n.hasAccuracy()) return false
        return n.accuracy <= o.accuracy + 3f
    }

    companion object {
        fun ageSeconds(loc: Location, nowNanos: Long = SystemClock.elapsedRealtimeNanos()): Long =
            (nowNanos - loc.elapsedRealtimeNanos) / 1_000_000_000L
    }
}
