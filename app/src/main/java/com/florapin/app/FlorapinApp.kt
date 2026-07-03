package com.florapin.app

import android.app.Application
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.push.PushTokenRegistrar
import com.florapin.app.sync.ConnectivityObserver
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.sync.SyncScheduler

/**
 * Classe Application : porte le cycle de vie app-scoped de l'observation réseau
 * et amorce la synchronisation (NODE-50).
 *
 * Choisie plutôt que MainActivity pour survivre aux recréations d'activité et
 * aux changements de configuration.
 */
class FlorapinApp : Application() {

    private lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate() {
        super.onCreate()

        // Relance la sync à chaque retour réseau (le worker est no-op si non connecté).
        connectivityObserver = ConnectivityObserver(this)
        connectivityObserver.start { SyncScheduler.syncNow(this) }

        val hasSession = EncryptedTokenStore(this).refreshToken() != null

        // Passage du défaut de sync ON → OFF : fige l'état des installations
        // existantes (session présente) pour qu'une mise à jour ne coupe pas leur
        // sync. Sans effet sur les nouvelles installations (nouveau défaut OFF).
        SyncPreferences(this).migrateDefaultForExistingInstall(hasSession)

        // Si déjà connecté au démarrage : planifie la sync périodique + une passe
        // immédiate, et (ré)enregistre le jeton push.
        if (hasSession) {
            SyncScheduler.schedulePeriodic(this)
            SyncScheduler.syncNow(this)
            PushTokenRegistrar.register(this)
        }
    }
}
