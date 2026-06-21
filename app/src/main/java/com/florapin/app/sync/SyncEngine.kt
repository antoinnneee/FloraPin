package com.florapin.app.sync

import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.applyTo
import com.florapin.app.data.toPushItem
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.SyncApi
import com.florapin.app.network.dto.SyncPushRequest
import com.florapin.app.network.dto.UpdateFlowerRequest
import java.io.File
import java.time.Instant

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
) {
    suspend fun sync() {
        push()
        pull()
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
            runCatching {
                flowersApi.update(
                    flower.serverId!!,
                    UpdateFlowerRequest(notes = flower.notes),
                )
                repository.markSynced(flower.id, flower.serverId, now())
            }.onFailure { repository.markFailed(flower.id) }
        }

        deleted.forEach { flower ->
            runCatching {
                flowersApi.delete(flower.serverId!!)
                repository.markSynced(flower.id, flower.serverId, now())
            }.onFailure { repository.markFailed(flower.id) }
        }
    }

    /** Tire le delta depuis le dernier sync et applique maj + suppressions. */
    suspend fun pull() {
        val response = syncApi.pull(lastSyncStore.get())
        response.flowers.forEach { dto ->
            val existing = repository.findByServerId(dto.id)
            if (existing != null) {
                repository.update(dto.applyTo(existing))
            }
            // Fleurs distantes inconnues : non insérées ici (l'image n'est pas
            // téléchargée localement) — à compléter ultérieurement.
        }
        response.deletedIds.forEach { serverId ->
            repository.softDeleteByServerId(serverId, now())
        }
        lastSyncStore.set(response.serverTime)
    }

    private fun isoToMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
