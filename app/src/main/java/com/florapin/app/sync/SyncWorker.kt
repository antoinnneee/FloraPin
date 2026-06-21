package com.florapin.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.florapin.app.data.FlowerRepository
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.upload.ImageUploader
import okhttp3.OkHttpClient

/**
 * Worker de synchronisation : assemble les dépendances et lance [SyncEngine].
 * Ne fait rien si l'utilisateur n'est pas connecté.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tokenStore = EncryptedTokenStore(applicationContext)
        if (tokenStore.refreshToken() == null) {
            return Result.success() // non connecté : rien à synchroniser
        }

        return try {
            val apis = NetworkModule.createAuthenticated(tokenStore)
            val engine = SyncEngine(
                repository = FlowerRepository.from(applicationContext),
                syncApi = apis.sync,
                flowersApi = apis.flowers,
                uploadImage = ImageUploader(OkHttpClient())::upload,
                lastSyncStore = PrefsLastSyncStore(applicationContext),
            )
            engine.sync()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
