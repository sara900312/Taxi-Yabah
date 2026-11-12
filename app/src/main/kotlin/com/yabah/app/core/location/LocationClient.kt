package com.yabah.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.yabah.app.core.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class LocationFix(val point: GeoPoint, val accuracyM: Float?)

/** One-shot, foreground-only location for choosing a pickup. No background tracking for passengers. */
interface LocationClient {
    fun hasPermission(): Boolean
    suspend fun currentLocation(): LocationFix?
}

class AndroidLocationClient(private val context: Context) : LocationClient {
    private val lm = context.getSystemService(LocationManager::class.java)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationFix? {
        if (!hasPermission() || lm == null) return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return null
        val fresh = withTimeoutOrNull(12_000) { current(providers.first()) }
        val best = fresh ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 5 * 60_000 }
            .minByOrNull { it.accuracy }
        return best?.let { LocationFix(GeoPoint(it.latitude, it.longitude), if (it.hasAccuracy()) it.accuracy else null) }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun current(provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm!!.getCurrentLocation(provider, signal, Executors.newSingleThreadExecutor()) { cont.resume(it) }
        } else {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) { if (cont.isActive) cont.resume(location) }
                // Required on API 24-28, where this interface method is still abstract.
                @Deprecated("Deprecated in Java") @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) { if (cont.isActive) cont.resume(null) }
            }
            lm!!.requestSingleUpdate(provider, listener, context.mainLooper)
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
    }
}
