package com.florapin.app

import android.app.Application
import com.florapin.app.data.FloraDatabase
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.onboarding.OnboardingPrefs
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

        // Introduction de l'onboarding : une mise à jour ne doit pas le ré-afficher
        // à une installation existante. On la détecte à moindre coût (sans requête
        // Room sur le thread principal) : session active ou base locale déjà créée
        // (le fichier n'existe qu'après une première utilisation de l'app).
        val hasExistingData = hasSession || getDatabasePath(FloraDatabase.DB_NAME).exists()
        OnboardingPrefs(this).markSeenForExistingInstall(hasExistingData)

        // Si déjà connecté au démarrage : planifie la sync périodique + une passe
        // immédiate, et (ré)enregistre le jeton push.
        if (hasSession) {
            SyncScheduler.schedulePeriodic(this)
            SyncScheduler.syncNow(this)
            PushTokenRegistrar.register(this)
        }
    }
}
