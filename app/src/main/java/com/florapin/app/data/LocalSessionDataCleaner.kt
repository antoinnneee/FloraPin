package com.florapin.app.data

import android.content.Context
import com.florapin.app.capture.PhotoStorage
import com.florapin.app.network.auth.SessionDataCleaner
import com.florapin.app.sync.LastSyncStore
import com.florapin.app.sync.PrefsLastSyncStore

/**
 * Purge des données locales au logout (NODE-93) : table `flowers`, fichiers
 * images du stockage privé, et curseur de sync (full-pull au prochain login).
 */
class LocalSessionDataCleaner(
    private val context: Context,
    private val flowers: FlowerRepository,
    private val lastSync: LastSyncStore,
) : SessionDataCleaner {

    override suspend fun clearLocalData() {
        flowers.deleteAll()
        PhotoStorage.clearAll(context)
        lastSync.clear()
    }

    companion object {
        /** Câble le cleaner sur la base et les stores singletons. */
        fun from(context: Context): LocalSessionDataCleaner {
            val app = context.applicationContext
            return LocalSessionDataCleaner(
                context = app,
                flowers = FlowerRepository.from(app),
                lastSync = PrefsLastSyncStore(app),
            )
        }
    }
}
