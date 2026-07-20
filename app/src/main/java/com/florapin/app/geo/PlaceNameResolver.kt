package com.florapin.app.geo

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Resolves GPS coordinates to a human-readable locality for shared flowers. */
object PlaceNameResolver {
    private data class AreaKey(val latitude: Int, val longitude: Int)

    private val cache = ConcurrentHashMap<AreaKey, String>()

    @Volatile
    private var regionResolver: RegionResolver? = null

    suspend fun resolve(context: Context, latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            val key = AreaKey(
                latitude = (latitude * CACHE_PRECISION).roundToInt(),
                longitude = (longitude * CACHE_PRECISION).roundToInt(),
            )
            cache[key]?.let { return@withContext it }

            val locality = reverseGeocode(context, latitude, longitude)
                ?: resolveRegion(context, latitude, longitude)
            locality?.also { cache[key] = it }
        }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): String? {
        if (!Geocoder.isPresent()) return null
        val address = runCatching {
            Geocoder(context.applicationContext, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return null

        return listOf(
            address.locality,
            address.subLocality,
            address.subAdminArea,
            address.adminArea,
        ).firstNotNullOfOrNull { value -> value?.takeIf { it.isNotBlank() } }
    }

    private fun resolveRegion(context: Context, latitude: Double, longitude: Double): String? {
        val resolver = regionResolver ?: synchronized(this) {
            regionResolver ?: runCatching {
                RegionResolver.fromAssets(context.applicationContext)
            }.getOrNull()?.also { regionResolver = it }
        }
        return resolver?.resolve(latitude, longitude)?.name
    }

    private const val CACHE_PRECISION = 10_000.0
}
