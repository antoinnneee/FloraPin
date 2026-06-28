package com.florapin.app.friends

import android.content.Context
import com.florapin.app.util.SeenIdsStore

/**
 * Suivi local des demandes d'amis entrantes déjà « vues », pour le badge sur
 * l'entrée amis 🤝. Ouvrir l'écran amis marque les demandes entrantes courantes
 * comme vues → badge à 0 (même sans avoir accepté/refusé).
 */
class FriendsBadgeStore(context: Context) :
    SeenIdsStore(context, PREFS, KEY) {
    private companion object {
        const val PREFS = "florapin_friends"
        const val KEY = "friends_seen_ids"
    }
}
