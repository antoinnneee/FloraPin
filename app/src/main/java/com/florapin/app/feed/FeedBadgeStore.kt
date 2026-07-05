package com.florapin.app.feed

import android.content.Context
import com.florapin.app.util.SeenIdsStore

/**
 * Suivi local des fleurs du feed « Partagées » déjà « vues », pour le badge de
 * nouveautés sur l'onglet 🖼️ de la bottom bar. Ouvrir l'onglet marque les fleurs
 * courantes du feed comme vues → badge à 0 (même sans avoir liké/commenté).
 *
 * Mémorise aussi l'horodatage de la dernière ouverture de l'onglet (TÂCHE 3.2),
 * qui sert à placer le séparateur « Nouveau depuis votre dernière visite » dans
 * le feed.
 */
class FeedBadgeStore(context: Context) :
    SeenIdsStore(context, PREFS, KEY) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Horodatage ISO-8601 de la dernière ouverture de l'onglet, ou null si l'onglet
     * n'a encore jamais été ouvert (aucun séparateur à afficher dans ce cas).
     */
    fun lastVisit(): String? = prefs.getString(KEY_LAST_VISIT, null)

    /** Mémorise l'instant [nowIso] (ISO-8601 UTC) comme dernière visite de l'onglet. */
    fun markVisited(nowIso: String) {
        prefs.edit().putString(KEY_LAST_VISIT, nowIso).apply()
    }

    private companion object {
        const val PREFS = "florapin_feed"
        const val KEY = "feed_seen_ids"
        const val KEY_LAST_VISIT = "feed_last_visit"
    }
}
