package com.florapin.app.sync

import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.applyTo
import com.florapin.app.data.toEntity
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.UpdateAlbumRequest
import retrofit2.HttpException

/**
 * Synchronisation des albums (NODE-102), offline-first comme [SyncEngine] :
 * pousse les albums locaux (création/renommage/suppression + appartenances),
 * puis tire l'état serveur. L'API albums étant du CRUD (pas de delta), le pull
 * récupère la liste complète et réconcilie.
 *
 * Conflits : last-write-wins. Le pull ne réécrit pas un album resté PENDING
 * localement (édition non encore poussée).
 *
 * Albums COLLABORATIFS (TÂCHE 7.1) : la liste serveur contient aussi les albums
 * des groupes dont je suis membre, possédés par d'autres. Le moteur en tient
 * compte pour ne pas provoquer de conflit d'édition concurrente :
 * - je ne renomme/supprime QUE mes propres albums ;
 * - la réconciliation d'appartenance ne touche QUE mes propres fleurs (les
 *   contributions des autres membres, inconnues localement, ne sont jamais
 *   effacées) ;
 * - un 403 (droits insuffisants sur un album de groupe) abandonne l'édition
 *   locale et laisse le pull restaurer l'état serveur.
 */
class AlbumSyncEngine(
    private val albums: AlbumRepository,
    private val flowers: FlowerRepository,
    private val albumsApi: AlbumsApi,
    /** Compte courant : distingue mes albums de ceux des autres membres. */
    private val currentUserId: String? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync() {
        push()
        pull()
    }

    suspend fun push() {
        // Ensemble de MES fleurs (serverId) : borne les retraits d'appartenance
        // pour ne pas effacer les fleurs des autres membres d'un album de groupe.
        val myServerIds = flowers.allForBackup().mapNotNull { it.serverId }.toSet()
        for (album in albums.pendingSync()) {
            val serverId = album.serverId
            // Un album sans propriétaire connu est local (donc mien) ; sinon on
            // compare au compte courant.
            val mine = album.ownerId == null || album.ownerId == currentUserId
            // Chaque album est isolé (I10) : un échec permanent (404/409…) sur
            // l'un ne bloque pas la sync des autres ni ne fait rejouer tout le
            // worker indéfiniment.
            try {
                when {
                    serverId == null && album.deletedAt == null -> {
                        // Création idempotente : le clientId permet au serveur de
                        // retomber sur l'album existant si un push précédent a réussi
                        // mais que markSynced n'a pas abouti (réponse perdue/crash).
                        val dto = albumsApi.create(
                            CreateAlbumRequest(
                                name = album.name,
                                clientId = album.clientId,
                                groupId = album.groupId,
                                permissionMode = album.groupId?.let { album.permissionMode },
                            ),
                        )
                        albums.markSynced(album.id, dto.id, now())
                        reconcileMembers(
                            dto.id,
                            albums.memberFlowerServerIds(album.id),
                            myServerIds,
                        )
                    }
                    serverId != null && album.deletedAt != null -> {
                        // Propriétaire : suppression réelle. Membre : on cesse
                        // simplement de suivre l'album de groupe (il subsiste pour
                        // les autres). Dans les deux cas on purge la copie locale.
                        if (mine) albumsApi.delete(serverId)
                        albums.hardDelete(album)
                    }
                    serverId != null -> {
                        // Seul le propriétaire renomme ; un membre ne pousse que
                        // ses contributions (fleurs), jamais le nom d'autrui.
                        if (mine) {
                            albumsApi.rename(serverId, UpdateAlbumRequest(album.name))
                        }
                        reconcileMembers(
                            serverId,
                            albums.memberFlowerServerIds(album.id),
                            myServerIds,
                        )
                        albums.markSynced(album.id, serverId, now())
                    }
                }
            } catch (e: HttpException) {
                when {
                    // 404 : l'album n'existe plus côté serveur (supprimé ailleurs).
                    // Inutile de repousser indéfiniment : on purge la copie locale
                    // (le pull ferait de même pour un album resté synchronisé).
                    e.code() == 404 && serverId != null -> albums.hardDelete(album)
                    // 403 : droits insuffisants sur un album de groupe. On abandonne
                    // l'édition locale (repasse SYNCED) et le pull restaurera l'état
                    // serveur faisant autorité — pas de boucle de retry.
                    e.code() == 403 && serverId != null ->
                        albums.markSynced(album.id, serverId, now())
                    // Autres codes : l'album reste PENDING, retenté au prochain sync.
                }
            } catch (_: Exception) {
                // Erreur transitoire (réseau…) : on continue avec les suivants,
                // l'album reste PENDING.
            }
        }
    }

    /**
     * Aligne l'appartenance serveur de l'album sur l'ensemble local désiré, en ne
     * touchant QUE mes propres fleurs (voir en-tête de classe) : ajout des fleurs
     * miennes absentes, retrait des fleurs miennes retirées localement. Les
     * fleurs des autres membres restent intactes.
     */
    private suspend fun reconcileMembers(
        serverAlbumId: String,
        desiredFlowerServerIds: List<String>,
        myServerIds: Set<String>,
    ) {
        val current = albumsApi.get(serverAlbumId).flowerIds.toSet()
        val desired = desiredFlowerServerIds.toSet()
        (desired - current).forEach {
            albumsApi.addFlower(serverAlbumId, AddFlowerToAlbumRequest(it))
        }
        ((current - desired) intersect myServerIds).forEach {
            albumsApi.removeFlower(serverAlbumId, it)
        }
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
