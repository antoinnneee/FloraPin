package com.florapin.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.capture.ImageCompressionEngine
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository
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
        // Aucun JPEG brut ne quitte l'app, quel que soit le déclencheur de sync.
        val compressed = ImageCompressionEngine(
            FlowerRepository.from(applicationContext),
            PhotoRepository.from(applicationContext),
        ).compressPending()
        if (!compressed) return@withLock Result.retry()

        // Une passe forcée (bouton « Tout synchroniser ») s'exécute même quand la
        // sync automatique est désactivée ; sinon on respecte le réglage.
        val forced = inputData.getBoolean(SyncScheduler.KEY_FORCE, false)
        if (!forced && !SyncPreferences(applicationContext).isEnabled()) {
            return@withLock Result.success() // sync auto désactivée : app 100% locale
        }
        val tokenStore = EncryptedTokenStore(applicationContext)
        if (tokenStore.refreshToken() == null) {
            return@withLock Result.success() // non connecté : rien à synchroniser
        }

        // Expose l'état de la passe pour l'onglet Configuration (TÂCHE 6.14) :
        // en cours, puis réussie / échouée (+ message). L'horodatage de la
        // dernière synchro réussie reste porté par last_sync_at (curseur de pull).
        val status = SyncStatusStore(applicationContext)
        status.markRunning()
        try {
            SyncEngineFactory.create(applicationContext, tokenStore).sync()
            status.markSuccess()
            Result.success()
        } catch (e: Exception) {
            status.markError(e.message)
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
