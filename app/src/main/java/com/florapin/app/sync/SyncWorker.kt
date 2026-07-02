package com.florapin.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Worker de synchronisation : assemble les dépendances et lance [SyncEngine].
 * Ne fait rien si l'utilisateur n'est pas connecté.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = SYNC_LOCK.withLock {
        if (!SyncPreferences(applicationContext).isEnabled()) {
            return@withLock Result.success() // sync désactivée : app 100% locale
        }
        val tokenStore = EncryptedTokenStore(applicationContext)
        if (tokenStore.refreshToken() == null) {
            return@withLock Result.success() // non connecté : rien à synchroniser
        }

        try {
            SyncEngineFactory.create(applicationContext, tokenStore).sync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private companion object {
        /**
         * Verrou process-wide (I8) : le travail périodique
         * (`florapin-sync-periodic`) et le one-shot (`florapin-sync-now`) sont
         * deux unique works distincts que WorkManager peut exécuter en
         * parallèle. Sérialiser doWork évite deux moteurs de sync concurrents
         * (double push → doublons, courses sur le curseur de sync).
         */
        val SYNC_LOCK = Mutex()
    }
}
