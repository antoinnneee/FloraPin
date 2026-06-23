package com.florapin.app.sync

import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.applyTo
import com.florapin.app.data.toEntity
import com.florapin.app.data.toPushItem
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.SyncApi
import com.florapin.app.network.dto.SyncPushRequest
import com.florapin.app.network.dto.UpdateFlowerRequest
import java.io.File
import java.time.Instant
import retrofit2.HttpException

/**
 * Orchestration offline-first (NODE-44) : pousse les changements locaux, tire le
 * delta serveur, applique les suppressions, persiste le curseur de sync.
 *
 * Conflits : last-write-wins (le delta serveur écrase le local lors du pull).
 * L'upload d'image est injecté (lambda) pour rester testable.
 */
class SyncEngine(
    private val repository: FlowerRepository,
    private val syncApi: SyncApi,
    private val flowersApi: FlowersApi,
    private val uploadImage: suspend (url: String, file: File) -> Unit,
    private val lastSyncStore: LastSyncStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    /** Sync des albums (NODE-102) ; optionnel pour rester rétrocompatible. */
    private val albumSync: AlbumSyncEngine? = null,
    /** Sync des photos additionnelles (NODE-107) ; optionnel. */
    private val photoSync: PhotoSyncEngine? = null,
) {
    suspend fun sync() {
        push()
        pull()
        // Les photos additionnelles se poussent après les fleurs (id serveur connu).
        photoSync?.push { localId -> repository.getById(localId)?.serverId }
        // Les albums se synchronisent après les fleurs : l'appartenance se
        // résout sur des fleurs déjà dotées de leur serverId.
        albumSync?.sync()
    }

    /** Envoie créations, mises à jour et suppressions locales en attente. */
    suspend fun push() {
        val pending = repository.pendingSync()
        val created = pending.filter { it.serverId == null && it.deletedAt == null }
        val updated = pending.filter { it.serverId != null && it.deletedAt == null }
        val deleted = pending.filter { it.serverId != null && it.deletedAt != null }

        if (created.isNotEmpty()) {
            val results = syncApi.push(SyncPushRequest(created.map { it.toPushItem() }))
            results.forEach { result ->
                val localId = result.localId.toLongOrNull() ?: return@forEach
                val local = repository.getById(localId)
                if (local != null && local.imagePath.isNotEmpty()) {
                    runCatching { uploadImage(result.upload.url, File(local.imagePath)) }
                }
                repository.markSynced(
                    localId,
                    result.flower.id,
                    isoToMillis(result.flower.updatedAt),
                )
            }
        }

        updated.forEach { flower ->
            try {
                flowersApi.update(
                    flower.serverId!!,
                    UpdateFlowerRequest(notes = flower.notes),
                )
                repository.markSynced(flower.id, flower.serverId, now())
            } catch (e: Exception) {
                handlePushFailure(flower.id, flower.serverId!!, e)
            }
        }

        deleted.forEach { flower ->
            try {
                flowersApi.delete(flower.serverId!!)
                repository.markSynced(flower.id, flower.serverId, now())
            } catch (e: Exception) {
                handlePushFailure(flower.id, flower.serverId!!, e)
            }
        }
    }

    /**
     * Sur 409 (conflit), le serveur fait foi : on marque synchronisé, le pull
     * suivant réconcilie le contenu (last-write-wins). Sinon, on marque en échec
     * pour réessayer plus tard.
     */
    private suspend fun handlePushFailure(
        localId: Long,
        serverId: String,
        error: Exception,
    ) {
        if (error is HttpException && error.code() == 409) {
            repository.markSynced(localId, serverId, now())
        } else {
            repository.markFailed(localId)
        }
    }

    /** Tire le delta depuis le dernier sync et applique maj + suppressions. */
    suspend fun pull() {
        val response = syncApi.pull(lastSyncStore.get())
        response.flowers.forEach { dto ->
            val existing = repository.findByServerId(dto.id)
            val localId = if (existing != null) {
                repository.update(dto.applyTo(existing))
                existing.id
            } else {
                // Fleur distante inconnue (autre appareil) : insérée avec son URL
                // image distante ; Coil charge l'URL présignée à l'affichage.
                repository.insert(dto.toEntity())
            }
            // Réconcilie les photos additionnelles de la fleur (NODE-107).
            photoSync?.reconcile(localId, dto.photos)
        }
        response.deletedIds.forEach { serverId ->
            repository.softDeleteByServerId(serverId, now())
        }
        lastSyncStore.set(response.serverTime)
    }

    private fun isoToMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
