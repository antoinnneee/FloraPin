package com.florapin.app.onboarding

import android.content.Context

/**
 * Préférence locale « onboarding déjà vu » (réglage par appareil).
 *
 * L'onboarding (promesse sociale, permissions, choix de synchronisation) ne
 * s'affiche qu'à la toute première installation. Une fois parcouru (ou figé pour
 * une installation existante), le drapeau reste vrai et l'app démarre directement
 * sur Login ou la galerie.
 *
 * Fichier de prefs dédié (`florapin_onboarding`) : distinct de `florapin_sync`
 * partagé, jamais purgé au logout.
 */
class OnboardingPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("florapin_onboarding", Context.MODE_PRIVATE)

    /** Vrai si l'onboarding a déjà été parcouru (ou figé pour un install existant). */
    fun isDone(): Boolean = prefs.getBoolean(KEY, DEFAULT)

    /** Marque l'onboarding comme terminé (à la fin du dernier écran). */
    fun setDone() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    /**
     * Fige les installations existantes à l'introduction de l'onboarding : une
     * simple mise à jour ne doit pas ré-afficher l'onboarding à un utilisateur
     * qui utilise déjà l'app.
     *
     * - si un choix a déjà été enregistré (clé présente), on n'y touche pas ;
     * - sinon, si l'appareil porte déjà des données ([hasExistingData] : session
     *   active ou base contenant des fleurs), c'est une installation existante →
     *   on marque l'onboarding « déjà vu » ;
     * - sinon (nouvelle installation vierge), on ne touche à rien : la clé reste
     *   absente et l'onboarding s'affichera.
     *
     * Idempotente : dès qu'une valeur est écrite, les appels suivants ne font
     * rien. À appeler tôt au démarrage (cf. [com.florapin.app.FlorapinApp]).
     */
    fun markSeenForExistingInstall(hasExistingData: Boolean) {
        if (!prefs.contains(KEY) && hasExistingData) {
            prefs.edit().putBoolean(KEY, true).apply()
        }
    }

    private companion object {
        const val KEY = "onboarding_done"

        /** Défaut pour les installations vierges : onboarding à afficher. */
        const val DEFAULT = false
    }
}
