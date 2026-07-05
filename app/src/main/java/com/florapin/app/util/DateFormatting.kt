package com.florapin.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formate un epoch millis en date/heure courte locale (ex. "21 juin 2026 09:14"). */
fun formatCaptureDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))

/**
 * Clé de regroupement par mois calendaire ("yyyy-MM") à partir d'un epoch millis.
 * Stable et triable, indépendante de la locale — sert d'ancre au regroupement de
 * la galerie et au fast scroller (TÂCHE 6.7).
 */
fun monthKey(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(epochMillis))

/**
 * Libellé d'en-tête de mois lisible et localisé (ex. "Juin 2026"), première
 * lettre capitalisée. Utilise le nom de mois autonome ("LLLL") pour un rendu
 * correct dans les langues qui distinguent la forme.
 */
fun formatMonthLabel(epochMillis: Long): String {
    val raw = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(epochMillis))
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
