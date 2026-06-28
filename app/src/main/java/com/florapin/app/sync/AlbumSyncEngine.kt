package com.florapin.app.sync

import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.applyTo
import com.florapin.app.data.toEntity
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.UpdateAlbumRequest

/**
 * Synchronisation des albums (NODE-102), offline-first comme [SyncEngine] :
 * pousse les albums locaux (création/renommage/suppression + appartenances),
 * puis tire l'état serveur. L'API albums étant du CRUD (pas de delta), le pull
 * récupère la liste complète et réconcilie.
 *
 * Conflits : last-write-wins. Le pull ne réécrit pas un album resté PENDING
 * localement (édition non encore poussée).
 */
class AlbumSyncEngine(
    private val albums: AlbumRepository,
    private val flowers: FlowerRepository,
    private val albumsApi: AlbumsApi,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync() {
        push()
        pull()
    }

    suspend fun push() {
        for (album in albums.pendingSync()) {
            val serverId = album.serverId
            when {
                serverId == null && album.deletedAt == null -> {
                    // Création idempotente : le clientId permet au serveur de
                    // retomber sur l'album existant si un push précédent a réussi
                    // mais que markSynced n'a pas abouti (réponse perdue/crash).
                    val dto = albumsApi.create(
                        CreateAlbumRequest(album.name, album.clientId),
                    )
                    albums.markSynced(album.id, dto.id, now())
                    reconcileMembers(dto.id, albums.memberFlowerServerIds(album.id))
                }
                serverId != null && album.deletedAt != null -> {
                    albumsApi.delete(serverId)
                    albums.hardDelete(album)
                }
                serverId != null -> {
                    albumsApi.rename(serverId, UpdateAlbumRequest(album.name))
                    reconcileMembers(serverId, albums.memberFlowerServerIds(album.id))
                    albums.markSynced(album.id, serverId, now())
                }
            }
        }
    }

    /** Aligne l'appartenance serveur de l'album sur l'ensemble local désiré. */
    private suspend fun reconcileMembers(
        serverAlbumId: String,
        desiredFlowerServerIds: List<String>,
    ) {
        val current = albumsApi.get(serverAlbumId).flowerIds.toSet()
        val desired = desiredFlowerServerIds.toSet()
        (desired - current).forEach {
            albumsApi.addFlower(serverAlbumId, AddFlowerToAlbumRequest(it))
        }
        (current - desired).forEach { albumsApi.removeFlower(serverAlbumId, it) }
    }

    suspend fun pull() {
        val remote = albumsApi.list()
        for (dto in remote) {
            // 1) par serverId. 2) sinon, filet anti-doublon : on rattache un album
            // local créé ici dont le push a réussi côté serveur mais dont le
            // serverId n'a pas été persisté (markSynced manqué) — repéré via le
            // clientId. Évite de réinsérer un doublon.
            val existing = albums.findByServerId(dto.id)
                ?: dto.clientId?.let { albums.findByClientId(it) }
            val localId: Long
            when {
                existing == null -> {
                    localId = albums.insert(dto.toEntity())
                }
                // Rattaché par clientId alors que le serverId local manque : le
                // push a créé l'album côté serveur mais markSynced a échoué. On
                // adopte le serverId SANS écraser un éventuel renommage local
                // resté PENDING (il sera poussé au prochain push via la branche
                // rename). Anti-doublon : évite la réinsertion.
                existing.serverId == null -> {
                    albums.update(existing.copy(serverId = dto.id))
                    localId = existing.id
                }
                // Album en cours d'édition locale (déjà synchronisé) : on ne
                // l'écrase pas, last-write-wins côté push.
                existing.syncState != com.florapin.app.data.SyncState.SYNCED.name -> {
                    continue
                }
                else -> {
                    albums.update(dto.applyTo(existing))
                    localId = existing.id
                }
            }
            // Réconcilie l'appartenance locale (fleurs déjà présentes localement).
            val memberLocalIds = dto.flowerIds.mapNotNull {
                flowers.findByServerId(it)?.id
            }
            albums.setMembers(localId, memberLocalIds)
        }

        // Albums synchronisés absents côté serveur : supprimés ailleurs → purge.
        val serverIds = remote.map { it.id }.toSet()
        albums.allActive()
            .filter { it.serverId != null && it.serverId !in serverIds }
            .forEach { albums.hardDelete(it) }
    }
}
