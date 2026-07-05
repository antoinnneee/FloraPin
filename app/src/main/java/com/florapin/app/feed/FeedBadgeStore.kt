package com.florapin.app.feed

import android.content.Context
import com.florapin.app.util.SeenIdsStore

/**
 * Suivi local des fleurs du feed « Partagées » déjà « vues », pour le badge de
 * nouveautés sur l'onglet 🖼️ de la bottom bar. Ouvrir l'onglet marque les fleurs
 * courantes du feed comme vues → badge à 0 (même sans avoir liké/commenté).
 */
class FeedBadgeStore(context: Context) :
    SeenIdsStore(context, PREFS, KEY) {
    private companion object {
        const val PREFS = "florapin_feed"
        const val KEY = "feed_seen_ids"
    }
}
