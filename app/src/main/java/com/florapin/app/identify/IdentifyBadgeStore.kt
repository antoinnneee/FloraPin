package com.florapin.app.identify

import android.content.Context
import com.florapin.app.util.SeenIdsStore

/**
 * Suivi local des demandes d'identification d'amis déjà « vues », pour le badge
 * sur l'entrée « à identifier » 🔎. Ouvrir l'écran marque toutes les demandes
 * courantes comme vues → badge à 0 (même sans avoir proposé d'espèce).
 */
class IdentifyBadgeStore(context: Context) :
    SeenIdsStore(context, PREFS, KEY) {
    private companion object {
        const val PREFS = "florapin_identify"
        const val KEY = "identify_seen_ids"
    }
}
