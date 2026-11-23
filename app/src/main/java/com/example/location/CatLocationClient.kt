package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CatLocationClient(context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(intervalMs: Long = 5000L): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(2500L)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error -> close(error) }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchFreshCurrentLocation(): Location? {
        val highAccuracy = try {
            val cts = CancellationTokenSource()
            try {
                kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                }
            } finally {
                cts.cancel()
            }
        } catch (_: Exception) {
            null
        }
        if (highAccuracy != null) return highAccuracy

        val balanced = try {
            val cts = CancellationTokenSource()
            try {
                kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                }
            } finally {
                cts.cancel()
            }
        } catch (_: Exception) {
            null
        }
        if (balanced != null) return balanced

        val fusedLast = try {
            kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                fusedClient.lastLocation.await()
            }
        } catch (_: Exception) {
            null
        }
        if (fusedLast != null) return fusedLast

        return try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val providers = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER
            )
            providers.mapNotNull { provider ->
                try {
                    lm?.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }.maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }
}

fun calculateDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}
