package com.florapin.app.map

import android.content.Context

/**
 * Styles de carte MapTiler proposés à l'utilisateur. L'`id` correspond au nom
 * de la carte dans l'URL de style MapTiler ; le `label` est affiché dans le
 * sélecteur. Tous sont disponibles sur l'offre gratuite MapTiler.
 */
enum class MapStyle(val id: String, val label: String) {
    BRIGHT("bright-v2", "Clair"),
    SATELLITE("satellite", "Satellite"),
    HYBRID("hybrid", "Hybride"),
    WINTER("winter-v2", "Hiver"),
    ;

    companion object {
        val DEFAULT = BRIGHT

        fun fromId(id: String?): MapStyle = entries.find { it.id == id } ?: DEFAULT
    }
}

/** Construit l'URL de style MapTiler pour un style donné. */
fun mapTilerStyleUrl(apiKey: String, style: MapStyle): String =
    "https://api.maptiler.com/maps/${style.id}/style.json?key=$apiKey"

/**
 * Préférence locale du style de carte choisi (réglage par appareil). Partagée
 * entre la carte principale et la mini-carte du détail.
 */
class MapStylePreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("florapin_map", Context.MODE_PRIVATE)

    fun get(): MapStyle = MapStyle.fromId(prefs.getString(KEY, null))

    fun set(style: MapStyle) {
        prefs.edit().putString(KEY, style.id).apply()
    }

    private companion object {
        const val KEY = "map_style"
    }
}
