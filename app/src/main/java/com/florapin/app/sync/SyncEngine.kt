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
    /** Téléverse l'image d'une fleur (serverId) ; le serveur la réencode en WebP. */
    private val uploadFlowerImage: suspend (serverId: String, file: File) -> Unit,
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
                .associateBy { it.localId }
            // On itère sur NOS fleurs locales et on retrouve leur résultat par
            // notre propre id : pas de dépendance au format du localId renvoyé.
            created.forEach { local ->
                val result = results[local.id.toString()] ?: return@forEach
                // Persiste le serverId AVANT toute opération faillible (upload
                // image, parsing de date). Sinon une erreur ici laisserait la
                // fleur en PENDING : au prochain sync elle serait re-poussée et
                // le serveur en créerait un doublon (fleur « grise » au pull).
                val serverUpdatedAt = runCatching { isoToMillis(result.flower.updatedAt) }
                    .getOrDefault(now())
                repository.markSynced(local.id, result.flower.id, serverUpdatedAt)
                if (local.imagePath.isNotEmpty()) {
                    runCatching {
                        uploadFlowerImage(result.flower.id, File(local.imagePath))
                    }
                }
            }
        }

        updated.forEach { flower ->
            try {
                flowersApi.update(
                    flower.serverId!!,
                    UpdateFlowerRequest(
                        notes = flower.notes,
                        visibility = flower.visibility,
                        feedIncludeGps = flower.feedIncludeGps,
                        species = flower.species,
                        speciesId = flower.speciesId,
                        tags = flower.tags,
                    ),
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
            if (existing != null) {
                repository.update(dto.applyTo(existing))
                // Réconcilie les photos additionnelles de la fleur (NODE-107).
                photoSync?.reconcile(existing.id, dto.photos)
                return@forEach
            }

            // Fleur distante au serverId inconnu localement. Avant de l'insérer,
            // on vérifie qu'elle ne fait pas doublon avec une de nos captures
            // locales (même date de capture, image présente) déjà synchronisée
            // sous un AUTRE serverId : ce serait un orphelin créé par un
            // double-push. On le supprime côté serveur au lieu de l'afficher en
            // double (fleur « grise »).
            val twin = repository.findLocalTwin(isoToMillis(dto.takenAt))
            if (twin?.serverId != null && twin.serverId != dto.id) {
                runCatching { flowersApi.delete(dto.id) }
                return@forEach
            }

            // Vraie fleur distante (autre appareil) : insérée avec son URL image
            // distante ; Coil charge l'URL présignée à l'affichage.
            val localId = repository.insert(dto.toEntity())
            photoSync?.reconcile(localId, dto.photos)
        }
        response.deletedIds.forEach { serverId ->
            repository.softDeleteByServerId(serverId, now())
        }
        lastSyncStore.set(response.serverTime)
    }

    private fun isoToMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
