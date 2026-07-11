package com.florapin.app.sync

import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.SyncState
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
    /**
     * Met en cache localement l'image d'une fleur distante (clé = serverId, URL
     * présignée) : télécharge le fichier et retourne son chemin local, ou null en
     * cas d'échec. Par défaut no-op (tests/compat). Évite que l'affichage dépende
     * de l'expiration des URLs présignées une fois la fleur tirée.
     */
    private val cacheRemoteImage: suspend (serverId: String, url: String) -> String? =
        { _, _ -> null },
    /**
     * Partage une fleur fraîchement créée côté serveur selon les réglages de
     * partage par défaut (cf. SharePreferences). Appelé une seule fois par fleur,
     * au moment où elle acquiert son id serveur. Par défaut no-op (tests/compat).
     */
    private val autoShareFlower: suspend (serverId: String) -> Unit = {},
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
        // Retente d'abord les uploads d'image restés en souffrance (I9) : fleurs
        // créées côté serveur lors d'un sync précédent mais dont l'envoi du
        // fichier avait échoué après markSynced.
        retryPendingImageUploads()

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
                repository.markSynced(
                    local.id, result.flower.id, serverUpdatedAt, local.updatedAt,
                )
                if (local.imagePath.isNotEmpty()) {
                    val uploaded = runCatching {
                        uploadFlowerImage(result.flower.id, File(local.imagePath))
                    }.isSuccess
                    // Échec d'upload APRÈS markSynced : sans marqueur, l'image
                    // serait perdue (fleur « grise » côté serveur). On la
                    // retentera aux syncs suivantes (I9).
                    if (!uploaded) repository.setImagePendingUpload(local.id, true)
                }
                // Partage automatique, après l'upload : partager plus tôt ferait
                // apparaître une fleur sans photo dans le flux des amis. Le
                // partage est accessoire, son échec ne doit pas rompre la sync.
                runCatching { autoShareFlower(result.flower.id) }
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
                repository.markSynced(flower.id, flower.serverId, now(), flower.updatedAt)
            } catch (e: Exception) {
                handlePushFailure(flower, e)
            }
        }

        deleted.forEach { flower ->
            try {
                val response = flowersApi.delete(flower.serverId!!)
                when {
                    response.isSuccessful -> purgeLocal(flower)
                    // Suppression idempotente : absente du serveur = objectif atteint.
                    response.code() == 404 -> purgeLocal(flower)
                    // Retrofit ne lève pas automatiquement quand l'endpoint
                    // retourne Response<T>. Sans ce contrôle, un 401/403/500
                    // purgeait la ligne locale alors que la fleur restait sur le
                    // serveur, donc encore visible via un partage existant.
                    else -> throw HttpException(response)
                }
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 404) {
                    // Déjà supprimée côté serveur : même issue qu'un succès.
                    purgeLocal(flower)
                } else {
                    handlePushFailure(flower, e)
                }
            }
        }
    }

    /** Retente les uploads d'image marqués en souffrance (I9), best-effort. */
    private suspend fun retryPendingImageUploads() {
        repository.pendingImageUploads().forEach { flower ->
            val serverId = flower.serverId ?: return@forEach
            if (flower.imagePath.isEmpty()) {
                // Plus de fichier local à envoyer : rien à retenter.
                repository.setImagePendingUpload(flower.id, false)
                return@forEach
            }
            runCatching {
                uploadFlowerImage(serverId, File(flower.imagePath))
                repository.setImagePendingUpload(flower.id, false)
            }
        }
    }

    /**
     * Purge une fleur dont la suppression a été propagée au serveur (C3) : la
     * ligne locale, son fichier image et ses photos additionnelles (lignes +
     * fichiers) ne servent plus à rien.
     */
    private suspend fun purgeLocal(flower: FlowerEntity) {
        photoSync?.purgeForFlower(flower.id)
        if (flower.imagePath.isNotEmpty()) {
            runCatching { File(flower.imagePath).delete() }
        }
        repository.hardDelete(flower)
    }

    /**
     * Sur 409 (conflit), le serveur fait foi : on marque synchronisé, le pull
     * suivant réconcilie le contenu (last-write-wins). Sinon, on marque en échec
     * pour réessayer plus tard.
     */
    private suspend fun handlePushFailure(
        flower: FlowerEntity,
        error: Exception,
    ) {
        if (error is HttpException && error.code() == 409) {
            repository.markSynced(flower.id, flower.serverId!!, now(), flower.updatedAt)
        } else {
            repository.markFailed(flower.id)
        }
    }

    /** Tire le delta depuis le dernier sync et applique maj + suppressions. */
    suspend fun pull() {
        val response = syncApi.pull(lastSyncStore.get())
        response.flowers.forEach { dto ->
            val existing = repository.findByServerId(dto.id)
            if (existing != null) {
                // Fleur en cours d'édition/suppression locale non poussée : on ne
                // l'écrase pas (last-write-wins côté push, comme
                // AlbumSyncEngine.pull). Elle sera réconciliée une fois poussée.
                if (existing.syncState != SyncState.SYNCED.name) return@forEach
                repository.update(dto.applyTo(existing))
                // Met en cache l'image localement si on n'en a pas de copie (fleur
                // tirée d'un autre appareil) : l'affichage ne dépendra plus de
                // l'expiration de l'URL présignée.
                cacheRemoteImageIfMissing(existing.id, dto.id, existing.imagePath, dto.imageUrl)
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
            // distante, puis téléchargée en local pour ne plus dépendre de
            // l'expiration de l'URL présignée à l'affichage.
            val localId = repository.insert(dto.toEntity())
            cacheRemoteImageIfMissing(localId, dto.id, imagePath = "", dto.imageUrl)
            photoSync?.reconcile(localId, dto.photos)
        }
        response.deletedIds.forEach { serverId ->
            repository.softDeleteByServerId(serverId, now())
        }
        lastSyncStore.set(response.serverTime)
    }

    /**
     * Télécharge l'image de la fleur [localId] dans le stockage privé et renseigne
     * son `imagePath`, sauf si une copie locale existe déjà ou si l'URL est
     * absente. Best-effort : un échec (URL expirée, réseau) laisse l'URL distante
     * en repli sans interrompre la synchro.
     */
    private suspend fun cacheRemoteImageIfMissing(
        localId: Long,
        serverId: String,
        imagePath: String,
        remoteUrl: String?,
    ) {
        if (imagePath.isNotEmpty() || remoteUrl.isNullOrEmpty()) return
        val path = cacheRemoteImage(serverId, remoteUrl) ?: return
        repository.cacheImagePath(localId, path)
    }

    private fun isoToMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
