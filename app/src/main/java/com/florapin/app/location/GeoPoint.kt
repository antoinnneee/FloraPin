package com.florapin.app.location

import java.util.Locale

/**
 * Position géographique associée à une capture.
 *
 * @param latitude en degrés décimaux.
 * @param longitude en degrés décimaux.
 * @param accuracyMeters précision horizontale estimée, en mètres (rayon à 68%).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
) {
    /** Représentation courte type "48.85837, 2.29448 (±5 m)". */
    fun format(): String = String.format(
        Locale.US,
        "%.5f, %.5f (±%.0f m)",
        latitude,
        longitude,
        accuracyMeters,
    )
}
