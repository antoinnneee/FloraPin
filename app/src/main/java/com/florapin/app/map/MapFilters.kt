package com.florapin.app.map

import java.util.Calendar

/**
 * Filtre temporel appliqué aux marqueurs de la carte.
 *
 * Les filtres « ami » et « espèce » prévus (NODE-14) dépendent de données pas
 * encore disponibles dans le modèle : les amis viendront avec le backend social
 * (NODE-15), l'espèce avec l'identification (NODE-24). La structure de filtrage
 * est volontairement extensible pour les accueillir.
 */
enum class DateFilter(val label: String) {
    ALL("Tout"),
    LAST_7_DAYS("7 jours"),
    LAST_30_DAYS("30 jours"),
    THIS_YEAR("Cette année"),
    ;

    /**
     * Horodatage minimal (epoch millis) accepté par ce filtre, ou null si aucun
     * seuil (ALL). [now] est l'instant courant.
     */
    fun minTimestamp(now: Long): Long? = when (this) {
        ALL -> null
        LAST_7_DAYS -> now - DAY_MS * 7
        LAST_30_DAYS -> now - DAY_MS * 30
        THIS_YEAR -> startOfYear(now)
    }

    private fun startOfYear(now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
