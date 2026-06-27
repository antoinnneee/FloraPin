package com.florapin.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.florapin.app.network.auth.EncryptedTokenStore

/**
 * Worker de synchronisation : assemble les dépendances et lance [SyncEngine].
 * Ne fait rien si l'utilisateur n'est pas connecté.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!SyncPreferences(applicationContext).isEnabled()) {
            return Result.success() // synchronisation désactivée : app 100% locale
        }
        val tokenStore = EncryptedTokenStore(applicationContext)
        if (tokenStore.refreshToken() == null) {
            return Result.success() // non connecté : rien à synchroniser
        }

        return try {
            SyncEngineFactory.create(applicationContext, tokenStore).sync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
