package com.florapin.app.util

import android.content.Context

/**
 * Suivi local (par appareil) d'un ensemble d'identifiants déjà « vus », pour
 * afficher un badge de NOUVEAUTÉS sur une entrée de navigation.
 *
 * Badge = nombre d'ids courants absents de l'ensemble « vu ». Ouvrir l'écran
 * concerné appelle [markSeen] avec les ids courants → le badge revient à 0, même
 * si rien n'a été traité. Générique : une sous-classe fixe le fichier de prefs et
 * la clé (ex. demandes d'identification, demandes d'amis).
 */
open class SeenIdsStore(
    context: Context,
    prefsName: String,
    private val key: String,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Ids déjà vus. */
    fun seenIds(): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()

    /**
     * Marque [ids] comme vus. On REMPLACE l'ensemble par [ids] : tout id disparu
     * (traité ailleurs / retiré) sort du suivi (borne la taille), et tout id encore
     * présent reste « vu ». Une copie est passée à `putStringSet` (SharedPreferences
     * ne doit pas conserver la référence mutable d'origine).
     */
    fun markSeen(ids: Set<String>) {
        prefs.edit().putStringSet(key, HashSet(ids)).apply()
    }

    /** Nombre d'ids courants non encore vus (= valeur du badge). */
    fun unseenCount(currentIds: List<String>): Int {
        val seen = seenIds()
        return currentIds.count { it !in seen }
    }
}
