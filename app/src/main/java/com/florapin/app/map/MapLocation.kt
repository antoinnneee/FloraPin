package com.florapin.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/** Vrai si une permission de localisation (fine ou approximative) est accordée. */
fun hasLocationPermission(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    return granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)
}

/** Permissions de localisation à demander en runtime (précise + approximative). */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Active l'indicateur « ma position » de MapLibre (point bleu + halo de
 * précision) sur [map]. L'appelant doit avoir vérifié la permission au préalable.
 * Réactiver après un rechargement de style réapplique simplement les réglages.
 */
@SuppressLint("MissingPermission") // permission vérifiée par l'appelant
fun MapLibreMap.enableMyLocation(context: Context, style: Style) {
    if (!style.isFullyLoaded) return
    val component = locationComponent
    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .useDefaultLocationEngine(true)
            .build(),
    )
    component.isLocationComponentEnabled = true
    component.cameraMode = CameraMode.NONE
    component.renderMode = RenderMode.COMPASS
}

/**
 * Recentre la caméra sur la dernière position connue. Renvoie false si la
 * position n'est pas encore disponible (indicateur non activé ou pas de fix GPS).
 */
@SuppressLint("MissingPermission") // permission vérifiée par l'appelant
fun MapLibreMap.centerOnMyLocation(zoom: Double = 14.0): Boolean {
    val component = locationComponent
    if (!component.isLocationComponentActivated) return false
    val location = component.lastKnownLocation ?: return false
    animateCamera(
        CameraUpdateFactory.newLatLngZoom(
            LatLng(location.latitude, location.longitude),
            zoom,
        ),
    )
    return true
}
