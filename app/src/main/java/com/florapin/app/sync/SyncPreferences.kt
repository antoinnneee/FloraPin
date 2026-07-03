package com.florapin.app.sync

import android.content.Context

/**
 * Préférence locale « Synchronisation cloud » (réglage par appareil).
 *
 * Désactivée par défaut : l'app est device-first (100 % locale). [SyncWorker] ne
 * pousse/tire la bibliothèque que si l'utilisateur active explicitement la sync
 * (à l'inscription ou dans Profil). Le feed des amis (lecture seule) reste
 * accessible indépendamment de ce réglage.
 */
class SyncPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("florapin_sync", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY, DEFAULT)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    /**
     * Migration unique liée au passage du défaut ON → OFF. Elle FIGE l'état des
     * installations existantes pour qu'une simple mise à jour ne coupe pas leur
     * synchronisation :
     *
     * - si un choix a déjà été enregistré (clé présente), on n'y touche pas ;
     * - sinon, si l'appareil a déjà une session ([hasExistingSession]), c'est une
     *   installation existante qui tournait sur l'ancien défaut ON → on l'écrit
     *   explicitement pour préserver son comportement ;
     * - sinon (nouvelle installation, sans session), on ne touche à rien : la clé
     *   reste absente et le nouveau défaut OFF s'applique.
     *
     * Idempotente : dès qu'une valeur est écrite, les appels suivants la voient et
     * ne font rien. À appeler tôt au démarrage (cf. FlorapinApp.onCreate).
     */
    fun migrateDefaultForExistingInstall(hasExistingSession: Boolean) {
        if (!prefs.contains(KEY) && hasExistingSession) {
            prefs.edit().putBoolean(KEY, LEGACY_DEFAULT).apply()
        }
    }

    private companion object {
        const val KEY = "sync_enabled"

        /** Nouveau défaut (device-first) pour les installations vierges. */
        const val DEFAULT = false

        /**
         * Ancien défaut, préservé pour les installations existantes lors de la
         * mise à jour (cf. [migrateDefaultForExistingInstall]).
         */
        const val LEGACY_DEFAULT = true
    }
}
