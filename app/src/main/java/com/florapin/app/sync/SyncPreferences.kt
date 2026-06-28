package com.florapin.app.sync

import android.content.Context

/**
 * Préférence locale « Synchronisation cloud » (réglage par appareil).
 *
 * Activée par défaut : [SyncWorker] pousse la bibliothèque locale vers le serveur
 * et tire le delta. L'utilisateur peut la désactiver pour rester 100% local (les
 * photos de base restent alors sur l'appareil). Le feed des amis (lecture seule)
 * reste accessible indépendamment de ce réglage.
 */
class SyncPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("florapin_sync", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY, DEFAULT)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    private companion object {
        const val KEY = "sync_enabled"
        const val DEFAULT = true
    }
}
