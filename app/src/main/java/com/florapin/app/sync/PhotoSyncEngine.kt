package com.florapin.app.sync

import com.florapin.app.data.PhotoRepository
import com.florapin.app.data.SyncState
import com.florapin.app.data.applyTo
import com.florapin.app.data.toEntity
import com.florapin.app.network.api.PhotosApi
import com.florapin.app.network.dto.PhotoDto
import java.io.File

/**
 * Synchronisation des photos additionnelles (NODE-107), offline-first.
 * Push : envoie les photos capturées localement (upload présigné) et propage
 * les suppressions. Pull : réconcilie depuis les photos d'une fleur déjà tirée
 * (FlowerDto.photos), en ignorant la couverture (déjà portée par la fleur).
 */
class PhotoSyncEngine(
    private val photos: PhotoRepository,
    private val photosApi: PhotosApi,
    private val uploadImage: suspend (url: String, file: File) -> Unit,
) {
    /**
     * Pousse les photos locales en attente, pour les fleurs déjà synchronisées
     * (dont l'id serveur est connu). [flowerServerIdOf] résout l'id serveur d'une
     * fleur locale (null si pas encore synchronisée).
     */
    suspend fun push(flowerServerIdOf: suspend (Long) -> String?) {
        for (photo in photos.pendingSync()) {
            val flowerServerId = flowerServerIdOf(photo.flowerLocalId) ?: continue
            when {
                photo.serverId == null && photo.deletedAt == null -> {
                    val res = photosApi.add(flowerServerId)
                    if (photo.imagePath.isNotEmpty()) {
                        runCatching { uploadImage(res.upload.url, File(photo.imagePath)) }
                    }
                    photos.markSynced(photo.id, res.photo.id)
                }
                photo.serverId != null && photo.deletedAt != null -> {
                    runCatching { photosApi.remove(flowerServerId, photo.serverId) }
                    photos.hardDelete(photo.id)
                }
            }
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
                photos.insert(dto.toEntity(flowerLocalId))
            } else if (existing.syncState == SyncState.SYNCED.name) {
                photos.update(dto.applyTo(existing))
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
}
