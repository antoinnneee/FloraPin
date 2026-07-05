package com.florapin.app.data

import android.content.Context
import com.florapin.app.capture.PhotoStorage
import com.florapin.app.network.auth.SessionDataCleaner
import com.florapin.app.sync.LastSyncStore
import com.florapin.app.sync.PrefsLastSyncStore

/**
 * Purge des données locales (table `flowers`, fichiers images du stockage privé,
 * curseur de sync). Utilisé à la suppression de compte (NODE-93). Le logout, lui,
 * conserve désormais les photos sur l'appareil (réglage de sync optionnel).
 */
class LocalSessionDataCleaner(
    private val context: Context,
    private val flowers: FlowerRepository,
    private val albums: AlbumRepository,
    private val photos: PhotoRepository,
    private val saved: SavedFlowerRepository,
    private val lastSync: LastSyncStore,
) : SessionDataCleaner {

    override suspend fun clearLocalData() {
        flowers.deleteAll()
        albums.deleteAll()
        photos.deleteAll()
        saved.deleteAll()
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
                albums = AlbumRepository.from(app),
                photos = PhotoRepository.from(app),
                saved = SavedFlowerRepository.from(app),
                lastSync = PrefsLastSyncStore(app),
            )
        }
    }
}
