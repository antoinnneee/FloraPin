package com.florapin.app.sync

import com.florapin.app.data.PhotoRepository
import com.florapin.app.capture.PhotoStorage
import com.florapin.app.data.SyncState
import com.florapin.app.data.applyTo
import com.florapin.app.data.toEntity
import com.florapin.app.network.api.PhotosApi
import com.florapin.app.network.dto.PhotoDto
import java.io.File
import retrofit2.HttpException

/**
 * Synchronisation des photos additionnelles (NODE-107), offline-first.
 * Push : envoie les photos capturées localement (upload présigné) et propage
 * les suppressions. Pull : réconcilie depuis les photos d'une fleur déjà tirée
 * (FlowerDto.photos), en ignorant la couverture (déjà portée par la fleur).
 */
class PhotoSyncEngine(
    private val photos: PhotoRepository,
    private val photosApi: PhotosApi,
    /** Téléverse les variantes WebP finales d'une photo (multipart). */
    private val uploadPhotoImage:
        suspend (flowerServerId: String, photoServerId: String, file: File) -> Unit,
    /**
     * Met en cache localement l'image d'une photo distante (clé = serverId, URL
     * présignée) ; retourne le chemin local ou null. Par défaut no-op
     * (tests/compat). Évite que l'affichage dépende de l'expiration des URLs.
     */
    private val cacheRemoteImage: suspend (photoServerId: String, url: String) -> String? =
        { _, _ -> null },
) {
    /**
     * Pousse les photos locales en attente, pour les fleurs déjà synchronisées
     * (dont l'id serveur est connu). [flowerServerIdOf] résout l'id serveur d'une
     * fleur locale (null si pas encore synchronisée).
     */
    suspend fun push(flowerServerIdOf: suspend (Long) -> String?) {
        // Retente d'abord les uploads d'image restés en souffrance (I9).
        retryPendingImageUploads(flowerServerIdOf)

        for (photo in photos.pendingSync()) {
            val flowerServerId = flowerServerIdOf(photo.flowerLocalId) ?: continue
            // Chaque photo est isolée (I10) : un échec permanent (404…) sur
            // l'une ne bloque pas la sync des autres ni ne la fait rejouer
            // indéfiniment par le worker.
            try {
                when {
                    photo.serverId == null && photo.deletedAt == null -> {
                        val res = photosApi.add(flowerServerId)
                        // markSynced d'abord : le serverId est persisté même si
                        // l'upload échoue (évite une re-création en doublon).
                        photos.markSynced(photo.id, res.photo.id)
                        if (photo.imagePath.isNotEmpty()) {
                            val uploaded = runCatching {
                                uploadPhotoImage(
                                    flowerServerId,
                                    res.photo.id,
                                    File(photo.imagePath),
                                )
                            }.isSuccess
                            // Échec après markSynced : marqué pour être retenté
                            // au prochain sync au lieu d'être perdu (I9).
                            if (!uploaded) photos.setImagePendingUpload(photo.id, true)
                        }
                    }
                    photo.serverId != null && photo.deletedAt != null -> {
                        runCatching { photosApi.remove(flowerServerId, photo.serverId) }
                        photos.hardDelete(photo.id)
                    }
                }
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 404) {
                    // La fleur n'existe plus côté serveur : la photo ne pourra
                    // jamais être poussée, on la purge localement.
                    if (photo.imagePath.isNotEmpty()) {
                        runCatching { PhotoStorage.deleteWithThumbnail(photo.imagePath) }
                    }
                    photos.hardDelete(photo.id)
                }
                // Sinon (réseau, 5xx…) : la photo reste PENDING et sera
                // retentée au prochain sync.
            }
        }
    }

    /** Retente les uploads d'image marqués en souffrance (I9), best-effort. */
    private suspend fun retryPendingImageUploads(
        flowerServerIdOf: suspend (Long) -> String?,
    ) {
        for (photo in photos.pendingImageUploads()) {
            val photoServerId = photo.serverId ?: continue
            val flowerServerId = flowerServerIdOf(photo.flowerLocalId) ?: continue
            if (photo.imagePath.isEmpty()) {
                photos.setImagePendingUpload(photo.id, false)
                continue
            }
            runCatching {
                uploadPhotoImage(flowerServerId, photoServerId, File(photo.imagePath))
                photos.setImagePendingUpload(photo.id, false)
            }
        }
    }

    /**
     * Purge locale des photos d'une fleur supprimée (lignes ET fichiers), une
     * fois la suppression de la fleur propagée au serveur (C3) : le serveur a
     * déjà supprimé ses photos en cascade.
     */
    suspend fun purgeForFlower(flowerLocalId: Long) {
        photos.allForFlower(flowerLocalId).forEach { photo ->
            if (photo.imagePath.isNotEmpty()) {
                runCatching { PhotoStorage.deleteWithThumbnail(photo.imagePath) }
            }
            photos.hardDelete(photo.id)
        }
    }

    /**
     * Réconcilie les photos locales d'une fleur depuis l'état serveur. Ignore la
     * couverture (représentée par l'image principale de la fleur) et ne touche
     * pas aux photos locales encore en attente (PENDING).
     */
    suspend fun reconcile(flowerLocalId: Long, remote: List<PhotoDto>) {
        val additional = remote.filter { !it.isCover }
        for (dto in additional) {
            val existing = photos.findByServerId(dto.id)
            if (existing == null) {
                val localId = photos.insert(dto.toEntity(flowerLocalId))
                cacheImageIfMissing(localId, dto.id, imagePath = "", dto.url)
            } else if (existing.syncState == SyncState.SYNCED.name) {
                photos.update(dto.applyTo(existing))
                cacheImageIfMissing(existing.id, dto.id, existing.imagePath, dto.url)
            }
        }
        // Photos synchronisées disparues du serveur : suppression locale.
        val serverIds = additional.map { it.id }.toSet()
        photos.forFlower(flowerLocalId)
            .filter {
                it.serverId != null &&
                    it.serverId !in serverIds &&
                    it.syncState == SyncState.SYNCED.name
            }
            .forEach { photos.hardDelete(it.id) }
    }

    /**
     * Télécharge l'image de la photo [localId] dans le stockage privé et renseigne
     * son `imagePath`, sauf si une copie locale existe déjà ou si l'URL est
     * absente. Best-effort : un échec laisse l'URL distante en repli.
     */
    private suspend fun cacheImageIfMissing(
        localId: Long,
        serverId: String,
        imagePath: String,
        remoteUrl: String?,
    ) {
        if (imagePath.isNotEmpty() || remoteUrl.isNullOrEmpty()) return
        val path = cacheRemoteImage(serverId, remoteUrl) ?: return
        photos.cacheImagePath(localId, path)
    }
}
