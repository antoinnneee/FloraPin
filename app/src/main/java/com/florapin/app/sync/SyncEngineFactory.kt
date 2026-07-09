package com.florapin.app.sync

import android.content.Context
import com.florapin.app.capture.PhotoStorage
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.auth.TokenStore
import com.florapin.app.network.dto.ShareToAllFriendsRequest
import com.florapin.app.share.DefaultRecipient
import com.florapin.app.share.SharePreferences
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Assemble un [SyncEngine] authentifié (fleurs + albums + photos additionnelles)
 * à partir du contexte applicatif. Mutualisé entre le [SyncWorker] (sync de fond)
 * et le flush avant purge au logout (LocalSessionDataCleaner).
 */
object SyncEngineFactory {

    fun create(context: Context, tokenStore: TokenStore): SyncEngine {
        val apis = NetworkModule.createAuthenticated(tokenStore)
        val flowerRepo = FlowerRepository.from(context)
        // Téléchargement des images distantes : les URLs présignées sont publiques
        // (pas d'en-tête d'auth), un client OkHttp simple suffit.
        val imageCacher = ImageCacher(
            client = OkHttpClient(),
            targetDir = PhotoStorage.photosDir(context),
        )
        return SyncEngine(
            repository = flowerRepo,
            syncApi = apis.sync,
            flowersApi = apis.flowers,
            // L'image transite par l'API (multipart) qui la réencode en WebP.
            uploadFlowerImage = { serverId, file ->
                apis.flowers.uploadImage(serverId, filePart(file))
            },
            lastSyncStore = PrefsLastSyncStore(context),
            cacheRemoteImage = { serverId, url -> imageCacher.cache(serverId, url) },
            autoShareFlower = { serverId -> autoShare(context, apis.shares, serverId) },
            albumSync = AlbumSyncEngine(
                albums = AlbumRepository.from(context),
                flowers = flowerRepo,
                albumsApi = apis.albums,
                // Distingue mes albums des albums de groupe possédés par d'autres
                // membres (évite les conflits d'édition concurrente — TÂCHE 7.1).
                currentUserId = tokenStore.userId(),
            ),
            photoSync = PhotoSyncEngine(
                photos = PhotoRepository.from(context),
                photosApi = apis.photos,
                uploadPhotoImage = { flowerServerId, photoServerId, file ->
                    apis.photos.uploadImage(flowerServerId, photoServerId, filePart(file))
                },
                cacheRemoteImage = { photoServerId, url ->
                    imageCacher.cache(photoServerId, url)
                },
            ),
        )
    }

    /**
     * Applique le réglage « partager automatiquement mes nouvelles fleurs » à la
     * fleur qui vient d'obtenir son id serveur. Sans destinataire par défaut,
     * `autoShare()` est faux et rien n'est envoyé.
     */
    private suspend fun autoShare(context: Context, shares: SharesApi, serverId: String) {
        val prefs = SharePreferences(context)
        if (!prefs.autoShare()) return
        when (prefs.defaultRecipient()) {
            DefaultRecipient.ALL_FRIENDS -> shares.createForAllFriends(
                ShareToAllFriendsRequest(
                    scope = "flower",
                    flowerId = serverId,
                    includeGps = prefs.includeGps(),
                ),
            )
            // Exclu par autoShare(), mais le `when` reste exhaustif.
            DefaultRecipient.NONE -> Unit
        }
    }

    /** Construit la part multipart « file » attendue par les endpoints d'upload. */
    private fun filePart(file: File): MultipartBody.Part {
        val body = file.asRequestBody("image/jpeg".toMediaType())
        return MultipartBody.Part.createFormData("file", file.name, body)
    }
}
