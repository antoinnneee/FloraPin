package com.florapin.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Accès ponctuel à la position via le FusedLocationProvider de Play Services.
 *
 * L'appelant doit s'être assuré qu'une permission de localisation est accordée
 * (gérée par le module permission) — sinon [currentLocation] renvoie `null`.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Demande une position fraîche (haute précision si FINE accordée).
     *
     * @return la position, ou `null` si la permission manque ou si le système
     * ne parvient pas à fixer une position.
     */
    @SuppressLint("MissingPermission") // vérifié explicitement via hasPermission()
    suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null

        val priority = Priority.PRIORITY_HIGH_ACCURACY
        val cancellationSource = CancellationTokenSource()

        val location = suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(priority, cancellationSource.token)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
            continuation.invokeOnCancellation { cancellationSource.cancel() }
        }

        return location?.let {
            GeoPoint(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracy,
            )
        }
    }
}
